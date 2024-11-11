/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * @author yudong
 * @since 2023/10/9 11:21
 **/
public class ReplicaFullValidSqlGenerator {
    private static final String REPLICA_HASHCHECK_WITHOUT_BOUND = "REPLICA HASHCHECK * FROM `%s`.`%s`";
    private static final String REPLICA_HASHCHECK_WITH_LOWER_BOUND = "REPLICA HASHCHECK * FROM `%s`.`%s` WHERE %s > %s";
    private static final String REPLICA_HASHCHECK_WITH_UPPER_BOUND =
        "REPLICA HASHCHECK * FROM `%s`.`%s` WHERE %s <= %s";
    private static final String REPLICA_HASHCHECK_WITH_LOWER_UPPER_BOUND =
        "REPLICA HASHCHECK * FROM `%s`.`%s` WHERE %s > %s AND %s <= %s";
    private static final String CHECK_SUM_WITHOUT_BOUND =
        "SELECT CAST(CRC32(%s) AS UNSIGNED) AS checksum, %s FROM `%s`.`%s` ORDER BY %s";
    private static final String CHECK_SUM_WITH_LOWER_BOUND =
        "SELECT CAST(CRC32(%s) AS UNSIGNED) AS checksum, %s FROM `%s`.`%s` WHERE %s > %s ORDER BY %s";
    private static final String CHECK_SUM_WITH_UPPER_BOUND =
        "SELECT CAST(CRC32(%s) AS UNSIGNED) AS checksum, %s FROM `%s`.`%s` WHERE %s <= %s ORDER BY %s";
    private static final String CHECK_SUM_WITH_LOWER_UPPER_BOUND =
        "SELECT CAST(CRC32(%s) AS UNSIGNED) AS checksum, %s FROM `%s`.`%s` WHERE %s > %s AND %s <= %s ORDER BY %s";

    /**
     * 返回一个SQL，用于对逻辑表的指定主键范围计算出一个checksum
     *
     * @param tableInfo table
     * @return sql template
     */
    public static String buildRplHashCheckSql(TableInfo tableInfo, boolean withLowerBound, boolean withUpperBound) {
        String dbName = escape(tableInfo.getSchema());
        String tableName = escape(tableInfo.getName());

        if (!withLowerBound && !withUpperBound) {
            return String.format(REPLICA_HASHCHECK_WITHOUT_BOUND, dbName, tableName);
        }

        List<String> primaryKeys = tableInfo.getPks();
        String pkNameStr = convertPrimaryKeysToStr(primaryKeys);
        String pkValuePlaceHolder = buildPrimaryKeyPlaceHolderStr(primaryKeys.size());

        if (withLowerBound && !withUpperBound) {
            return String.format(REPLICA_HASHCHECK_WITH_LOWER_BOUND, dbName, tableName, pkNameStr, pkValuePlaceHolder);
        } else if (!withLowerBound) {
            return String.format(REPLICA_HASHCHECK_WITH_UPPER_BOUND, dbName, tableName, pkNameStr, pkValuePlaceHolder);
        } else {
            return String.format(REPLICA_HASHCHECK_WITH_LOWER_UPPER_BOUND, dbName, tableName, pkNameStr,
                pkValuePlaceHolder, pkNameStr, pkValuePlaceHolder);
        }
    }

    /**
     * 返回一个SQL，用于select出每一行的主键以及该行的checksum
     *
     * @param tableInfo table
     * @return SQL
     */
    public static String buildRowCheckSumSql(TableInfo tableInfo, boolean withLowerBound, boolean withUpperBound) {
        String dbName = escape(tableInfo.getSchema());
        String tbName = escape(tableInfo.getName());

        // ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)
        StringBuilder isNullSb = new StringBuilder();
        List<ColumnInfo> columns = tableInfo.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (i == 0) {
                isNullSb.append(String.format("ISNULL(`%s`)", escape(columns.get(i).getName())));
            } else {
                isNullSb.append(String.format(", ISNULL(`%s`)", escape(columns.get(i).getName())));
            }
        }

        // ',', `id`, `name`, `order_id`,
        StringBuilder columnListSb = new StringBuilder();
        columnListSb.append("',', ");
        for (ColumnInfo column : columns) {
            columnListSb.append(String.format("`%s`, ", escape(column.getName())));
        }

        // CONCAT_WS(',', `id`, `name`, `order_id`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", columnListSb.append(isNullSb));

        // `id`, `order_id`
        List<String> keys = tableInfo.getKeyList();
        String keyCols = keys.stream().map(CommonUtils::escape).collect(Collectors.joining("`,`", "`", "`"));

        if (!withLowerBound && !withUpperBound) {
            return String.format(CHECK_SUM_WITHOUT_BOUND, concatWs, keyCols, dbName, tbName, keyCols);
        }

        List<String> primaryKeys = tableInfo.getPks();
        String pkNameStr = convertPrimaryKeysToStr(primaryKeys);
        String pkValuePlaceHolder = buildPrimaryKeyPlaceHolderStr(primaryKeys.size());

        if (withLowerBound && !withUpperBound) {
            return String.format(CHECK_SUM_WITH_LOWER_BOUND, concatWs, keyCols, dbName, tbName, pkNameStr,
                pkValuePlaceHolder, keyCols);
        } else if (!withLowerBound) {
            return String.format(CHECK_SUM_WITH_UPPER_BOUND, concatWs, keyCols, dbName, tbName, pkNameStr,
                pkValuePlaceHolder, keyCols);
        } else {
            return String.format(CHECK_SUM_WITH_LOWER_UPPER_BOUND, concatWs, keyCols, dbName, tbName, pkNameStr,
                pkValuePlaceHolder, pkNameStr, pkValuePlaceHolder, keyCols);
        }
    }

    private static String convertPrimaryKeysToStr(List<String> primaryKeys) {
        if (CollectionUtils.isEmpty(primaryKeys)) {
            throw new IllegalArgumentException("primary keys is empty!");
        }

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < primaryKeys.size() - 1; i++) {
            sb.append("`").append(escape(primaryKeys.get(i))).append("`").append(",");
        }
        sb.append("`").append(escape(primaryKeys.get(primaryKeys.size() - 1))).append("`").append(")");
        return sb.toString();
    }

    private static String buildPrimaryKeyPlaceHolderStr(int pkNum) {
        if (pkNum <= 0) {
            throw new IllegalArgumentException("illegal primary key number!");
        }

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < pkNum - 1; i++) {
            sb.append("?,");
        }
        sb.append("?").append(")");
        return sb.toString();
    }

}
