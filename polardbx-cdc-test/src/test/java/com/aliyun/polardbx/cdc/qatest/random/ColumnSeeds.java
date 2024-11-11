/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.random;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_CREATE_SQL;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_QUERY_SQL;

/**
 * created by ziyang.lb
 **/
public class ColumnSeeds {
    final String dbName;
    final String tableName;
    // <column name, column type>
    final Map<String, String> COLUMN_NAME_COLUMN_TYPE_MAPPING = new ConcurrentHashMap<>();
    // <column type, default value>
    final Map<String, List<String>> COLUMN_TYPE_DEFAULT_VALUE_MAPPING = new ConcurrentHashMap<>();
    // <column name, query value>
    final Map<String, Object> COLUMN_NAME_QUERY_VALUE_MAPPING = new ConcurrentHashMap<>();

    final List<String> CHARSET_LIST = Arrays.asList("utf8mb4", "utf8", "gbk");

    private long tableCharsetIndex = 0;

    public ColumnSeeds(String dbName, String tableName) {
        this.dbName = dbName;
        this.tableName = tableName;
    }

    void buildColumnSeeds() throws SQLException {
        build1();
        build2();
        print();
    }

    private void build1() {
        Map<String, String> nameTypeMap = new HashMap<>();
        Map<String, List<String>> typeValueMap = new HashMap<>();

        MemoryTableMeta repository = new MemoryTableMeta(null, false);
        repository.apply(null, "d1", String.format(T_RANDOM_CREATE_SQL, tableName), null);
        TableMeta tableMeta = repository.find("d1", tableName);
        for (TableMeta.FieldMeta fieldMeta : tableMeta.getFields()) {
            if (!StringUtils.equals(fieldMeta.getColumnName(), "id")
                && !StringUtils.equals(fieldMeta.getColumnName(), "c_idx")) {
                nameTypeMap.put(fieldMeta.getColumnName(), fieldMeta.getColumnType());
                typeValueMap.computeIfAbsent(fieldMeta.getColumnType(), k -> new ArrayList<>());
                if (fieldMeta.getDefaultValue() == null) {
                    typeValueMap.get(fieldMeta.getColumnType()).add("NULL");
                } else {
                    typeValueMap.get(fieldMeta.getColumnType()).add(fieldMeta.getDefaultValue());
                }
            }
        }

        COLUMN_NAME_COLUMN_TYPE_MAPPING.putAll(nameTypeMap);
        COLUMN_TYPE_DEFAULT_VALUE_MAPPING.putAll(typeValueMap);
    }

    private void build2() throws SQLException {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeQuery("use " + dbName, connection);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(String.format(T_RANDOM_QUERY_SQL, tableName));
            ResultSetMetaData rsMeta = rs.getMetaData();
            if (rs.next()) {
                for (int index = 1; index <= rsMeta.getColumnCount(); index++) {
                    String columnName = rsMeta.getColumnName(index);
                    Object columnValue = rs.getObject(index);
                    COLUMN_NAME_QUERY_VALUE_MAPPING.put(columnName, columnValue);
                }
            }
        }
    }

    public String randomCharset() {
        return CHARSET_LIST.get((int) (Math.random() * CHARSET_LIST.size()));
    }

    public String nextCharset() {
        return CHARSET_LIST.get(Math.abs((int) (tableCharsetIndex++ % CHARSET_LIST.size())));
    }

    private void print() {
        System.out.println("============================= column name column type mapping ===========================");
        COLUMN_NAME_COLUMN_TYPE_MAPPING.forEach((key, value) -> System.out.println(
            StringUtils.rightPad(key, 30, "") + "   " + StringUtils.rightPad(value, 50, "")));

        System.out.println("============================= column type default value mapping =========================");
        COLUMN_TYPE_DEFAULT_VALUE_MAPPING.forEach((key, value) -> System.out.println(
            StringUtils.rightPad(key, 30, "") + "   " + StringUtils.rightPad(value.toString(), 50, "")));

        System.out.println("============================= column name query value mapping =========================");
        COLUMN_NAME_QUERY_VALUE_MAPPING.forEach((key, value) -> System.out.println(
            StringUtils.rightPad(key, 50, "") + "   " + StringUtils.rightPad(toStr(value), 50, "")
                + "     " + value.getClass()));
    }

    private static String toStr(Object value) {
        if (value instanceof byte[]) {
            return Arrays.toString((byte[]) value);
        } else {
            return value.toString();
        }
    }
}
