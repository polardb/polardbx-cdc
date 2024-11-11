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
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidSqlGenerator;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author yudong
 * @since 2023/10/9 11:37
 **/
public class ReplicaFullValidSqlGeneratorTest extends BaseTest {

    @SneakyThrows
    @Test
    public void replicaHashSqlWithSinglePkTest() {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_full_valid_sql_test_single_pk";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Collections.singletonList("id"));

        // no bound val
        Assert.assertEquals("REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk`",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, false));
        // lower bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) > (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, false));
        // upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) <= (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, true));
        // lower & upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) > (?) AND (`id`) <= (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, true));
    }

    @SneakyThrows
    @Test
    public void replicaHashSqlWithMultiPkTest() {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_full_valid_sql_test_multi_pk";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Arrays.asList("pk1", "pk2"));

        // no bound val
        Assert.assertEquals("REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk`",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, false));
        // lower bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) > (?,?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, false));
        // upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) <= (?,?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, true));
        // lower & upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) > (?,?) AND (`pk1`,`pk2`) <= (?,?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, true));
    }

    @SneakyThrows
    @Test
    public void replicaHashSqlWithSpecialNameTest() {
        String schema = "polardbx_meta_db";
        String tbName = "`rpl_full_valid_sql_test_special-name`";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Collections.singletonList("`id`"));
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(new ColumnInfo("`id`", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("name", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("`info`", 0, null, true, true, null, 0));
        tableInfo.setColumns(columns);

        // no bound val
        Assert.assertEquals("REPLICA HASHCHECK * FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name```",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, false));
        // lower bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) > (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, false));
        // upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) <= (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, false, true));
        // lower & upper bound val
        Assert.assertEquals(
            "REPLICA HASHCHECK * FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) > (?) AND (```id```) <= (?)",
            ReplicaFullValidSqlGenerator.buildRplHashCheckSql(tableInfo, true, true));
    }

    @SneakyThrows
    @Test
    public void checksumSqlWithSinglePkTest() {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_full_valid_sql_test_single_pk";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Collections.singletonList("id"));
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(new ColumnInfo("id", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("name", 0, null, true, true, null, 0));
        tableInfo.setColumns(columns);

        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `id`, `name`, ISNULL(`id`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `id` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` ORDER BY `id`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `id`, `name`, ISNULL(`id`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `id` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) > (?) ORDER BY `id`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `id`, `name`, ISNULL(`id`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `id` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) <= (?) ORDER BY `id`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, true));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `id`, `name`, ISNULL(`id`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `id` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_single_pk` WHERE (`id`) > (?) AND (`id`) <= (?) ORDER BY `id`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, true));
    }

    @SneakyThrows
    @Test
    public void checksumSqlWithMultiPkTest() {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_full_valid_sql_test_multi_pk";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Arrays.asList("pk1", "pk2"));
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(new ColumnInfo("pk1", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("pk2", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("name", 0, null, true, true, null, 0));
        tableInfo.setColumns(columns);

        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `pk1`, `pk2`, `name`, ISNULL(`pk1`), ISNULL(`pk2`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `pk1`,`pk2` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` ORDER BY `pk1`,`pk2`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `pk1`, `pk2`, `name`, ISNULL(`pk1`), ISNULL(`pk2`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `pk1`,`pk2` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) > (?,?) ORDER BY `pk1`,`pk2`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `pk1`, `pk2`, `name`, ISNULL(`pk1`), ISNULL(`pk2`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `pk1`,`pk2` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) <= (?,?) ORDER BY `pk1`,`pk2`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, true));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', `pk1`, `pk2`, `name`, ISNULL(`pk1`), ISNULL(`pk2`), ISNULL(`name`))) AS UNSIGNED) AS checksum, `pk1`,`pk2` FROM `polardbx_meta_db`.`rpl_full_valid_sql_test_multi_pk` WHERE (`pk1`,`pk2`) > (?,?) AND (`pk1`,`pk2`) <= (?,?) ORDER BY `pk1`,`pk2`",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, true));
    }

    @SneakyThrows
    @Test
    public void checksumSqlWithSpecialNameTest() {
        String schema = "polardbx_meta_db";
        String tbName = "`rpl_full_valid_sql_test_special-name`";

        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        tableInfo.setPks(Collections.singletonList("`id`"));
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(new ColumnInfo("`id`", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("name", 0, null, true, true, null, 0));
        columns.add(new ColumnInfo("`info`", 0, null, true, true, null, 0));
        tableInfo.setColumns(columns);

        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', ```id```, `name`, ```info```, ISNULL(```id```), ISNULL(`name`), ISNULL(```info```))) AS UNSIGNED) AS checksum, ```id``` FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` ORDER BY ```id```",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', ```id```, `name`, ```info```, ISNULL(```id```), ISNULL(`name`), ISNULL(```info```))) AS UNSIGNED) AS checksum, ```id``` FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) > (?) ORDER BY ```id```",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, false));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', ```id```, `name`, ```info```, ISNULL(```id```), ISNULL(`name`), ISNULL(```info```))) AS UNSIGNED) AS checksum, ```id``` FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) <= (?) ORDER BY ```id```",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, false, true));
        Assert.assertEquals(
            "SELECT CAST(CRC32(CONCAT_WS(',', ```id```, `name`, ```info```, ISNULL(```id```), ISNULL(`name`), ISNULL(```info```))) AS UNSIGNED) AS checksum, ```id``` FROM `polardbx_meta_db`.```rpl_full_valid_sql_test_special-name``` WHERE (```id```) > (?) AND (```id```) <= (?) ORDER BY ```id```",
            ReplicaFullValidSqlGenerator.buildRowCheckSumSql(tableInfo, true, true));
    }
}
