/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author yudong
 * @since 2024/1/16 17:35
 **/
public class ValSQLGeneratorTest extends BaseTest {

    @Test
    @SneakyThrows
    public void testBatchCheckForTableWithMultiPrimaryKeys() {
        TableInfo tableInfo =
            DbMetaManager.getTableInfo(getGmsDataSource(), "valSqlGeneratorTest", "test_tb", HostType.RDS, false);
        tableInfo.setPks(Arrays.asList("pk1", "pk2", "pk3"));
        List<ColumnInfo> columns = tableInfo.getColumns();
        columns.add(new ColumnInfo("col1", 0, null, false, false, null, 0));
        columns.add(new ColumnInfo("col2", 0, null, false, false, null, 0));
        columns.add(new ColumnInfo("col3", 0, null, false, false, null, 0));

        List<Object> lowerBounds = new ArrayList<>();
        lowerBounds.add(1);
        lowerBounds.add(2);
        lowerBounds.add(3);
        List<Object> upperBounds = new ArrayList<>();
        upperBounds.add(4);
        upperBounds.add(5);
        upperBounds.add(6);

        // test without any bounds
        SqlContextBuilder.SqlContext queryContext =
            ValSQLGenerator.getBatchCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, null, null);
        Assert.assertEquals(
            "SELECT COUNT(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED)) AS CHECKSUM FROM `valSqlGeneratorTest`.`test_tb`",
            queryContext.sql);
        Assert.assertEquals(0, queryContext.params.size());

        // test with lowerBounds
        queryContext =
            ValSQLGenerator.getBatchCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, lowerBounds, null);
        Assert.assertEquals(
            "SELECT COUNT(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED)) AS CHECKSUM FROM `valSqlGeneratorTest`.`test_tb` WHERE (`pk1` >= ?) OR (`pk1` = ? AND `pk2` >= ?) OR (`pk1` = ? AND `pk2` = ? AND `pk3` >= ?)",
            queryContext.sql);

        // test with upperBounds
        queryContext =
            ValSQLGenerator.getBatchCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, null, upperBounds);
        Assert.assertEquals(
            "SELECT COUNT(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED)) AS CHECKSUM FROM `valSqlGeneratorTest`.`test_tb` WHERE (pk1 < ?) OR (pk1 = ? AND pk2 < ?) OR (pk1 = ? AND pk2 = ? AND pk3 < ?)",
            queryContext.sql);

        // test with lowerBounds and upperBounds
        queryContext =
            ValSQLGenerator.getBatchCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, lowerBounds,
                upperBounds);
        Assert.assertEquals(
            "SELECT COUNT(*) as CNT, BIT_XOR(CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED)) AS CHECKSUM FROM `valSqlGeneratorTest`.`test_tb` WHERE ((`pk1` >= ?) OR (`pk1` = ? AND `pk2` >= ?) OR (`pk1` = ? AND `pk2` = ? AND `pk3` >= ?)) AND ((pk1 < ?) OR (pk1 = ? AND pk2 < ?) OR (pk1 = ? AND pk2 = ? AND pk3 < ?))",
            queryContext.sql);
    }

    @Test
    @SneakyThrows
    public void testRowCheckForTableWithSinglePrimaryKey() {
        TableInfo tableInfo =
            DbMetaManager.getTableInfo(getGmsDataSource(), "valSqlGeneratorTest", "test_tb", HostType.RDS, false);
        tableInfo.setPks(Collections.singletonList("`pk`"));
        List<ColumnInfo> columns = tableInfo.getColumns();
        columns.add(new ColumnInfo("col1", 0, null, false, false, null, 0));
        columns.add(new ColumnInfo("col2", 0, null, false, false, null, 0));
        columns.add(new ColumnInfo("col3", 0, null, false, false, null, 0));
        tableInfo.setDbShardKey("db_shard_key");
        tableInfo.setTbShardKey("tb_shard_key");

        List<Object> lowerBounds = new ArrayList<>();
        lowerBounds.add(1);
        List<Object> upperBounds = new ArrayList<>();
        upperBounds.add(4);

        // test without any bounds
        SqlContextBuilder.SqlContext queryContext =
            ValSQLGenerator.getRowCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, null, null);
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED) AS CHECKSUM, ```pk```, `db_shard_key`, `tb_shard_key` FROM `valSqlGeneratorTest`.`test_tb` ORDER BY ```pk```, `db_shard_key`, `tb_shard_key`",
            queryContext.sql);
        Assert.assertEquals(0, queryContext.params.size());

        // test with lowerBounds
        queryContext =
            ValSQLGenerator.getRowCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, lowerBounds, null);
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED) AS CHECKSUM, ```pk```, `db_shard_key`, `tb_shard_key` FROM `valSqlGeneratorTest`.`test_tb` WHERE (```pk``` >= ?) ORDER BY ```pk```, `db_shard_key`, `tb_shard_key`",
            queryContext.sql);

        // test with upperBounds
        queryContext =
            ValSQLGenerator.getRowCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, null, upperBounds);
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED) AS CHECKSUM, ```pk```, `db_shard_key`, `tb_shard_key` FROM `valSqlGeneratorTest`.`test_tb` WHERE (`pk` < ?) ORDER BY ```pk```, `db_shard_key`, `tb_shard_key`",
            queryContext.sql);

        // test with lowerBounds and upperBounds
        queryContext =
            ValSQLGenerator.getRowCheckSql(tableInfo.getSchema(), tableInfo.getName(), tableInfo, lowerBounds,
                upperBounds);
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `col1`, `col2`, `col3`, CONCAT(ISNULL(`col1`), ISNULL(`col2`), ISNULL(`col3`))))AS UNSIGNED) AS CHECKSUM, ```pk```, `db_shard_key`, `tb_shard_key` FROM `valSqlGeneratorTest`.`test_tb` WHERE ((```pk``` >= ?)) AND ((`pk` < ?)) ORDER BY ```pk```, `db_shard_key`, `tb_shard_key`",
            queryContext.sql);
    }

}
