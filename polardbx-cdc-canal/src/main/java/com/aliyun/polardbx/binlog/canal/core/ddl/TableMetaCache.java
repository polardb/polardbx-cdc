/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.canal.core.ddl;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.ConsoleTableMetaTSDB;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private MysqlConnection connection;
    private MemoryTableMeta memoryTableMeta;

    private int MAX_RETRY = 3;

    public void reset() {
        memoryTableMeta.destory();
    }

    public TableMetaCache(MysqlConnection connection) {
        this.connection = connection;
        this.memoryTableMeta = new MemoryTableMeta(logger);
    }

    public TableMeta getTableMeta(String schema, String table) throws Throwable {
        TableMeta tableMeta = memoryTableMeta.find(schema, table);
        if (tableMeta == null) {
            String fullName = getFullName(schema, table);
            tableMeta = getTableMetaFromDb(fullName);
        }

        if (StringUtils.isBlank(tableMeta.getCharset())) {
            String dbCharset = getDbCharset(schema);
            tableMeta.setCharset(dbCharset);
        }
        return tableMeta;
    }

    public boolean apply(BinlogPosition position, String schema, String ddl) {
        return memoryTableMeta.apply(position, schema, ddl, "");
    }

    private String getFullName(String schema, String table) {
        StringBuilder builder = new StringBuilder();
        return builder.append('`')
            .append(schema)
            .append('`')
            .append('.')
            .append('`')
            .append(table)
            .append('`')
            .toString();
    }

    private TableMeta getTableMetaFromDb(final String fullname) throws Throwable {
        int retry = 0;

        while (true) {
            try {
                // 优先使用show create table处理
                TableMeta tableMeta = connection.query("show create table " + fullname, rs -> {
                    String[] names = StringUtils.split(fullname, "`.`");
                    String schema = names[0];
                    String table = names[1];

                    String createDDL = null;
                    while (rs.next()) {
                        createDDL = rs.getString(2);
                    }

                    memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, schema, createDDL, null);
                    return memoryTableMeta.find(schema, table);
                });

                if (tableMeta != null) {
                    return tableMeta;
                }

                // fallback
                return connection.query("desc " + fullname, rs -> {
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
                        metas.add(meta);
                    }

                    String[] names = StringUtils.split(fullname, "`.`");
                    String schema = names[0];
                    String table = names[1];
                    return new TableMeta(schema, table, metas);
                });
            } catch (Throwable e) {
                logger.error("getTableMetaFromDb failed", e);
                if (++retry >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    private String getDbCharset(String schema) {
        String sql = String.format(
            "SELECT default_character_set_name FROM information_schema.SCHEMATA WHERE schema_name = '%s'",
            schema);
        String charset = connection.query(sql, rs -> {
            rs.next();
            return rs.getString("default_character_set_name");
        });
        return charset;
    }
}
