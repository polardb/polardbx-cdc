/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import com.aliyun.polardbx.binlog.format.utils.SQLModeConsts;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DDL_STRICT_TRANS_TABLES_MODE_BLACKLIST_DB;

public class QueryEventBuilder extends BinlogBuilder {

    /**
     * https://dev.mysql.com/doc/internals/en/query-event.html#q-flags2-code
     */
    public static final int Q_FLAGS2_CODE = 0x00;
    /**
     *
     */
    public static final int Q_SQL_MODE_CODE = 0x01;
    public static final int Q_CATALOG = 0x02;
    public static final int Q_AUTO_INCREMENT = 0x03;
    public static final int Q_CHARSET_CODE = 0x04;
    public static final int Q_TIME_ZONE_CODE = 0x05;
    public static final int Q_CATALOG_NZ_CODE = 0x06;
    public static final int Q_LC_TIME_NAMES_CODE = 0x07;
    public static final int Q_CHARSET_DATABASE_CODE = 0x08;
    public static final int Q_TABLE_MAP_FOR_UPDATE_CODE = 0x09;
    public static final int Q_MASTER_DATA_WRITTEN_CODE = 0x0a;
    public static final int Q_INVOKERS = 0x0b;
    public static final int Q_UPDATED_DB_NAMES = 0x0c;
    public static final int Q_MICROSECONDS = 0x0d;
    public static final int Q_DEFAULT_COLLATION_FOR_UTF8MB4 = 18;

    public static final String ISO_8859_1 = "ISO-8859-1";

    private static final Set<String> strictModeBlackList = buildBlackList();

    /**
     * The ID of the thread that issued this statement.
     * Needed for temporary tables.
     * This is also useful for a DBA for knowing who did what on the master.
     */
    private final int threadId;
    /**
     * The time in seconds that the statement took to execute. Only useful for inspection by the DBA.
     */
    private final int executeTime;

    /**
     * The error code resulting from execution of the statement on the master.
     * Error codes are defined in include/mysqld_error.h. 0 means no error.
     * How come statements with a nonzero error code can exist in the binary log?
     * This is mainly due to the use of nontransactional tables within transactions.
     * For example,
     * if an INSERT ... SELECT fails after inserting 1000 rows into a MyISAM table (for example, with a duplicate-key violation),
     * we have to write this statement to the binary log, because it truly modified the MyISAM table.
     * For transactional tables,
     * there should be no event with a nonzero error code (though it can happen,
     * for example if the connection was interrupted (Control-C)).
     * The slave checks the error code:
     * After executing the statement itself, it compares the error code it got with the error code in the event,
     * and if they are different it stops replicating (unless --slave-skip-errors was used to ignore the error).
     */
    private int errorCode = 0;

    /**
     * If flags2_inited==0, this is an event from 3.23 or 4.0; nothing to
     * print (remember we don't produce mixed relay logs so there cannot be
     * 5.0 events before that one so there is nothing to reset).
     */
    private boolean flags2_inited;

    /**
     * Now the session variables;
     * it's more efficient to pass SQL_MODE as a number instead of a
     * comma-separated list.
     * FOREIGN_KEY_CHECKS, SQL_AUTO_IS_NULL, UNIQUE_CHECKS are session-only
     * variables (they have no global version; they're not listed in
     * sql_class.h), The tests below work for pure binlogs or pure relay
     * logs. Won't work for mixed relay logs but we don't create mixed
     * relay logs (that is, there is no relay log with a format change
     * except within the 3 first events, which mysqlbinlog handles
     * gracefully). So this code should always be good.
     */
    private boolean sql_mode_inited;

    /**
     * Colleagues: please never free(thd->catalog) in MySQL. This would
     * lead to bugs as here thd->catalog is a part of an alloced block,
     * not an entire alloced block (see
     * Query_log_event::do_apply_event()). Same for thd->db().str.  Thank
     * you.
     */
    private boolean catalog_len;

    /**
     * In 5.0.x where x<4 masters we used to store the end zero here. This was
     * a waste of one byte so we don't do it in x>=4 masters. We change code to
     * Q_CATALOG_NZ_CODE, because re-using the old code would make x<4 slaves
     * of this x>=4 master segfault (expecting a zero when there is
     * none). Remaining compatibility problems are: the older slave will not
     * find the catalog; but it is will not crash, and it's not an issue
     * that it does not find the catalog as catalogs were not used in these
     * older MySQL versions (we store it in binlog and read it from relay log
     * but do nothing useful with it). What is an issue is that the older slave
     * will stop processing the Q_* blocks (and jumps to the db/query) as soon
     * as it sees unknown Q_CATALOG_NZ_CODE; so it will not be able to read
     * Q_AUTO_INCREMENT*, Q_CHARSET and so replication will fail silently in
     * various ways. Documented that you should not mix alpha/beta versions if
     * they are not exactly the same version, with example of 5.0.3->5.0.2 and
     * 5.0.4->5.0.3. If replication is from older to new, the new will
     * recognize Q_CATALOG_CODE and have no problem.
     */
    private boolean auto_increment_increment;

    /**
     * print the catalog when we feature SET CATALOG
     */
    private boolean charset_inited;

    /**
     * Note that our event becomes dependent on the Time_zone object
     * representing the time zone. Fortunately such objects are never deleted
     * or changed during mysqld's lifetime.
     */
    private boolean time_zone_len;

    private boolean lc_time_names_number;

    private boolean charset_database_number;

    /**
     * is used to evaluate the filter rules specified by --replicate-do-table / --replicate-ignore-table
     */
    private boolean table_map_for_update;
    /**
     * default database name
     */
    private final String schema;
    private final String queryString;
    private int statusVarStartOffset;
    private final boolean isDDL;
    private final int clientCharset;
    private final int connectionCharset;
    private final int serverCharset;

    public QueryEventBuilder(String schema, String queryString, int clientCharset, int connectionCharset,
                             int serverCharset, boolean isDDL, int createTime, long serverId) {
        super(createTime, BinlogEventType.QUERY_EVENT.getType(), serverId);
        this.schema = schema;
        this.queryString = queryString;
        this.executeTime = (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        this.threadId = 1;
        this.eventType = BinlogEventType.QUERY_EVENT.getType();
        this.clientCharset = clientCharset;
        this.connectionCharset = connectionCharset;
        this.serverCharset = serverCharset;
        this.isDDL = isDDL;
    }

    private int putStatusVars(AutoExpandBuffer outputData) throws UnsupportedEncodingException {
        int begin = outputData.position();
        StatusVarPars statuVarPars = new StatusVarPars(outputData);
        statuVarPars.putInt(Q_FLAGS2_CODE, 0);
        statuVarPars.putLong(Q_SQL_MODE_CODE, buildSqlMode());
        statuVarPars.putString(Q_CATALOG_NZ_CODE, "std");
        statuVarPars.putString(Q_TIME_ZONE_CODE, "SYSTEM");

        statuVarPars.putWithSize(Q_CHARSET_CODE, INT8);
        statuVarPars.putWithSize(clientCharset, INT16);
        statuVarPars.putWithSize(connectionCharset, INT16);
        statuVarPars.putWithSize(serverCharset, INT16);
        if (!isDDL) {
            // 如果包含utf8mb4字符集的变量
            statuVarPars.putByte(Q_DEFAULT_COLLATION_FOR_UTF8MB4);
            statuVarPars.putWithSize(CollationCharset.utf8mb4Charset.getId(), INT16);
        } else {
            String needSetDb = schema;
            if (StringUtils.isBlank(needSetDb)) {
                needSetDb = "mysql";
            }
            byte[] data = needSetDb.getBytes(ISO_8859_1);
            statuVarPars.putByte(Q_UPDATED_DB_NAMES, 1);
            statuVarPars.put(data);
            statuVarPars.putByte(0);
        }
        return outputData.position() - begin;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        int len = putStatusVars(outputData);

        String charset = CharsetConversion.getJavaCharset(clientCharset);
        writeString(outputData, schema, ISO_8859_1, false);
        // null-terminated for schema
        numberToBytes(outputData, 0, INT8);
        writeString(outputData, queryString, charset, false);
        outputData.putShort(len, statusVarStartOffset);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, threadId, INT32);
        numberToBytes(outputData, executeTime, INT32);
        numberToBytes(outputData, getStringLength(schema), INT8);
        numberToBytes(outputData, errorCode, INT16);
        statusVarStartOffset = outputData.position();
        numberToBytes(outputData, 1, INT16);
    }

    private long buildSqlMode() {
        long mode = SQLModeConsts.MODE_ONLY_FULL_GROUP_BY | SQLModeConsts.MODE_ERROR_FOR_DIVISION_BY_ZERO |
            SQLModeConsts.MODE_NO_ENGINE_SUBSTITUTION;
        if (!strictModeBlackList.contains(StringUtils.lowerCase(schema))) {
            mode |= SQLModeConsts.MODE_STRICT_TRANS_TABLES;
        }
        return mode;
    }

    private static Set<String> buildBlackList() {
        String configStr = DynamicApplicationConfig.getString(TASK_DDL_STRICT_TRANS_TABLES_MODE_BLACKLIST_DB);
        if (StringUtils.isBlank(configStr)) {
            return Sets.newHashSet();
        } else {
            configStr = configStr.toLowerCase();
            String[] array = configStr.split(",");
            return Sets.newHashSet(array);
        }
    }

    private static class StatusVarPars {

        private final AutoExpandBuffer outputData;

        public StatusVarPars(AutoExpandBuffer outputData) {
            this.outputData = outputData;
        }

        public void putWithSize(int v, int byteSize) {
            for (int i = 0; i < byteSize; i++) {
                outputData.put((byte) (v >> (i * 8) & 0xFF));
            }
        }

        public void putString(int code, String value) throws UnsupportedEncodingException {
            byte[] datas = value.getBytes(ISO_8859_1);
            outputData.put((byte) code);
            outputData.put((byte) datas.length);
            outputData.put(datas);
        }

        public void put(int code, byte[] value) {
            outputData.put((byte) code);
            outputData.put(value);
        }

        public void put(byte[] data) {
            outputData.put(data);
        }

        public void putByte(int code) {
            outputData.put((byte) code);
        }

        public void putByte(int code, int v) {
            outputData.put((byte) code);
            outputData.put((byte) v);
        }

        public void putInt(int code, int v) {
            outputData.put((byte) code);
            outputData.put((byte) (v & 0xFF));
            outputData.put((byte) (v >> 8 & 0xFF));
            outputData.put((byte) (v >> 16 & 0xFF));
            outputData.put((byte) (v >> 24 & 0xFF));
        }

        public void putLong(int code, long v) {
            outputData.put((byte) code);
            outputData.put((byte) (v & 0xFF));
            outputData.put((byte) (v >> 8 & 0xFF));
            outputData.put((byte) (v >> 16 & 0xFF));
            outputData.put((byte) (v >> 24 & 0xFF));
            outputData.put((byte) (v >> 32 & 0xFF));
            outputData.put((byte) (v >> 40 & 0xFF));
            outputData.put((byte) (v >> 48 & 0xFF));
            outputData.put((byte) (v >> 56 & 0xFF));
        }
    }
}
