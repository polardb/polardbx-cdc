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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSql;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForMysqlPart;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForPolarPart;

/**
 * created by ziyang.lb
 **/
public class DDLConverterTest extends BaseTest {

    @Test
    public void testTryRemoveDropImplicitPk() {
        String sql = "alter table modify_sk_simple_checker_test_tblPF drop column _drds_implicit_id_";

        SQLAlterTableStatement sqlStatement = (SQLAlterTableStatement) SQLUtils.parseSQLStatement(sql);
        assert sqlStatement != null;
        sqlStatement.getItems().forEach(DDLConverter::tryRemoveDropImplicitPk);
        Assert.assertEquals("ALTER TABLE modify_sk_simple_checker_test_tblPF ", sqlStatement.toUnformattedString());

        String ddlEventSql = buildDdlEventSql(null, sql, "utf8mb4", "utf8mb4_unicode_520_ci", "111", sql);
        String expectResult = "# POLARX_ORIGIN_SQL=ALTER TABLE modify_sk_simple_checker_test_tblPF \n"
            + "# POLARX_TSO=111\n"
            + "ALTER TABLE modify_sk_simple_checker_test_tblPF ";
        Assert.assertEquals(expectResult, ddlEventSql);
    }

    @Test
    public void testBuildDdlEventSqlForMysqlPart() {
        /*
         * test if partition info with table can be removed
         */
        String ddl = "CREATE PARTITION TABLE `wp_users_user_email` (\n"
            + " `ID` bigint(20) UNSIGNED NOT NULL,\n"
            + " `user_email` varchar(100) COLLATE utf8mb4_unicode_520_ci NOT NULL DEFAULT '',\n"
            + "  PRIMARY KEY (`ID`),\n"
            + "  KEY `auto_shard_key_user_email`(`user_email`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci  "
            + " dbpartition by hash(`user_email`) ";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "wp_users_user_email", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `wp_users_user_email` ( `ID` bigint(20) UNSIGNED NOT NULL, `user_email` varchar(100) COLLATE utf8mb4_unicode_520_ci NOT NULL DEFAULT '', PRIMARY KEY (`ID`), KEY `auto_shard_key_user_email` (`user_email`) ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci",
            sb.toString());

        /*
          test if implicit pk info can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE TABLE `zqhz0kzsecxfgdf` (\n"
            + "  `zsjzmjsoidxxtr` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `7jg0ekks` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `3pf6xdowmaf` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `hkqh6gd` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  _drds_implicit_id_ bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "  PRIMARY KEY (_drds_implicit_id_)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        buildDdlEventSqlForMysqlPart(sb, "zqhz0kzsecxfgdf", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `zqhz0kzsecxfgdf` ( `zsjzmjsoidxxtr` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `7jg0ekks` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `3pf6xdowmaf` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `hkqh6gd` int(6) UNSIGNED ZEROFILL DEFAULT NULL ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        /*
         * test if broadcast info can be removed
         */
        ddl = "CREATE TABLE `bt` (\n"
            + " `id` int(11) NOT NULL AUTO_INCREMENT BY GROUP,\n"
            + " `name` varchar(20) DEFAULT NULL,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB AUTO_INCREMENT = 200006 DEFAULT CHARSET = utf8mb4 broadcast";
        sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "bt", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `bt` ( `id` int(11) NOT NULL AUTO_INCREMENT, `name` varchar(20) DEFAULT NULL, PRIMARY KEY (`id`) ) ENGINE = InnoDB AUTO_INCREMENT = 200006 DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        /*
         * test if single info can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE TABLE hash_test4 (\n"
            + "id int NOT NULL,\n"
            + "PRIMARY KEY (id)\n"
            + ") single";
        buildDdlEventSqlForMysqlPart(sb, "hash_test4", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE hash_test4 ( id int NOT NULL, PRIMARY KEY (id) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        /*
         * test if locality info can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE TABLE IF NOT EXISTS `t0` ( "
            + "`c1` bigint NOT NULL, "
            + "`c2` date NOT NULL, "
            + "`c3` double NOT NULL )"
            + " SINGLE LOCALITY 'balance_single_table=on'";
        buildDdlEventSqlForMysqlPart(sb, "t0", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE IF NOT EXISTS `t0` ( `c1` bigint NOT NULL, `c2` date NOT NULL, `c3` double NOT NULL ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        /*
         * test create table like
         */
        sb = new StringBuilder();
        ddl = "CREATE TABLE dn_gen_col_comment_2 LIKE dn_gen_col_comment";
        buildDdlEventSqlForMysqlPart(sb, "dn_gen_col_comment_2", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE TABLE dn_gen_col_comment_2 LIKE dn_gen_col_comment", sb.toString());

        /*
         * test if tablegroup info can be removed
         */
        sb = new StringBuilder();
        ddl = "create table `meng``shi1` ("
            + "`a` int(11) not null, "
            + "`b` char(1) default null, "
            + "`c` double default null, "
            + " primary key (`a`) )"
            + " engine = innodb default charset = utf8mb4 "
            + " default character set = utf8mb4 default collate = utf8mb4_general_ci tablegroup `tgtest`";
        buildDdlEventSqlForMysqlPart(sb, "meng`shi1", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `meng``shi1` ( `a` int(11) NOT NULL, `b` char(1) DEFAULT NULL, `c` double DEFAULT NULL, PRIMARY KEY (`a`) ) ENGINE = innodb DEFAULT CHARSET = utf8mb4 DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());

        /*
         * test if clustered index info can be remove
         */
        sb = new StringBuilder();
        ddl = "ALTER TABLE `auto_partition_idx_tb`\n"
            + "\tADD UNIQUE CLUSTERED INDEX `ap_index` (`id`)";
        buildDdlEventSqlForMysqlPart(sb, "auto_partition_idx_tb", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE `auto_partition_idx_tb` ADD UNIQUE INDEX `ap_index` (`id`)", sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE UNIQUE CLUSTERED INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)";
        buildDdlEventSqlForMysqlPart(sb, "auto_partition_idx_tb", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE UNIQUE INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)", sb.toString());

        /*
         * test if local index info can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE LOCAL INDEX l_i_idx_with_clustered ON auto_idx_with_clustered (i)";
        buildDdlEventSqlForMysqlPart(sb, "auto_idx_with_clustered", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE INDEX l_i_idx_with_clustered ON auto_idx_with_clustered (i)", sb.toString());

        sb = new StringBuilder();
        ddl = "ALTER TABLE auto_idx_with_clustered ADD LOCAL INDEX l_i_idx_with_clustered (i)";
        buildDdlEventSqlForMysqlPart(sb, "auto_idx_with_clustered", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE auto_idx_with_clustered ADD INDEX l_i_idx_with_clustered (i)", sb.toString());

        /*
         * test if partition info with index can be removed
         */
        sb = new StringBuilder();
        ddl = "ALTER TABLE tb2 ADD CLUSTERED INDEX g4 (name, id) "
            + "PARTITION BY LIST (id) ( PARTITION p1 VALUES IN (1),  PARTITION pd VALUES IN (DEFAULT) ) "
            + "TABLEGROUP= test_tg /* INVISIBLE */ ";
        buildDdlEventSqlForMysqlPart(sb, "tb2", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE tb2 ADD INDEX g4 (name, id)", sb.toString());

        /*
         * test if clustered info with index can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE CLUSTERED INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)";
        buildDdlEventSqlForMysqlPart(sb, "auto_partition_idx_tb", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)", sb.toString());

        /*
         test if expression info with index can be removed
         */
        sb = new StringBuilder();
        ddl = "ALTER TABLE expr_multi_column_tbl\n"
            + "\tADD INDEX expr_multi_column_tbl_idx (a + 1 DESC, b, c - 1, substr(d, -2) ASC, a + b + c * 2)";
        buildDdlEventSqlForMysqlPart(sb, "expr_multi_column_tbl", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE expr_multi_column_tbl ADD INDEX expr_multi_column_tbl_idx (a + 1 DESC, b, c - 1, substr(d, -2) ASC, a + b + c * 2)",
            sb.toString());

        /*
         test if logical info with column can be removed
         */
        sb = new StringBuilder();
        ddl = "ALTER TABLE gen_col_ordinal_test_tblYn\n"
            + "  ADD COLUMN g1 int AS (a+b) LOGICAL AFTER c";
        buildDdlEventSqlForMysqlPart(sb, "gen_col_ordinal_test_tblYn", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE gen_col_ordinal_test_tblYn ADD COLUMN g1 int AFTER c", sb.toString());

        sb = new StringBuilder();
        ddl = "alter table gen_col_with_insert_select_1o "
            + "add column c int not null as (a-b) logical first, "
            + "add column d int not null as (a+b) logical unique first";
        buildDdlEventSqlForMysqlPart(sb, "gen_col_with_insert_select_1o", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE gen_col_with_insert_select_1o ADD COLUMN c int NOT NULL FIRST, ADD COLUMN d int NOT NULL FIRST",
            sb.toString());

        /*
         test if align to info can be removed
         */
        sb = new StringBuilder();
        ddl = " alter table pt_k_2 partition align to t_a_t_s_tg2";
        buildDdlEventSqlForMysqlPart(sb, "pt_k_2", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE pt_k_2", sb.toString());

        /*
         test if OMC info can be removed
         */
        sb = new StringBuilder();
        ddl = "alter table nnn change column b bb bigint ALGORITHM=OMC";
        buildDdlEventSqlForMysqlPart(sb, "nnn", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE nnn CHANGE COLUMN b bb bigint", sb.toString());

        sb = new StringBuilder();
        ddl = "alter table nnn change column b bb bigint ALGORITHM=XXX";
        buildDdlEventSqlForMysqlPart(sb, "nnn", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE nnn CHANGE COLUMN b bb bigint, ALGORITHM = XXX", sb.toString());

        sb = new StringBuilder();
        ddl = "ALTER TABLE column_backfill_ts_tbl\n"
            + "  MODIFY COLUMN c1_1 timestamp(6) DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),\n"
            + "  ALGORITHM = omc";
        buildDdlEventSqlForMysqlPart(sb, "column_backfill_ts_tbl", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE column_backfill_ts_tbl MODIFY COLUMN c1_1 timestamp(6) DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6)",
            sb.toString());

        sb = new StringBuilder();
        ddl = "ALTER TABLE modify_pk_with_upsert_1bv DROP PRIMARY KEY, ADD PRIMARY KEY (b) ALGORITHM = OMC";
        buildDdlEventSqlForMysqlPart(sb, "modify_pk_with_upsert_1bv", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE modify_pk_with_upsert_1bv DROP PRIMARY KEY, ADD PRIMARY KEY (b)", sb.toString());

        sb = new StringBuilder();
        ddl = "alter table omc_change_column_ordinal_test_tbl change column c cc bigint first ALGORITHM=OMC ";
        buildDdlEventSqlForMysqlPart(sb, "omc_change_column_ordinal_test_tbl", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE omc_change_column_ordinal_test_tbl CHANGE COLUMN c cc bigint FIRST",
            sb.toString());

        /*
         test if `AUTO_INCREMENT BY GROUP` info can be removed
         */
        sb = new StringBuilder();
        ddl = "ALTER TABLE alter_table_without_seq_change\n"
            + "\tMODIFY COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT BY GROUP";
        buildDdlEventSqlForMysqlPart(sb, "alter_table_without_seq_change", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE alter_table_without_seq_change MODIFY COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT",
            sb.toString());

        sb = new StringBuilder();
        ddl = "ALTER TABLE alter_table_without_seq_change\n"
            + "\tADD COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT BY GROUP";
        buildDdlEventSqlForMysqlPart(sb, "alter_table_without_seq_change", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE alter_table_without_seq_change ADD COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT",
            sb.toString());

        /*
        test if PURGE info can be removed
         */
        sb = new StringBuilder();
        ddl = "DROP TABLE IF EXISTS test_recycle_broadcast_tb PURGE";
        buildDdlEventSqlForMysqlPart(sb, "test_recycle_broadcast_tb", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("DROP TABLE IF EXISTS test_recycle_broadcast_tb", sb.toString());

        /*
         * test if comment info can be removed
         */
        sb = new StringBuilder();
        ddl = "/* CDC_TOKEN : 0644b5a0-1ce9-43f9-8b62-62d0a0f59d72 */\n"
            + "CREATE TABLE IF NOT EXISTS `t_ddl_test_normal` (\n"
            + "\t`ID` BIGINT(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`JOB_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`EXT_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`TV_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`SCHEMA_NAME` VARCHAR(200) NOT NULL,\n"
            + "\t`TABLE_NAME` VARCHAR(200) NOT NULL,\n"
            + "\t`GMT_CREATED` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "\t`DDL_SQL` TEXT NOT NULL,\n"
            + "\tPRIMARY KEY (`ID`),\n"
            + "\tKEY `idx1` (`SCHEMA_NAME`),\n"
            + "\tINDEX `auto_shard_key_job_id` USING BTREE(`JOB_ID`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "DBPARTITION BY hash(ID)\n"
            + "TBPARTITION BY hash(JOB_ID) TBPARTITIONS 16";
        buildDdlEventSqlForMysqlPart(sb, "t_ddl_test_normal", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE IF NOT EXISTS `t_ddl_test_normal` ( `ID` BIGINT(20) NOT NULL AUTO_INCREMENT, `JOB_ID` BIGINT(20) NOT NULL DEFAULT 0, `EXT_ID` BIGINT(20) NOT NULL DEFAULT 0, `TV_ID` BIGINT(20) NOT NULL DEFAULT 0, `SCHEMA_NAME` VARCHAR(200) NOT NULL, `TABLE_NAME` VARCHAR(200) NOT NULL, `GMT_CREATED` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, `DDL_SQL` TEXT NOT NULL, PRIMARY KEY (`ID`), KEY `idx1` (`SCHEMA_NAME`), INDEX `auto_shard_key_job_id` USING BTREE(`JOB_ID`) ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        /*
         * test if local key can be removed
         */
        sb = new StringBuilder();
        ddl = "CREATE TABLE `update_delete_base_with_index_one_multi_db_multi_tb` (\n"
            + "\t`pk` bigint(11) NOT NULL,\n"
            + "\t`integer_test` int(11) DEFAULT NULL,\n"
            + "\t`varchar_test` varchar(255) DEFAULT NULL,\n"
            + "\t`char_test` char(255) DEFAULT NULL,\n"
            + "\t`blob_test` blob,\n"
            + "\t`tinyint_test` tinyint(4) DEFAULT NULL,\n"
            + "\t`tinyint_1bit_test` tinyint(1) DEFAULT NULL,\n"
            + "\t`smallint_test` smallint(6) DEFAULT NULL,\n"
            + "\t`mediumint_test` mediumint(9) DEFAULT NULL,\n"
            + "\t`bit_test` bit(1) DEFAULT NULL,\n"
            + "\t`bigint_test` bigint(20) DEFAULT NULL,\n"
            + "\t`float_test` float DEFAULT NULL,\n"
            + "\t`double_test` double DEFAULT NULL,\n"
            + "\t`decimal_test` decimal(10, 0) DEFAULT NULL,\n"
            + "\t`date_test` date DEFAULT NULL,\n"
            + "\t`time_test` time DEFAULT NULL,\n"
            + "\t`datetime_test` datetime DEFAULT NULL,\n"
            + "\t`timestamp_test` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,\n"
            + "\t`year_test` year(4) DEFAULT NULL,\n"
            + "\t`mediumtext_test` mediumtext,\n"
            + "\tPRIMARY KEY (`pk`),\n"
            + "\tINDEX `index_date` (`date_test`),\n"
            + "\tINDEX `index_integer` (`integer_test`),\n"
            + "\tINDEX `index_mix_1` (`char_test`, `smallint_test`, `float_test`),\n"
            + "\tINDEX `index_varchar` (`varchar_test`),\n"
            + "\tLOCAL KEY `index_mix_2` (`double_test`, `year_test`)\n"
            + ")";
        buildDdlEventSqlForMysqlPart(sb, "update_delete_base_with_index_one_multi_db_multi_tb", "utf8mb4",
            "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `update_delete_base_with_index_one_multi_db_multi_tb` ( `pk` bigint(11) NOT NULL, `integer_test` int(11) DEFAULT NULL, `varchar_test` varchar(255) DEFAULT NULL, `char_test` char(255) DEFAULT NULL, `blob_test` blob, `tinyint_test` tinyint(4) DEFAULT NULL, `tinyint_1bit_test` tinyint(1) DEFAULT NULL, `smallint_test` smallint(6) DEFAULT NULL, `mediumint_test` mediumint(9) DEFAULT NULL, `bit_test` bit(1) DEFAULT NULL, `bigint_test` bigint(20) DEFAULT NULL, `float_test` float DEFAULT NULL, `double_test` double DEFAULT NULL, `decimal_test` decimal(10, 0) DEFAULT NULL, `date_test` date DEFAULT NULL, `time_test` time DEFAULT NULL, `datetime_test` datetime DEFAULT NULL, `timestamp_test` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP, `year_test` year(4) DEFAULT NULL, `mediumtext_test` mediumtext, PRIMARY KEY (`pk`), INDEX `index_date`(`date_test`), INDEX `index_integer`(`integer_test`), INDEX `index_mix_1`(`char_test`, `smallint_test`, `float_test`), INDEX `index_varchar`(`varchar_test`), KEY `index_mix_2` (`double_test`, `year_test`) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        sb = new StringBuilder();
        ddl = "alter table t_order_gsi3 add local index l_i_order(seller_id)";
        buildDdlEventSqlForMysqlPart(sb, "t_order_gsi3", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE t_order_gsi3 ADD INDEX l_i_order (seller_id)", sb.toString());

        /*
         * test if global index can be converted to normal index
         */
        sb = new StringBuilder();
        ddl = "alter table `t_order_gsi2` add global index `g_i_buyer_for_gsi2`(`buyer_id`)"
            + "  COVERING(`seller_id`, `order_snapshot`)  dbpartition by hash(`buyer_id`)  tbpartition by hash(`buyer_id`) tbpartitions 3;";
        buildDdlEventSqlForMysqlPart(sb, "t_order_gsi2", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE `t_order_gsi2` ADD INDEX `g_i_buyer_for_gsi2` (`buyer_id`);", sb.toString());

        sb = new StringBuilder();
        ddl = "alter table t_order_gsi4 add unique global index `g_i_buyer_for_gsi4`(`buyer_id`) "
            + " COVERING(`seller_id`, `order_snapshot`)  dbpartition by hash(`buyer_id`)  tbpartition by hash(`buyer_id`) tbpartitions 3;";
        buildDdlEventSqlForMysqlPart(sb, "t_order_gsi4", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE t_order_gsi4 ADD UNIQUE INDEX `g_i_buyer_for_gsi4` (`buyer_id`);",
            sb.toString());

        sb = new StringBuilder();
        ddl = "ALTER TABLE `t_idx_order`\n"
            + "\tADD INDEX g_i_idx_seller USING hash (`c2`, c3) COVERING (`c4`)";
        buildDdlEventSqlForMysqlPart(sb, "t_idx_order", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE `t_idx_order` ADD INDEX g_i_idx_seller (`c2`, c3)", sb.toString());

        sb = new StringBuilder();
        ddl = "create index `convweqz5` using hash on `uupy2v` ( `b57` , `nai` desc ) "
            + "partition by key ( `dfhgls` , `nai` )";
        buildDdlEventSqlForMysqlPart(sb, "convweqz5", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE INDEX `convweqz5` ON `uupy2v` (`b57`, `nai` DESC) USING HASH", sb.toString());

        sb = new StringBuilder();
        ddl = "create unique global index `g_i_buyer_for_gsi4` on t_order_gsi4(`buyer_id`)  "
            + "COVERING(`seller_id`, `order_snapshot`)  dbpartition by hash(`buyer_id`)  tbpartition by hash(`buyer_id`) tbpartitions 3;";
        buildDdlEventSqlForMysqlPart(sb, "t_order_gsi4", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE UNIQUE INDEX `g_i_buyer_for_gsi4` ON t_order_gsi4 (`buyer_id`);", sb.toString());

        sb = new StringBuilder();
        ddl = "alter table t_order_gsi5 add clustered index l_i_order(buyer_id) "
            + "dbpartition by hash(`buyer_id`)  tbpartition by hash(`buyer_id`) tbpartitions 3;";
        buildDdlEventSqlForMysqlPart(sb, "t_order_gsi5", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE t_order_gsi5 ADD INDEX l_i_order (buyer_id);", sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE UNIQUE GLOBAL INDEX `g_i_buyer_for_gsi2` ON `t_order_gsi2`(`buyer_id`) \n"
            + "  COVERING(`seller_id`, `order_snapshot`) \n"
            + "   dbpartition by hash(`buyer_id`) tbpartition by hash(`buyer_id`) tbpartitions 3;";
        buildDdlEventSqlForMysqlPart(sb, "g_i_buyer_for_gsi2", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE UNIQUE INDEX `g_i_buyer_for_gsi2` ON `t_order_gsi2` (`buyer_id`);", sb.toString());

        sb = new StringBuilder();
        ddl = "create shadow table __test_truncate_gsi_test_7 ("
            + "id int primary key, name varchar(20), "
            + "global index __test_g_i_truncate_test_7 (name) partition by hash(name))"
            + " partition by hash(id)";
        buildDdlEventSqlForMysqlPart(sb, "__test_truncate_gsi_test_7", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE __test_truncate_gsi_test_7 ( id int PRIMARY KEY, name varchar(20), INDEX __test_g_i_truncate_test_7(name) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs",
            sb.toString());

        System.out.println(sb.toString());

    }

    @Test
    public void testBuildDdlEventSqlForPolarPart() {
        /*
         * test if `TABLEGROUP & FORCE` info can be removed
         */
        StringBuilder sb = new StringBuilder();
        String ddl = "ALTER TABLE `dkx0zjr` SET tablegroup = `Zmn` FORCE";
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "");
        Assert.assertEquals("# POLARX_ORIGIN_SQL=ALTER TABLE `dkx0zjr`\n"
            + "# POLARX_TSO=\n", sb.toString());

        /*
         * test hints
         */
        ddl = "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*//!tddl:enable_recyclebin=true*/"
            + "drop table test_recyclebin_tb";
        String sql = buildDdlEventSql("", ddl, "utf8mb4", "utf8_general_cs", "111111", ddl);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*/ /*tddl:enable_recyclebin=true*/ DROP TABLE test_recyclebin_tb\n"
                + "# POLARX_TSO=111111\n"
                + "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*/ /*tddl:enable_recyclebin=true*/ DROP TABLE test_recyclebin_tb",
            sql);
    }

    @Test
    public void testPrivateDDLSwitch() {
        setConfig(ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String sql1 =
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4";
        String sql2 = buildDdlEventSql("", sql1, null, "", "",
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4");
        Assert.assertTrue(sql2.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(sql2.contains("# POLARX_TSO="));

        setConfig(ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "false");
        String sql3 = "alter table nnn change column b bb bigint ALGORITHM=XXX";
        String sql4 = buildDdlEventSql(sql3, null, null, "");
        Assert.assertFalse(sql4.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertFalse(sql4.contains("# POLARX_TSO="));
    }

    @Test
    public void testLineWrap() {
        String sql = "CREATE TABLE `cloud_hanging_user_pack` (\n"
            + "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '人群包配置id',\n"
            + "  `name` varchar(40) NOT NULL DEFAULT '' COMMENT '人群包名称',\n"
            + "  `pack_comment` varchar(40) NOT NULL DEFAULT '' COMMENT '备注\\n',\n"
            + "  `original_count` bigint(11) NOT NULL DEFAULT '0' COMMENT '原始人数',\n"
            + "  `effective_count` bigint(11) NOT NULL DEFAULT '0' COMMENT '有效人数',\n"
            + "  `orginal_url` varchar(100) NOT NULL DEFAULT '' COMMENT '原始人群包下载地址',\n"
            + "  `effective_url` varchar(100) NOT NULL DEFAULT '' COMMENT '有效人群包',\n"
            + "  PRIMARY KEY (`id`) USING BTREE\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='云挂机人群包配置';";

        String convertSql = buildDdlEventSql(sql, "utf8", "utf8", "123456");
        convertSql = StringUtils.substringAfter(convertSql, "# POLARX_ORIGIN_SQL=");
        convertSql = StringUtils.substringBefore(convertSql, ";");
        Assert.assertFalse(StringUtils.contains(convertSql, "\n"));
    }

    @Test
    public void testProcessDdlSqlCharacters() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);

        // keep the value in sql
        String sql = "CREATE TABLE lbkkfddjvc (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_ci \n"
            + " PARTITION BY KEY (id, k)PARTITIONS 1 \n"
            + " AUTO_SPLIT 'ON'";
        sql = DDLConverter.processDdlSqlCharacters(sql, "utf8mb4", "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("test_db", "lbkkfddjvc");
        Assert.assertEquals("utf8", tableMeta.getCharset());

        // attach the value in sql
        sql = "CREATE TABLE xxvvzz (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " PARTITION BY KEY (id, k)PARTITIONS 1 \n"
            + " AUTO_SPLIT 'ON'";
        sql = DDLConverter.processDdlSqlCharacters(sql, "utf8mb4", "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        tableMeta = memoryTableMeta.find("test_db", "xxvvzz");
        Assert.assertEquals("utf8mb4", tableMeta.getCharset());

        sql = "CREATE TABLE xxvvzz (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " PARTITION BY KEY (id, k)PARTITIONS 1 \n"
            + " AUTO_SPLIT 'ON'";
        sql = DDLConverter.processDdlSqlCharacters(sql, null, "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        tableMeta = memoryTableMeta.find("test_db", "xxvvzz");
        Assert.assertEquals("utf8mb4", tableMeta.getCharset());
    }
}
