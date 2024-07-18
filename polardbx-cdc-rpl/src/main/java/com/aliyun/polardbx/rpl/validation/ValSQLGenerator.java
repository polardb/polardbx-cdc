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
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.rpl.validation.common.ValidationUtil.escape;

/**
 * Validation SQL generator per table
 *
 * @author siyu.yusi
 **/
@Builder
@Slf4j
public class ValSQLGenerator {
    private boolean convertToByte;
    private static final String CHECKSUM = "checksum";

    private static final String BATCH_CHECK_SQL_TEMPLATE =
        "SELECT COUNT(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', %s, CONCAT(%s)))AS UNSIGNED)) AS CHECKSUM FROM %s";

    private static final String ROW_CHECK_SQL_TEMPLATE =
        "SELECT CAST(CRC32(CONCAT_WS(',', %s, CONCAT(%s)))AS UNSIGNED) AS CHECKSUM, %s FROM %s";

    private static final String SELECT_SQL_TEMPLATE =
        "SELECT %s FROM %s";

    private static final String REPLACE_INTO_TEMPLATE =
        "REPLACE INTO %s (%s) VALUES (%s)";

    private static final String DELETE_FROM_TEMPLATE =
        "DELETE FROM %s";

    /**
     * calculate CRC32 checksum and count example:
     * mysql> select count(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', id, name, age, CONCAT(ISNULL(id), ISNULL(name), ISNULL(age))) AS UNSIGNED)) AS CHECKSUM from `test_db`.`test_tb` where id > 0;
     * +--------+------------+
     * |  CNT   |  CHECKSUM  |
     * +--------+------------+
     * | 100000 | 1128664311 |
     * +--------+------------+
     * 1 row in set (0.46 sec)
     */
    public static SqlContextBuilder.SqlContext getBatchCheckSql(String dbName, String tableName, TableInfo srcTableInfo,
                                                                List<Object> lowerBounds, List<Object> upperBounds) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnInfo col : srcTableInfo.getColumns()) {
            columnNames.add(col.getName());
        }

        String columnNamesStr =
            columnNames.stream().map(k -> String.format("`%s`", escape(k))).collect(Collectors.joining(", "));
        String columnIsNullStr =
            columnNames.stream().map(k -> String.format("ISNULL(`%s`)", escape(k))).collect(Collectors.joining(", "));
        String sqlBeforeWhere = String.format(BATCH_CHECK_SQL_TEMPLATE, columnNamesStr, columnIsNullStr,
            CommonUtils.tableName(dbName, tableName));

        SqlContextBuilder.SqlContext result =
            SqlContextBuilder.buildRangeQueryContext(sqlBeforeWhere, null, srcTableInfo.getPks(), lowerBounds,
                upperBounds);

        if (log.isDebugEnabled()) {
            log.debug("batch check sql:{}, params:{}", result.sql, result.params);
        }

        return result;
    }

    /**
     * 返回一个SQL，用于查询出给定区间内每一行的主键 & 拆分键，以及该行的checksum
     */
    public static SqlContextBuilder.SqlContext getRowCheckSql(String dbName, String tableName, TableInfo srcTableInfo,
                                                              List<Object> lowerBounds, List<Object> upperBounds) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnInfo col : srcTableInfo.getColumns()) {
            columnNames.add(col.getName());
        }

        List<String> keys = srcTableInfo.getKeyList();
        String keyCols =
            keys.stream().map(k -> String.format("`%s`", escape(k))).collect(Collectors.joining(", "));
        String columnNamesStr =
            columnNames.stream().map(k -> String.format("`%s`", escape(k))).collect(Collectors.joining(", "));
        String columnIsNullStr =
            columnNames.stream().map(k -> String.format("ISNULL(`%s`)", escape(k))).collect(Collectors.joining(", "));

        String sqlBeforeWhere = String.format(ROW_CHECK_SQL_TEMPLATE, columnNamesStr, columnIsNullStr, keyCols,
            CommonUtils.tableName(dbName, tableName));
        String sqlAfterWhere = " ORDER BY " + keyCols;

        SqlContextBuilder.SqlContext result =
            SqlContextBuilder.buildRangeQueryContext(sqlBeforeWhere, sqlAfterWhere, srcTableInfo.getPks(), lowerBounds,
                upperBounds);

        if (log.isDebugEnabled()) {
            log.debug("row check sql:{}, params:{}", result.sql, result.params);
        }

        return result;
    }

    public static SqlContextBuilder.SqlContext getPointSelectSql(TableInfo tableInfo, List<Object> keyVal) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnInfo col : tableInfo.getColumns()) {
            columnNames.add(col.getName());
        }

        String columnNamesStr =
            columnNames.stream().map(k -> String.format("`%s`", escape(k))).collect(Collectors.joining(", "));
        String sqlBeforeWhere = String.format(SELECT_SQL_TEMPLATE, columnNamesStr,
            CommonUtils.tableName(tableInfo.getSchema(), tableInfo.getName()));

        SqlContextBuilder.SqlContext result =
            SqlContextBuilder.buildPointQueryContext(sqlBeforeWhere, tableInfo.getKeyList(), keyVal);

        if (log.isDebugEnabled()) {
            log.debug("point select sql:{}, params:{}", result.sql, result.params);
        }

        return result;
    }

    public static SqlContextBuilder.SqlContext getReplaceIntoSql(String dbName, String tableName, TableInfo tableInfo,
                                                                 List<Object> row) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnInfo col : tableInfo.getColumns()) {
            columnNames.add(col.getName());
        }
        String columnNamesStr =
            columnNames.stream().map(k -> String.format("`%s`", escape(k))).collect(Collectors.joining(", "));
        StringBuilder valuesSb = new StringBuilder();
        for (int i = 0; i < columnNames.size(); i++) {
            if (i == 0) {
                valuesSb.append("?");
            } else {
                valuesSb.append(", ?");
            }
        }

        String sql =
            String.format(REPLACE_INTO_TEMPLATE, CommonUtils.tableName(dbName, tableName), columnNamesStr, valuesSb);

        if (log.isDebugEnabled()) {
            log.debug("replace into sql:{}", sql);
        }

        return new SqlContextBuilder.SqlContext(sql, row);
    }

    public static SqlContextBuilder.SqlContext getDeleteFromSql(String dbName, String tableName, TableInfo tableInfo,
                                                                List<Object> keyVal) {
        String sqlBeforeWhere =
            String.format(DELETE_FROM_TEMPLATE, CommonUtils.tableName(dbName, tableName));

        SqlContextBuilder.SqlContext sqlContext =
            SqlContextBuilder.buildPointQueryContext(sqlBeforeWhere, tableInfo.getKeyList(), keyVal);

        if (log.isDebugEnabled()) {
            log.debug("point delete sql:{}", sqlContext.getSql());
        }

        return sqlContext;
    }
}
