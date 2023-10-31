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
package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.polardbx.druid.wall.spi.MySqlWallProvider;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.XATransactionType;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent.ColumnInfo;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMetaCache;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_FILTER_TABLE_ERROR;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_DDL_PREFIX;

/**
 * 基于{@linkplain LogEvent}转化为Entry对象的处理
 *
 * @author jianghang 2013-1-17 下午02:41:14
 * @version 1.0.0
 */
@Slf4j
public class LogEventConvert {

    public static final String ISO_8859_1 = "ISO-8859-1";
    public static final int TINYINT_MAX_VALUE = 256;
    public static final int SMALLINT_MAX_VALUE = 65536;
    public static final int MEDIUMINT_MAX_VALUE = 16777216;
    public static final long INTEGER_MAX_VALUE = 4294967296L;
    public static final BigInteger BIGINT_MAX_VALUE = new BigInteger("18446744073709551616");
    public static final int version = 1;
    public static final String BEGIN = "BEGIN";
    public static final String COMMIT = "COMMIT";
    private static final Pattern ORIGIN_SQL_PATTERN = Pattern.compile(PRIVATE_DDL_DDL_PREFIX + "([\\W\\w]+)");

    protected static final String MYSQL = "mysql";
    protected static final String HA_HEALTH_CHECK = "ha_health_check";
    protected static final String DRDS_SYSTEM_MYSQL_HEARTBEAT = "__drds__system__mysql__heartbeat__";

    protected TableMetaCache tableMetaCache;

    protected String binlogFileName = "";
    protected String charset = "utf8";
    protected boolean filterQueryDml = true;
    protected boolean filterRowsQuery = false;
    // 是否跳过table相关的解析异常,比如表不存在或者列数量不匹配,issue 92
    protected boolean filterTableError = DynamicApplicationConfig.getBoolean(RPL_FILTER_TABLE_ERROR);
    protected boolean firstBinlogFile = true;
    protected HostType srcHostType;
    protected HostInfo metaHostInfo;
    protected BaseFilter filter;

    protected FieldMeta rdsImplicitIDFieldMeta;
    protected TableMeta rdsHeartBeatTableMeta;
    protected String nowTso;

    public LogEventConvert(HostInfo metaHostInfo, BaseFilter filter, BinlogPosition startBinlogPosition,
                           HostType srcHostType) {
        this.metaHostInfo = metaHostInfo;
        this.filter = filter;
        this.binlogFileName = startBinlogPosition.getFileName();
        this.srcHostType = srcHostType;
    }

    public static DBMSTransactionBegin createTransactionBegin(long threadId) {
        DBMSTransactionBegin transactionBegin = new DBMSTransactionBegin();
        transactionBegin.setThreadId(threadId);
        return transactionBegin;
    }

    public static DBMSTransactionEnd createTransactionEnd(long transactionId) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(transactionId);
        return transactionEnd;
    }

    public void refreshState() {
        firstBinlogFile = true;
    }

    public void init() throws Exception {
        this.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        DataSource dataSource = DataSourceUtil.createDruidMySqlDataSource(metaHostInfo.isUsePolarxPoolCN(),
            metaHostInfo.getHost(),
            metaHostInfo.getPort(),
            "",
            metaHostInfo.getUserName(),
            metaHostInfo.getPassword(),
            "",
            1,
            2,
            null,
            null);
        this.tableMetaCache = new TableMetaCache(dataSource, srcHostType == HostType.POLARX2);
        initMeta();
    }

    protected void initMeta() {
        rdsImplicitIDFieldMeta =
            new FieldMeta(RplConstants.RDS_IMPLICIT_ID, "long", false, true, null, true);

        // 处理rds模式的mysql.ha_health_check心跳数据
        // 主要RDS的心跳表基本无权限,需要mock一个tableMeta
        FieldMeta idMeta = new FieldMeta("id", "bigint(20)", true, false, "0", false);
        FieldMeta typeMeta = new FieldMeta("type", "char(1)", false, true, "0", false);// type为主键
        rdsHeartBeatTableMeta = new TableMeta(MYSQL, HA_HEALTH_CHECK, Arrays.asList(idMeta, typeMeta));
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public void setBinlogFileName(String binlogFileName) {
        this.binlogFileName = binlogFileName;
    }

    public MySQLDBMSEvent parse(LogEvent logEvent, boolean isSeek) throws Exception {
        if (logEvent == null || logEvent instanceof UnknownLogEvent) {
            return null;
        }

        int DBMSAction = logEvent.getHeader().getType();
        switch (DBMSAction) {
        // canal 解析 binlog 时，传入的 context (LogContext 类型)，其成员 FormatDescriptionLogEvent
        // formatDescription 是默认 binlog 不开启 CRC32 校验的（FormatDescriptionLogEvent 的
        // checksumAlg 字段）。
        // 而解析 binlog 时会用到 checksumAlg 值（参考 LogDecoder 第 64 行）。
        // 一个 binlog 文件，第一个事件为 ROTATE_EVENT，第二个为 FORMAT_DESCRIPTION_EVENT。
        // 一个 binlog 文件，只有 ROTATE_EVENT 带有 fileName 信息。
        // ROTATE_EVENT 不包含 checksumAlg 信息。FORMAT_DESCRIPTION_EVENT 包含 checksumAlg 信息。
        // 故如果 binlog 开启了 CRC32，那么：
        // canal 解析出来的 ROTATE_EVENT 中的 fileName 是错误的，它将 CRC32
        // 校验值的 4 个字节当成了 fileName 的一部分（参考 LogDecoder 第 64 行）。
        // canal 在解析完 FORMAT_DESCRIPTION_EVENT 后，会解析出正确的 checksumAlg。同时将 context 中的
        // formatDescription 字段替换成正确的。
        // 故如果 binlog 开启了 CRC32，那么解析出来的 binlogFileName 是错误的，而后续解析出的 event 数据却是对的。
        // 但是如果 binlog 文件发生了切换，那么第二个 binlog 文件被解析出来的 binlogFileName 则是对的。
        case LogEvent.ROTATE_EVENT:
            if (!firstBinlogFile) {
                binlogFileName = ((RotateLogEvent) logEvent).getFilename();
            }
            break;
        case LogEvent.FORMAT_DESCRIPTION_EVENT:
            firstBinlogFile = false;
            break;
        case LogEvent.QUERY_EVENT:
            return parseQueryEvent((QueryLogEvent) logEvent, isSeek);
        case LogEvent.XID_EVENT:
            return parseXidEvent((XidLogEvent) logEvent);
        case LogEvent.TABLE_MAP_EVENT:
            break;
        case LogEvent.WRITE_ROWS_EVENT_V1:
        case LogEvent.WRITE_ROWS_EVENT:
            return parseRowsEvent((WriteRowsLogEvent) logEvent, isSeek);
        case LogEvent.UPDATE_ROWS_EVENT_V1:
        case LogEvent.UPDATE_ROWS_EVENT:
            return parseRowsEvent((UpdateRowsLogEvent) logEvent, isSeek);
        case LogEvent.DELETE_ROWS_EVENT_V1:
        case LogEvent.DELETE_ROWS_EVENT:
            return parseRowsEvent((DeleteRowsLogEvent) logEvent, isSeek);
        case LogEvent.ROWS_QUERY_LOG_EVENT:
            nowTso = LogEventUtil.getTsoFromRowQuery(((RowsQueryLogEvent) logEvent).getRowsQuery());
            return parseRowsQueryEvent((RowsQueryLogEvent) logEvent);
        case LogEvent.ANNOTATE_ROWS_EVENT:
            return parseAnnotateRowsEvent((AnnotateRowsEvent) logEvent);
        case LogEvent.USER_VAR_EVENT:
            return parseUserVarLogEvent((UserVarLogEvent) logEvent);
        case LogEvent.INTVAR_EVENT:
            return parseIntrvarLogEvent((IntvarLogEvent) logEvent);
        case LogEvent.RAND_EVENT:
            return parseRandLogEvent((RandLogEvent) logEvent);
        case LogEvent.HEARTBEAT_LOG_EVENT:
            return null;
        default:
            break;
        }

        return null;
    }

    public void reset() {
        tableMetaCache.reset();
    }

    protected MySQLDBMSEvent parseQueryEvent(QueryLogEvent event, boolean isSeek) {
        String queryString = event.getQuery();
        if (StringUtils.endsWithIgnoreCase(queryString, BEGIN)) {
            nowTso = null;
            DBMSTransactionBegin transactionBegin = createTransactionBegin(event.getSessionId());
            return new MySQLDBMSEvent(transactionBegin, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
        } else if (StringUtils.endsWithIgnoreCase(queryString, COMMIT)) {
            nowTso = null;
            DBMSTransactionEnd transactionEnd = createTransactionEnd(0L);
            return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
        } else {
            // ddl
            // 先提取# POLARX_TSO=
            String tso = CommonUtils.extractPrivateTSOFromDDL(queryString);
            if (StringUtils.isNotBlank(tso)) {
                nowTso = tso;
            }

            String originSql = queryString;
            Scanner scanner = new Scanner(queryString);
            while (scanner.hasNextLine()) {
                String str = scanner.nextLine();
                Matcher matcher = ORIGIN_SQL_PATTERN.matcher(str);
                if (matcher.find()) {
                    originSql = matcher.group(1);
                    break;
                }
            }

            // DDL语句处理
            // 只保留最后一条，queryString都是一样的
            HashMap<String, String> ddlInfo = Maps.newHashMap();

            // 解析 sql 语句
            if (StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "flush")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "grant")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "create user")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "drop user")) {
                return null;
            }
            List<DdlResult> resultList = DruidDdlParser.parse(originSql, event.getDbName());
            if (CollectionUtils.isEmpty(resultList)) {
                if (!(originSql.startsWith("/*!") && originSql.endsWith("*/"))) {
                    log.error("DDL result list is empty. Raw query string: {}, db: {}", queryString, event.getDbName());
                }
                return null;
            }
            DdlResult result = resultList.get(0);
            DBMSAction action = result.getType();

            // 根据 filter 的 rewriteDbs 重写 dbName

            // 对于非跨库 DDL: use db1, create table tb1(id int)
            // event.getDbName() == result.getSchemaName() == db1

            // 对于跨库 DDL:
            // 如在源库 use db1, create db2.tb1(id int)
            // 则，event.getDbName() == db1，result.getSchemaName() == db2;

            // 对于 create database db1, drop database db1:
            // 则不管是否跨库，始终 event.getDbName() == result.getSchemaName() == db1

            // getRewriteDb 的作用：如果一条 sql,
            // 不管是跨库的 use db2, create table db1.tb1(id int),
            // 还是不跨库的 use db1, create table tb1(id int)
            // 则 result.getSchemaName() == db1,
            // mysql 在目标上执行逻辑：use result.getSchemaName()，create table tb1(id int);

            // 注意：getRewriteDb 对 create database 和 drop database 不生效，
            // 因为对于 mysql 来说，即便是有 rewriteDbs: <db1, db2>，
            // 一条 sql: create database db1，在目标库执行时，不会变成 create database db2，
            // 而仍然是 create database db1。

            // mysql 逻辑，不管是否是跨库 ddl，都使用 event.getDbName() 对应的 rewriteDb 来过滤，
            // 而不是使用 result.getSchemaName()。
            // 按照逻辑来说，应该要使用 result.getSchemaName() 的 rewriteDb 来过滤，但此处 polarx 与 mysql 逻辑对齐。
            String rewriteDbName = filter.getRewriteDb(event.getDbName(), action);
            if (filter.ignoreEvent(rewriteDbName, result.getTableName(), action, event.getServerId())) {
                return null;
            }
            if (filter.ignoreEventByTso(nowTso)) {
                return null;
            }

            // 跨库 ddl，构造 DDL 事件时，要使用其真正的 db，即 result.getSchemaName()
            if (!StringUtils.equalsIgnoreCase(event.getDbName(), result.getSchemaName())) {
                rewriteDbName = filter.getRewriteDb(result.getSchemaName(), action);
            }

            // 对于 create database, drop database，则不再需要指定当前 DDL 在哪个 db 上执行，
            // 因为执行 sql: create databases db1 时，本就不要也不能预先执行 use db1
            if (action == DBMSAction.CREATEDB || action == DBMSAction.DROPDB) {
                rewriteDbName = "";
            }

            // 更新内存 tableMetaCache
            BinlogPosition position = createPosition(event.getHeader());
            tableMetaCache.apply(position, rewriteDbName, originSql);

            // 构造对象
            DefaultQueryLog queryEvent = new DefaultQueryLog(rewriteDbName,
                queryString,
                new java.sql.Timestamp(event.getHeader().getWhen() * 1000),
                event.getErrorCode(),
                event.getSqlMode(),
                result.getType());

            // 将 ddl 的一些信息 set 到 optionValue
            if (!ddlInfo.isEmpty()) {
                queryEvent.setOptionValue(DefaultQueryLog.ddlInfo, ddlInfo);
            }
            return new MySQLDBMSEvent(queryEvent, createPosition(event.getHeader()), event.getHeader().getEventLen());
        }
    }

    protected MySQLDBMSEvent parseRowsQueryEvent(RowsQueryLogEvent event) {
        if (filterQueryDml && filterRowsQuery) {
            return null;
        }
        // mysql5.6支持，需要设置binlog-rows-query-log-events=1，可详细打印原始DML语句
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildRowsQueryEntry(queryString, event.getHeader(), DBMSAction.ROWQUERY);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseAnnotateRowsEvent(AnnotateRowsEvent event) {
        if (filterQueryDml) {
            return null;
        }
        // mariaDb支持，需要设置binlog_annotate_row_events=true，可详细打印原始DML语句
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildQueryEntry(queryString, event.getHeader(), null);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseUserVarLogEvent(UserVarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseIntrvarLogEvent(IntvarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseRandLogEvent(RandLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseXidEvent(XidLogEvent event) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(event.getXid());
        return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()), event.getHeader().getEventLen());
    }

    protected MySQLDBMSEvent parseRowsEvent(RowsLogEvent event, boolean isSeek) {
        try {
            TableMapLogEvent table = event.getTable();
            if (table == null) {
                // tableId对应的记录不存在
                throw new TableIdNotFoundException("not found tableId:" + event.getTableId());
            }

            DBMSAction action = null;
            int type = event.getHeader().getType();
            if (LogEvent.WRITE_ROWS_EVENT_V1 == type || LogEvent.WRITE_ROWS_EVENT == type) {
                action = DBMSAction.INSERT;
            } else if (LogEvent.UPDATE_ROWS_EVENT_V1 == type || LogEvent.UPDATE_ROWS_EVENT == type) {
                action = DBMSAction.UPDATE;
            } else if (LogEvent.DELETE_ROWS_EVENT_V1 == type || LogEvent.DELETE_ROWS_EVENT == type) {
                action = DBMSAction.DELETE;
            } else {
                throw new CanalParseException("unsupport event type :" + event.getHeader().getType());
            }

            // 根据 filter 的 rewriteDbs 重写 DbName
            String rewriteDbName = filter.getRewriteDb(table.getDbName(), action);

            // 过滤条件
            if (filter.ignoreEvent(rewriteDbName, table.getTableName(), action, event.getServerId())) {
                return null;
            }
            if (filter.ignoreEventByTso(nowTso)) {
                return null;
            }
            if (isRDSHeartBeat(rewriteDbName, table.getTableName())) {
                return null;
            }
            BinlogPosition position = createPosition(event.getHeader());

            // 如果是查找模式，那么不进行行数据的解析
            if (isSeek) {
                DefaultRowChange rowChange = new DefaultRowChange(action,
                    rewriteDbName,
                    table.getTableName(),
                    new DefaultColumnSet(Lists.newArrayList()));
                return new MySQLDBMSEvent(rowChange, position, event.getHeader().getEventLen());
            }

            RowsLogBuffer buffer = event.getRowsBuf(charset);
            BitSet columns = event.getColumns();
            BitSet changeColumns = event.getChangeColumns(); // 非变更的列,而是指存在binlog记录的列,mysql full image模式会提供所有列
            boolean tableError = false;
            TableMeta tableMeta = getTableMeta(rewriteDbName, table.getTableName());
            if (tableMeta == null) {
                if (filterTableError) {
                    return null;
                } else {
                    throw new CanalParseException(
                        "not found [" + rewriteDbName + "." + table.getTableName() + "] in db , pls check!");
                }
            }

            // check table fileds count，只能处理加字段
            int columnSize = event.getTable().getColumnCnt();

            // 构造列信息
            List<DBMSColumn> dbmsColumns = Lists.newArrayList();
            List<FieldMeta> fieldMetas = tableMeta.getFields();
            // 兼容一下canal的逻辑,认为DDL新增列都加在末尾,如果表结构的列比binlog的要多
            int size = fieldMetas.size();
            if (columnSize < size) {
                size = columnSize;
            }
            for (int i = 0; i < size; i++) {
                FieldMeta fieldMeta = fieldMetas.get(i);
                // 先临时加一个sqlType=0的值
                DefaultColumn column = new DefaultColumn(fieldMeta.getColumnName(),
                    i,
                    Types.OTHER,
                    fieldMeta.isUnsigned(),
                    fieldMeta.isNullable(),
                    fieldMeta.isKey(),
                    fieldMeta.isUnique(),
                    fieldMeta.isGenerated(),
                    fieldMeta.isImplicitPk());
                dbmsColumns.add(column);
            }

            DefaultRowChange rowChange = new DefaultRowChange(action,
                rewriteDbName,
                table.getTableName(),
                new DefaultColumnSet(dbmsColumns));
            BitSet actualChangeColumns = new BitSet(columnSize); // 需要处理到update类型时，基于数据内容进行判定
            rowChange.setChangeColumnsBitSet(actualChangeColumns);

            while (buffer.nextOneRow(columns)) {
                // 处理row记录
                if (DBMSAction.INSERT == action) {
                    // insert的记录放在before字段中
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else if (DBMSAction.DELETE == action) {
                    // delete的记录放在before字段中
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else {
                    // update需要处理before/after
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                    if (!buffer.nextOneRow(changeColumns)) {
                        break;
                    }

                    parseOneRow(rowChange, event, buffer, changeColumns, true, tableMeta, actualChangeColumns);
                }
            }

            MySQLDBMSEvent dbmsEvent = new MySQLDBMSEvent(rowChange, position, event.getHeader().getEventLen());
            if (tableError) {
                log.warn("table parser error : " + rowChange.toString());
                return null;
            } else {
                return dbmsEvent;
            }
        } catch (Exception e) {
            throw new CanalParseException("parse row data failed.", e);
        }
    }

    protected void parseOneRow(DefaultRowChange rowChange, RowsLogEvent event, RowsLogBuffer buffer, BitSet cols,
                               boolean isAfter, TableMeta tableMeta,
                               BitSet actualChangeColumns) throws UnsupportedEncodingException {
        int columnCnt = event.getTable().getColumnCnt();
        ColumnInfo[] columnInfo = event.getTable().getColumnInfo();

        DefaultRowData rowData = new DefaultRowData(columnCnt);
        for (int i = 0; i < columnCnt; i++) {
            ColumnInfo info = columnInfo[i];
            // mysql 5.6开始支持nolob/mininal类型,并不一定记录所有的列,需要进行判断
            if (!cols.get(i)) {
                continue;
            }

            FieldMeta fieldMeta = tableMeta.getFields().get(i);
            // 优先使用列的 charset, 如果列没有则使用表的 charset, 表一定有 charset
            String charset = StringUtils.isNotBlank(fieldMeta.getCharset()) ? fieldMeta
                .getCharset() : tableMeta.getCharset();
            String javaCharset = CharsetConversion.getJavaCharset(charset);
            buffer.nextValue(info.type, info.meta, fieldMeta.isBinary(), javaCharset);

            int javaType = buffer.getJavaType();
            Serializable dataValue = null;
            if (!buffer.isNull()) {
                final Serializable value = buffer.getValue();
                // 处理各种类型
                switch (javaType) {
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.BIGINT:
                    // 处理unsigned类型
                    Number number = (Number) value;
                    if (fieldMeta.isUnsigned() && number.longValue() < 0) {
                        switch (buffer.getLength()) {
                        case 1: /* MYSQL_TYPE_TINY */
                            dataValue = String.valueOf(Integer.valueOf(TINYINT_MAX_VALUE + number.intValue()));
                            javaType = Types.SMALLINT; // 往上加一个量级
                            break;

                        case 2: /* MYSQL_TYPE_SHORT */
                            dataValue = String.valueOf(Integer.valueOf(SMALLINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 3: /* MYSQL_TYPE_INT24 */
                            dataValue = String
                                .valueOf(Integer.valueOf(MEDIUMINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 4: /* MYSQL_TYPE_LONG */
                            dataValue = String.valueOf(Long.valueOf(INTEGER_MAX_VALUE + number.longValue()));
                            javaType = Types.BIGINT; // 往上加一个量级
                            break;

                        case 8: /* MYSQL_TYPE_LONGLONG */
                            dataValue = BIGINT_MAX_VALUE.add(BigInteger.valueOf(number.longValue())).toString();
                            javaType = Types.DECIMAL; // 往上加一个量级，避免执行出错
                            break;
                        default:
                            break;
                        }
                    } else {
                        // 对象为number类型，直接valueof即可
                        dataValue = String.valueOf(value);
                    }
                    break;
                case Types.REAL: // float
                case Types.DOUBLE: // double
                    // 对象为number类型，直接valueof即可
                    dataValue = String.valueOf(value);
                    break;
                case Types.BIT:// bit
                    // 对象为byte[]类型,不能直接转为字符串,入库的时候会有问题
                    dataValue = value;
                    break;
                case Types.DECIMAL:
                    dataValue = ((BigDecimal) value).toPlainString();
                    break;
                case Types.TIMESTAMP:
                    // 修复时间边界值
                    // String v = value.toString();
                    // v = v.substring(0, v.length() - 2);
                    // columnBuilder.setValue(v);
                    // break;
                case Types.TIME:
                case Types.DATE:
                    // 需要处理year
                    dataValue = value.toString();
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    // fixed text encoding
                    // https://github.com/AlibabaTech/canal/issues/18
                    // mysql binlog中blob/text都处理为blob类型，需要反查table
                    // meta，按编码解析text
                    if (fieldMeta != null && isText(fieldMeta.getColumnType())) {
                        dataValue = new String((byte[]) value, javaCharset);
                        javaType = Types.CLOB;
                    } else {
                        // byte数组，直接使用iso-8859-1保留对应编码，浪费内存
                        dataValue = (byte[]) value;
                        javaType = Types.BLOB;
                    }
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                    if (value == null) {
                        dataValue = null;
                    } else {
                        dataValue = value.toString();
                    }
                    break;
                default:
                    dataValue = value.toString();
                }
            }

            // 下标从1开始
            rowData.setRowValue(i + 1, dataValue);
            // 处理一下sqlType
            DBMSColumn dbmsColumn = rowChange.getColumns().get(i);
            if (dbmsColumn instanceof DefaultColumn && dbmsColumn.getSqlType() == Types.OTHER) {
                ((DefaultColumn) dbmsColumn).setSqlType(javaType);
            }
        }

        if (isAfter) {
            rowChange.addChangeData(rowData);
            // 处理一下变更列
            DBMSRowData beforeRowData = rowChange.getRowData(rowChange.getRowSize());
            buildChangeColumns(beforeRowData, rowData, columnCnt, actualChangeColumns);
        } else {
            rowChange.addRowData(rowData);
        }
    }

    protected void buildChangeColumns(DBMSRowData beforeRowData, DBMSRowData afterRowData, int size,
                                      BitSet changeColumns) {
        for (int i = 1; i <= size; i++) {
            Serializable before = beforeRowData.getRowValue(i);
            Serializable after = afterRowData.getRowValue(i);

            boolean check = isUpdate(before, after);
            if (check) {
                changeColumns.set(i - 1, true);
            }
        }

    }

    protected boolean isUpdate(Serializable before, Serializable after) {
        if (before == null && after == null) {
            return false;
        } else if (before != null && after != null && isEqual(before, after)) {
            return false;
        }

        // 比如nolob/minial模式下,可能找不到before记录,认为是有变化
        return true;
    }

    private boolean isEqual(Serializable before, Serializable after) {
        if (before instanceof byte[] && after instanceof byte[]) {
            return Arrays.equals((byte[]) before, (byte[]) after);
        } else {
            return before.equals(after);
        }
    }

    protected MySQLDBMSEvent buildQueryEntry(String queryString, LogHeader logHeader, DBMSAction action) {
        DefaultQueryLog queryLog = new DefaultQueryLog(null,
            queryString,
            new java.sql.Timestamp(logHeader.getWhen() * 1000),
            0,
            0,
            action);

        return new MySQLDBMSEvent(queryLog, createPosition(logHeader), logHeader.getEventLen());
    }

    protected MySQLDBMSEvent buildRowsQueryEntry(String queryString, LogHeader logHeader, DBMSAction action) {
        DefaultRowsQueryLog queryLog = new DefaultRowsQueryLog(
            queryString,
            action);

        return new MySQLDBMSEvent(queryLog, createPosition(logHeader), logHeader.getEventLen());
    }

    protected BinlogPosition createPosition(LogHeader logHeader) {
        BinlogPosition position = new BinlogPosition(binlogFileName, logHeader.getLogPos(), logHeader.getServerId(),
            logHeader.getWhen()); // 记录到秒
        position.setRtso(nowTso);
        return position;
    }

    protected TableMeta getTableMeta(String dbName, String tbName) {
        try {
            TableMeta tableMeta = tableMetaCache.getTableMeta(dbName, tbName);
            if (tableMeta.getPrimaryFields().isEmpty() && !tableMeta.getUseImplicitPk()) {
                if (srcHostType == HostType.RDS) {
                    // 添加 polarx 隐藏主键
                    log.info("Add implicit id {} for table {}.{}", RplConstants.RDS_IMPLICIT_ID, dbName, tbName);
                    tableMeta.addFieldMeta(rdsImplicitIDFieldMeta);
                    tableMeta.setUseImplicitPk(true);
                }
            }
            return tableMeta;
        } catch (Throwable e) {
            String message = ExceptionUtils.getRootCauseMessage(e);
            if (filterTableError) {
                if (StringUtils.containsIgnoreCase(message, "doesn't exist")) {
                    return null;
                }
                if (StringUtils.containsIgnoreCase(message, "Unknown database")) {
                    return null;
                }
                log.error("filter table error, root cause: {}", message);
            }

            throw new CanalParseException(e);
        }
    }

    protected boolean isText(String columnType) {
        return "LONGTEXT".equalsIgnoreCase(columnType) || "MEDIUMTEXT".equalsIgnoreCase(columnType)
            || "TEXT".equalsIgnoreCase(columnType) || "TINYTEXT".equalsIgnoreCase(columnType);
    }

    protected boolean isRDSHeartBeat(String schema, String table) {
        return (MYSQL.equalsIgnoreCase(schema) && HA_HEALTH_CHECK.equalsIgnoreCase(table))
            || DRDS_SYSTEM_MYSQL_HEARTBEAT.equalsIgnoreCase(table) ||
            "__drds__systable__leadership__".equalsIgnoreCase(table);
    }

    protected String getXid(String queryString, XATransactionType type) throws CanalParseException {
        return queryString.substring(type.getName().length()).trim();
    }

    protected DBMSXATransaction getXaTransaction(String queryString) throws CanalParseException {
        DBMSXATransaction xaTransaction = null;
        if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_START.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_START),
                XATransactionType.XA_START);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_END.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_END),
                XATransactionType.XA_END);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_COMMIT.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_COMMIT),
                XATransactionType.XA_COMMIT);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_ROLLBACK.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_ROLLBACK),
                XATransactionType.XA_ROLLBACK);
        }

        return xaTransaction;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean rollback(BinlogPosition position) {
        this.nowTso = position.getRtso();
        return tableMetaCache.rollback(position);
    }
}
