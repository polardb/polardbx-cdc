/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.full;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * 全量使用的工具类
 *
 * @author sunxi'en
 * @since 1.0.0
 */
public class ExtractorUtil {

    private static final boolean DEFAULT_NULLABLE = true;
    private static final boolean DEFAULT_SIGNED = true;

    /**
     * @return Object
     */
    public static Object getColumnValue(ResultSet resultSet, String columnName, int type) throws SQLException {
        Object value;
        if (type == Types.TIME || type == Types.DATE || type == Types.TIMESTAMP || isCharType(type)
            || isClobType(type)) {
            value = resultSet.getString(columnName);
        } else if (isBlobType(type)) {
            value = resultSet.getBytes(columnName);
        } else if (Types.BIT == type) {
            // 需要特殊处理tinyint(1)
            value = resultSet.getBytes(columnName);
        } else {
            value = resultSet.getString(columnName);
        }
        // 使用clone对象，避免translator修改了引用
        return value;
    }

    public static boolean isCharType(int sqlType) {
        return (sqlType == Types.CHAR || sqlType == Types.VARCHAR || sqlType == Types.NCHAR
            || sqlType == Types.NVARCHAR);
    }

    public static boolean isClobType(int sqlType) {
        return (sqlType == Types.CLOB || sqlType == Types.LONGVARCHAR || sqlType == Types.NCLOB
            || sqlType == Types.LONGNVARCHAR);
    }

    public static boolean isBlobType(int sqlType) {
        return (sqlType == Types.BLOB || sqlType == Types.BINARY || sqlType == Types.VARBINARY
            || sqlType == Types.LONGVARBINARY);
    }

    public static boolean isNumber(int sqlType) {
        return (sqlType == Types.TINYINT || sqlType == Types.SMALLINT || sqlType == Types.INTEGER
            || sqlType == Types.BIGINT || sqlType == Types.NUMERIC || sqlType == Types.DECIMAL);
    }

    public static boolean isInteger(int sqlType) {
        return (sqlType == Types.TINYINT || sqlType == Types.SMALLINT || sqlType == Types.INTEGER
            || sqlType == Types.BIGINT);
    }

    public static boolean isFloat(int sqlType) {
        return (sqlType == Types.FLOAT || sqlType == Types.DOUBLE || sqlType == Types.NUMERIC
            || sqlType == Types.DECIMAL);
    }

    public static boolean isCaseInsensitiveCollation(String collation) {
        if (collation == null) {
            return false;
        }

        String lowerCollation = collation.toLowerCase();

        // 大小写不敏感的 collation 通常包含 "_ci" 后缀
        // 例如: utf8mb4_general_ci, utf8mb4_0900_ai_ci, latin1_swedish_ci 等
        if (lowerCollation.endsWith("_ci")) {
            return true;
        }

        // 以下类型的排序规则是大小写敏感的，不包含在大小写不敏感的判断中：
        // 1. 二进制排序规则（如 utf8mb4_bin, utf8_bin）
        // 2. 大小写敏感排序规则（如 utf8mb4_cs_as_cs）
        // 3. 大小写敏感和重音敏感排序规则（如 utf8mb4_0900_as_cs）
        if (lowerCollation.endsWith("_bin") || lowerCollation.contains("_cs")) {
            return false;
        }

        // 对于其他情况，如果不符合明确的大小写敏感模式，则默认为大小写敏感
        // 这种情况比较少见，通常需要根据具体的排序规则名称来判断
        return false;
    }

    public static RowChangeBuilder buildRowChangeMeta(TableInfo tableInfo, String schema, String tbName,
                                                      DBMSAction action) {
        RowChangeBuilder builder = RowChangeBuilder.createBuilder(schema, tbName, action);
        for (ColumnInfo column : tableInfo.getColumns()) {
            builder.addMetaColumn(column.getName(),
                column.getType(),
                DEFAULT_SIGNED,
                DEFAULT_NULLABLE,
                tableInfo.getPks().contains(column.getName()));
        }
        for (ColumnInfo column : tableInfo.getColumns()) {
            if (column.isGenerated()) {
                for (DBMSColumn column1 : builder.getMetaColumns()) {
                    if (column1.getName().equals(column.getName())) {
                        ((DefaultColumn) column1).setGenerated(true);
                        break;
                    }
                }
            }
        }
        return builder;
    }

    public static void addRowData(RowChangeBuilder builder, TableInfo tableInfo, ResultSet resultSet) throws Exception {
        Map<String, Serializable> fieldValueMap = new HashMap<>(tableInfo.getColumns().size());
        for (ColumnInfo column : tableInfo.getColumns()) {
            Object value = ExtractorUtil.getColumnValue(resultSet, column.getName(), column.getType());
            fieldValueMap.put(column.getName(), (Serializable) value);
        }
        builder.addRowData(fieldValueMap);
    }

}

