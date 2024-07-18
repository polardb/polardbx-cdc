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
package com.aliyun.polardbx.binlog.canal.core.ddl;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.PolarxTableMetaProxy;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.TableMetaTSDB;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理table meta解析和缓存
 *
 * @author jianghang 2013-1-17 下午10:15:16
 * @version 1.0.0
 */
public class TableMetaCache {

    private static final Logger logger = LoggerFactory.getLogger(TableMetaCache.class);
    private static final String SHOW_CREATE_TABLE = "SHOW CREATE TABLE `%s`.`%s`";
    private static final String DESC = "DESC `%s`.`%s`";
    private final DataSource dataSource;
    private MysqlConnection connection;
    private final TableMetaTSDB memoryTableMeta;
    private final int MAX_RETRY = 3;

    public TableMetaCache(DataSource dataSource, boolean enableSrcLogicalMetaSnapshot) {
        this.dataSource = dataSource;
        if (enableSrcLogicalMetaSnapshot) {
            this.memoryTableMeta = new PolarxTableMetaProxy(dataSource);
        } else {
            this.memoryTableMeta = new MemoryTableMeta(logger, true);
        }
        this.memoryTableMeta.init(null);
        resetMysqlConnection();
    }

    public void reset() {
        memoryTableMeta.destroy();
    }

    public void resetMysqlConnection() {
        try {
            // 不想动太底层的MysqlConnection代码
            // conn存在已被druid remove的可能
            // 忽略异常： java.sql.SQLException: connection holder is null
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable e) {
                    logger.error("disconnect mysqlconnection failed", e);
                }
            }
            Connection conn = dataSource.getConnection();
            connection = new MysqlConnection(conn);
        } catch (Throwable e) {
            logger.error("reset mysqlconnection failed", e);
        }
    }

    @SneakyThrows
    public TableMeta getTableMeta(String schema, String table) {
        TableMeta tableMeta = memoryTableMeta.find(schema, table);
        if (tableMeta == null) {
            tableMeta = getTableMetaFromDb(schema, table);
        }

        if (StringUtils.isBlank(tableMeta.getCharset())) {
            String dbCharset = getDbCharset(schema);
            tableMeta.setCharset(dbCharset);
        }
        return tableMeta;
    }

    public TableMeta getTableMetaIfPresent(String schema, String table) {
        return memoryTableMeta.find(schema, table);
    }

    public boolean apply(BinlogPosition position, String schema, String ddl) {
        return memoryTableMeta.apply(position, schema, ddl, "");
    }

    private String getFullName(String schema, String table) {
        StringBuilder builder = new StringBuilder();
        return builder.append('`')
            .append(CommonUtils.escape(schema))
            .append('`')
            .append('.')
            .append('`')
            .append(CommonUtils.escape(table))
            .append('`')
            .toString();
    }

    private TableMeta getTableMetaFromDb(String schema, String table) throws Throwable {
        int retry = 0;
        while (true) {
            try {
                // 优先使用show create table处理
                TableMeta tableMeta = connection.query(
                    String.format(SHOW_CREATE_TABLE, CommonUtils.escape(schema), CommonUtils.escape(table)), rs -> {

                        String createDDL = null;
                        while (rs.next()) {
                            createDDL = rs.getString(2);
                        }

                        memoryTableMeta.apply(TableMetaTSDB.INIT_POSITION, schema, createDDL, null);
                        return memoryTableMeta.find(schema, table);
                    });

                if (tableMeta != null) {
                    return tableMeta;
                }

                // fallback
                return connection.query(String.format(DESC, CommonUtils.escape(schema), CommonUtils.escape(table)),
                    rs -> {
                        List<FieldMeta> metas = new ArrayList<FieldMeta>();
                        while (rs.next()) {
                            FieldMeta meta = new FieldMeta();
                            // 做一个优化，使用String.intern()，共享String对象，减少内存使用
                            meta.setColumnName(rs.getString("Field"));
                            meta.setColumnType(rs.getString("Type"));
                            meta.setNullable(StringUtils.equalsIgnoreCase(rs.getString("Null"), "YES"));
                            meta.setKey("PRI".equalsIgnoreCase(rs.getString("Key")));
                            meta.setUnique("UNI".equalsIgnoreCase(rs.getString("Key")));
                            meta.setDefaultValue(rs.getString("Default"));
                            meta.setGenerated(rs.getString("Extra").contains("GENERATED"));
                            metas.add(meta);
                        }
                        return new TableMeta(schema, table, metas);
                    });
            } catch (Throwable e) {
                logger.error("getTableMetaFromDb failed", e);
                resetMysqlConnection();
                if (++retry >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    private String getDbCharset(String schema) {
        int retry = 0;
        while (true) {
            try {
                String sql = String.format(
                    "SELECT default_character_set_name FROM information_schema.SCHEMATA WHERE schema_name = '%s'",
                    schema);
                return connection.query(sql, rs -> {
                    if (rs.next()) {
                        return rs.getString("default_character_set_name");
                    }
                    return null;
                });
            } catch (Throwable e) {
                logger.error("getDbCharset failed", e);
                resetMysqlConnection();
                if (++retry >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    public boolean rollback(BinlogPosition position) {
        if (memoryTableMeta instanceof PolarxTableMetaProxy) {
            return memoryTableMeta.rollback(position);
        }
        return true;
    }
}
