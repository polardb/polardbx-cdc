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
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_HINT_BLACKLIST;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSql;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForMysqlPart;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForPolarPart;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.tryRemoveAutoShardKey;
import static com.aliyun.polardbx.binlog.util.CommonUtils.extractPolarxOriginSql;

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
            + "# POLARX_DDL_ID=0\n"
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

        sb = new StringBuilder();
        ddl =
            "CREATE TABLE my_modify_ttl_t1 ( a int NOT NULL AUTO_INCREMENT, b datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (a) ) TTL = TTL_DEFINITION( TTL_ENABLE = 'OFF', TTL_EXPR = `b` EXPIRE AFTER 2 MONTH TIMEZONE '+08:00', TTL_JOB = CRON '*/1 * * * * ?' TIMEZONE '+08:00', ARCHIVE_TYPE = '', ARCHIVE_TABLE_SCHEMA = '', ARCHIVE_TABLE_NAME = '', ARCHIVE_TABLE_PRE_ALLOCATE = 3, ARCHIVE_TABLE_POST_ALLOCATE = 4 ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci PARTITION BY KEY (a) PARTITIONS 2 WITH TABLEGROUP = tg5055 IMPLICIT";
        buildDdlEventSqlForMysqlPart(sb, "my_modify_ttl_t1", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE my_modify_ttl_t1 ( a int NOT NULL AUTO_INCREMENT, b datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (a) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());

        sb = new StringBuilder();
        ddl =
            "alter table test_tbl modify ttl  set  TTL_ENABLE = 'OFF', TTL_EXPR = `b` EXPIRE AFTER 2 MONTH TIMEZONE '+08:00', TTL_JOB = CRON '*/1 * * * * ?' TIMEZONE '+08:00', ARCHIVE_TYPE = '', ARCHIVE_TABLE_SCHEMA = '', ARCHIVE_TABLE_NAME = '', ARCHIVE_TABLE_PRE_ALLOCATE = 3, ARCHIVE_TABLE_POST_ALLOCATE = 4";
        buildDdlEventSqlForMysqlPart(sb, "test_tbl", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "ALTER TABLE test_tbl",
            sb.toString());
    }

    @Test
    public void testAddKeyForAutoIncrement() {
        StringBuilder sb = new StringBuilder();
        String ddl = "CREATE TABLE `wy6uo8g` (\n"
            + "  `y` INT(3) PRIMARY KEY AUTO_INCREMENT,\n"
            + "  `ZW2JPD` DATETIME(0) NOT NULL UNIQUE\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY HASH(`y`)\n"
            + "TBPARTITION BY MM(`ZW2JPD`) TBPARTITIONS 3";
        buildDdlEventSqlForMysqlPart(sb, "wy6uo8g", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("CREATE TABLE `wy6uo8g` "
            + "( `y` INT(3) PRIMARY KEY AUTO_INCREMENT, "
            + "`ZW2JPD` DATETIME(0) NOT NULL UNIQUE )"
            + " DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE TABLE `wy6uo8g` (\n"
            + "  `y` INT(3) AUTO_INCREMENT UNIQUE,\n"
            + "  `ZW2JPD` DATETIME(0) NOT NULL UNIQUE\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY HASH(`y`)\n"
            + "TBPARTITION BY MM(`ZW2JPD`) TBPARTITIONS 3";
        buildDdlEventSqlForMysqlPart(sb, "wy6uo8g", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `wy6uo8g` ("
                + " `y` INT(3) UNIQUE AUTO_INCREMENT, "
                + "`ZW2JPD` DATETIME(0) NOT NULL UNIQUE ) "
                + "DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE TABLE `wy6uo8g` (\n"
            + "  `y` INT(3) AUTO_INCREMENT,\n"
            + "  `ZW2JPD` DATETIME(0) NOT NULL UNIQUE\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY HASH(`y`)\n"
            + "TBPARTITION BY MM(`ZW2JPD`) TBPARTITIONS 3";
        buildDdlEventSqlForMysqlPart(sb, "wy6uo8g", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `wy6uo8g` ("
                + " `y` INT(3) AUTO_INCREMENT, "
                + "`ZW2JPD` DATETIME(0) NOT NULL UNIQUE, "
                + "KEY (`y`) ) DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE TABLE `wy6uo8g` (\n"
            + "  `y` INT(3) AUTO_INCREMENT,\n"
            + "  `ZW2JPD` DATETIME(0) NOT NULL UNIQUE,\n"
            + "  key k1(`y`,`ZW2JPD`)"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY HASH(`y`)\n"
            + "TBPARTITION BY MM(`ZW2JPD`) TBPARTITIONS 3";
        buildDdlEventSqlForMysqlPart(sb, "wy6uo8g", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `wy6uo8g` ("
                + " `y` INT(3) AUTO_INCREMENT,"
                + " `ZW2JPD` DATETIME(0) NOT NULL UNIQUE,"
                + " KEY k1 (`y`, `ZW2JPD`) ) DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());

        sb = new StringBuilder();
        ddl = "CREATE TABLE `wy6uo8g` (\n"
            + "  `y` INT(3) AUTO_INCREMENT,\n"
            + "  `ZW2JPD` DATETIME(0) NOT NULL UNIQUE,\n"
            + "  key k1(`ZW2JPD`,`y`)"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY HASH(`y`)\n"
            + "TBPARTITION BY MM(`ZW2JPD`) TBPARTITIONS 3";
        buildDdlEventSqlForMysqlPart(sb, "wy6uo8g", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(
            "CREATE TABLE `wy6uo8g` ("
                + " `y` INT(3) AUTO_INCREMENT,"
                + " `ZW2JPD` DATETIME(0) NOT NULL UNIQUE,"
                + " KEY k1 (`ZW2JPD`, `y`),"
                + " KEY (`y`) ) DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());
    }

    @Test
    public void testBuildDdlEventSqlForPolarPart() {
        /*
         * test if `TABLEGROUP & FORCE` info can be removed
         */
        StringBuilder sb = new StringBuilder();
        String ddl = "ALTER TABLE `dkx0zjr` SET tablegroup = `Zmn` FORCE";
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "");
        Assert.assertEquals("# POLARX_ORIGIN_SQL=ALTER TABLE `dkx0zjr` SET tablegroup = `Zmn` FORCE\n"
            + "# POLARX_TSO=\n# POLARX_DDL_ID=0\n", sb.toString());

        /*
         * test hints
         */
        ddl = "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*//!tddl:enable_recyclebin=true*//*DDL_ID=1234*/"
            + "drop table test_recyclebin_tb";
        String sql = buildDdlEventSql("", ddl, "utf8mb4", "utf8_general_cs", "111111", ddl);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*/ /*tddl:enable_recyclebin=true*/ DROP TABLE test_recyclebin_tb\n"
                + "# POLARX_TSO=111111\n"
                + "# POLARX_DDL_ID=1234\n"
                + "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*/\n/*tddl:enable_recyclebin=true*/\nDROP TABLE test_recyclebin_tb",
            sql);
    }

    @Test
    public void testPrivateDDLSwitch() {
        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String sql1 =
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4";
        String sql2 = buildDdlEventSql("", sql1, null, "", "",
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4");
        Assert.assertTrue(sql2.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(sql2.contains("# POLARX_TSO="));

        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "false");
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
        convertSql = extractPolarxOriginSql(convertSql, false);
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
            + " PARTITION BY KEY (id, k)PARTITIONS 1";
        sql = DDLConverter.processDdlSqlCharacters(sql, "utf8mb4", "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("test_db", "lbkkfddjvc");
        Assert.assertEquals("utf8", tableMeta.getCharset());

        // attach the value in sql
        sql = "CREATE TABLE xxvvzz (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " PARTITION BY KEY (id, k)PARTITIONS 1";
        sql = DDLConverter.processDdlSqlCharacters(sql, "utf8mb4", "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        tableMeta = memoryTableMeta.find("test_db", "xxvvzz");
        Assert.assertEquals("utf8mb4", tableMeta.getCharset());

        sql = "CREATE TABLE xxvvzz (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " PARTITION BY KEY (id, k)PARTITIONS 1";
        sql = DDLConverter.processDdlSqlCharacters(sql, null, "utf8mb4_general_ci");
        memoryTableMeta.apply(null, "test_db", sql, null);
        tableMeta = memoryTableMeta.find("test_db", "xxvvzz");
        Assert.assertEquals("utf8mb4", tableMeta.getCharset());
    }

    @Test
    public void testHintsFilter() {
        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        setConfig(TASK_REFORMAT_DDL_HINT_BLACKLIST, "GSI_BACKFILL_POSITION_MARK,GSI_BACKFILL_BATCH_SIZE,ALLOW_ADD_GSI");
        String sql =
            "/*+TDDL:CMD_EXTRA(GSI_BACKFILL_BATCH_SIZE=2, gsi_backfill_position_mark = \"[{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100001\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_0\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100002\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_1\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_2\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_3\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_4\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100000\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_5\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"}]\", ALLOW_ADD_GSI=TRUE)*/ "
                + "CREATE GLOBAL INDEX g_resume_id ON gsi_backfill_resume_primary (id) COVERING (c_bit_1, c_bit_8, c_bit_16, c_bit_32, c_bit_64, c_tinyint_1, c_tinyint_1_un, c_tinyint_4, c_tinyint_4_un, c_tinyint_8, c_tinyint_8_un, c_smallint_1, c_smallint_16, c_smallint_16_un, c_mediumint_1, c_mediumint_24, c_mediumint_24_un, c_int_1, c_int_32, c_int_32_un, c_bigint_1, c_bigint_64, c_bigint_64_un, c_decimal, c_decimal_pr, c_float, c_float_pr, c_float_un, c_double, c_double_pr, c_double_un, c_date, c_datetime, c_datetime_1, c_datetime_3, c_datetime_6, c_timestamp_1, c_timestamp_3, c_timestamp_6, c_time, c_time_1, c_time_3, c_time_6, c_year, c_year_4, c_char, c_varchar, c_binary, c_varbinary, c_blob_tiny, c_blob, c_blob_medium, c_blob_long, c_text_tiny, c_text, c_text_medium, c_text_long, c_enum, c_set, c_json, c_geometory, c_point, c_linestring, c_polygon, c_multipoint, c_multilinestring, c_multipolygon) DBPARTITION BY HASH(id) TBPARTITION BY HASH(id) TBPARTITIONS 7";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        String expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:CMD_EXTRA(  )*/ CREATE GLOBAL INDEX g_resume_id ON gsi_backfill_resume_primary (id) COVERING (c_bit_1, c_bit_8, c_bit_16, c_bit_32, c_bit_64, c_tinyint_1, c_tinyint_1_un, c_tinyint_4, c_tinyint_4_un, c_tinyint_8, c_tinyint_8_un, c_smallint_1, c_smallint_16, c_smallint_16_un, c_mediumint_1, c_mediumint_24, c_mediumint_24_un, c_int_1, c_int_32, c_int_32_un, c_bigint_1, c_bigint_64, c_bigint_64_un, c_decimal, c_decimal_pr, c_float, c_float_pr, c_float_un, c_double, c_double_pr, c_double_un, c_date, c_datetime, c_datetime_1, c_datetime_3, c_datetime_6, c_timestamp_1, c_timestamp_3, c_timestamp_6, c_time, c_time_1, c_time_3, c_time_6, c_year, c_year_4, c_char, c_varchar, c_binary, c_varbinary, c_blob_tiny, c_blob, c_blob_medium, c_blob_long, c_text_tiny, c_text, c_text_medium, c_text_long, c_enum, c_set, c_json, c_geometory, c_point, c_linestring, c_polygon, c_multipoint, c_multilinestring, c_multipolygon) DBPARTITION BY HASH(id) TBPARTITION BY HASH(id) TBPARTITIONS 7\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        setConfig(TASK_REFORMAT_DDL_HINT_BLACKLIST,
            "GSI_BACKFILL_POSITION_MARK,FP_PAUSE_AFTER_DDL_TASK_EXECUTION,FP_STATISTIC_SAMPLE_ERROR");
        sql = "/*+TDDL:cmd_extra(FP_PAUSE_AFTER_DDL_TASK_EXECUTION='AlterTablePhyDdlTask')*/ "
            + "ALTER TABLE wumu_test DROP COLUMN b";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:cmd_extra()*/ ALTER TABLE wumu_test DROP COLUMN b\n"
                + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "/*+TDDL:cmd_extra(FP_STATISTIC_SAMPLE_ERROR=true)*/ "
            + "ALTER TABLE t1 ADD GLOBAL INDEX gsi1 (a) PARTITION BY KEY (a) PARTITIONS 5 WITH TABLEGROUP= tg4723 IMPLICIT";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:cmd_extra()*/ ALTER TABLE t1 ADD GLOBAL INDEX gsi1 (a) PARTITION BY KEY (a) PARTITIONS 5 WITH TABLEGROUP= tg4723 IMPLICIT\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testRemoveLocalityForCreateTableGroup() {
        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String sql = "CREATE TABLEGROUP tg1 "
            + "LOCALITY = 'dn=xgdn-ddl-230916222943-5eb4-xv8f-dn-0, xgdn-ddl-230916222943-5eb4-xv8f-dn-1'";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        String expectSql = "# POLARX_ORIGIN_SQL=CREATE TABLEGROUP tg1\n" + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "CREATE TABLEGROUP sellerid_tg "
            + "PARTITION BY LIST COLUMNS ( BIGINT) SUBPARTITION BY KEY ( BIGINT,  BIGINT) "
            + "( PARTITION p1 VALUES IN (1, 2) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-0' SUBPARTITIONS 1,  "
            + "  PARTITION p2 VALUES IN (3, 4) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-1' SUBPARTITIONS 2,  "
            + "  PARTITION p3 VALUES IN (5, 6) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-0' SUBPARTITIONS 4,  "
            + "  PARTITION p_default VALUES IN (DEFAULT) LOCALITY'dn=ziyang-116-do-not-delete-kwmg-dn-1' SUBPARTITIONS 4 )";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql = "# POLARX_ORIGIN_SQL=CREATE TABLEGROUP sellerid_tg PARTITION BY LIST COLUMNS ( BIGINT) "
            + "SUBPARTITION BY KEY ( BIGINT,  BIGINT) ( "
            + "PARTITION p1 VALUES IN (1, 2) SUBPARTITIONS 1,  "
            + "PARTITION p2 VALUES IN (3, 4) SUBPARTITIONS 2,  "
            + "PARTITION p3 VALUES IN (5, 6) SUBPARTITIONS 4,  "
            + "PARTITION p_default VALUES IN (DEFAULT) SUBPARTITIONS 4 )\n" + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testRemoveLocalityForGlobalIndex() {
        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        // create index
        String sql = "CREATE UNIQUE GLOBAL INDEX `W9H4uo` ON `8f6` (`Du3z` DESC)"
            + "PARTITION BY LIST (`Du3z`) ( "
            + "     PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ',  "
            + "     PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1 , ziyang-107-do-not-delete-l4rm-dn-1, ziyang-107-do-not-delete-l4rm-dn-0 ',  "
            + "     PARTITION `BmEnjPq` VALUES IN (68, 118) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ', "
            + "     PARTITION `t61YgnWpjT` VALUES IN (47) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1  ' ) "
            + "USING HASH";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        String expectSql =
            "# POLARX_ORIGIN_SQL=CREATE UNIQUE GLOBAL INDEX `W9H4uo` ON `8f6` (`Du3z` DESC) PARTITION BY LIST (`Du3z`) ( PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11),  PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72),  PARTITION `BmEnjPq` VALUES IN (68, 118),  PARTITION `t61YgnWpjT` VALUES IN (47) ) USING HASH\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        // alter table add index
        sql = "alter table t1 add UNIQUE GLOBAL INDEX `W9H4uo` (`Du3z` DESC)"
            + "PARTITION BY LIST (`Du3z`) ( "
            + "     PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ',  "
            + "     PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1 , ziyang-107-do-not-delete-l4rm-dn-1, ziyang-107-do-not-delete-l4rm-dn-0 ',  "
            + "     PARTITION `BmEnjPq` VALUES IN (68, 118) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ', "
            + "     PARTITION `t61YgnWpjT` VALUES IN (47) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1  ' ) "
            + "USING HASH";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql =
            "# POLARX_ORIGIN_SQL=ALTER TABLE t1 ADD UNIQUE GLOBAL INDEX `W9H4uo` USING HASH (`Du3z` DESC) PARTITION BY LIST (`Du3z`) ( PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11),  PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72),  PARTITION `BmEnjPq` VALUES IN (68, 118),  PARTITION `t61YgnWpjT` VALUES IN (47) )\n"
                + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        // create table with gsi
        sql = "create table t1 ("
            + "id bigint primary key , "
            + "Du3z bigint not null, "
            + "global index `W9H4uo` (`Du3z` DESC)"
            + "PARTITION BY LIST (`Du3z`) ( "
            + "     PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ',  "
            + "     PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1 , ziyang-107-do-not-delete-l4rm-dn-1, ziyang-107-do-not-delete-l4rm-dn-0 ',  "
            + "     PARTITION `BmEnjPq` VALUES IN (68, 118) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ', "
            + "     PARTITION `t61YgnWpjT` VALUES IN (47) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1  ' ) "
            + "USING HASH" + ")";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql =
            "# POLARX_ORIGIN_SQL=CREATE TABLE t1 ( id bigint PRIMARY KEY, Du3z bigint NOT NULL, GLOBAL INDEX `W9H4uo` USING HASH(`Du3z` DESC) PARTITION BY LIST (`Du3z`) ( PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11),  PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72),  PARTITION `BmEnjPq` VALUES IN (68, 118),  PARTITION `t61YgnWpjT` VALUES IN (47) ) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs\n"
                + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testRemoveLocalityForPartitionBy() {
        setConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        String sql = "ALTER TABLE t1 PARTITION BY HASH (a) "
            + "PARTITIONS 16 LOCALITY = 'DN=ZIYANG-128-DO-NOT-DELETE-JCCK-DN-1' WITH TABLEGROUP=tg3588 IMPLICIT";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        String expectSql = "# POLARX_ORIGIN_SQL=ALTER TABLE t1 PARTITION BY HASH (a) PARTITIONS 16 "
            + "WITH TABLEGROUP=tg3588 IMPLICIT\n" + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "ALTER TABLE t1 SINGLE "
            + "LOCALITY = 'DN=ZIYANG-129-DO-NOT-DELETE-RLP2-DN-1' WITH TABLEGROUP=single_tg4465 IMPLICIT";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        expectSql = "# POLARX_ORIGIN_SQL=ALTER TABLE t1 SINGLE WITH TABLEGROUP=single_tg4465 IMPLICIT\n"
            + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testNewlineEscape() {
        String sql = "create table t1 \n "
            + "(id bigint comment 'ssdd\ndddd' \n,"
            + "name varchar(100) comment 'uiui\nwerw' \n,"
            + " primary key(id) \n"
            + ")";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        Assert.assertEquals("# POLARX_ORIGIN_SQL_ENCODE=BASE64\n"
            + "# POLARX_ORIGIN_SQL=Q1JFQVRFIFRBQkxFIHQxICggaWQgYmlnaW50IENPTU1FTlQgJ3NzZGQKZGRkZCcsIG5hbWUgdmFyY2hhcigxMDApIENPTU1FTlQgJ3VpdWkKd2VydycsIFBSSU1BUlkgS0VZIChpZCkgKSBERUZBVUxUIENIQVJBQ1RFUiBTRVQgPSB1dGY4IERFRkFVTFQgQ09MTEFURSA9IHV0ZjhfZ2VuZXJhbF9jcw==\n"
            + "# POLARX_TSO=\n"
            + "# POLARX_DDL_ID=0\n", sb.toString());

        String decodeSql = extractPolarxOriginSql(sb.toString());
        Assert.assertEquals("CREATE TABLE t1 ( id bigint COMMENT 'ssdd\n"
            + "dddd', name varchar(100) COMMENT 'uiui\n"
            + "werw', PRIMARY KEY (id) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs", decodeSql);
    }

    @Test
    public void testModifyWithTableGroup() {
        String ddl = "ALTER TABLE t_modify MODIFY COLUMN b mediumint WITH TABLEGROUP=tg1216 IMPLICIT, "
            + "INDEX gsi_2 WITH TABLEGROUP=tg1221 IMPLICIT, INDEX gsi_1 WITH TABLEGROUP=tg1219 IMPLICIT";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t_modify", "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals("ALTER TABLE t_modify MODIFY COLUMN b mediumint", sb.toString());
    }

    @Test
    public void testRemoveIndexVisible() {
        String sql = "CREATE TABLE t_order ( "
            + "`id` bigint(11), "
            + "`order_id` varchar(20),"
            + "`buyer_id` varchar(20), "
            + "INDEX `g_order_id`(order_id) INVISIBLE  ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "wp_users_user_email", "utf8mb4", "utf8_general_cs", sql);
        Assert.assertEquals(
            "CREATE TABLE `wp_users_user_email` ( "
                + "`id` bigint(11), "
                + "`order_id` varchar(20), "
                + "`buyer_id` varchar(20), "
                + "INDEX `g_order_id`(order_id) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());
    }

    @Test
    public void testAutoIncrementUnitCount() {
        String sql = "CREATE TABLE group_seq_unit_partition ( id int PRIMARY KEY AUTO_INCREMENT UNIT COUNT 4 INDEX 3 ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t_modify", "utf8mb4", "utf8_general_cs", sql);
        Assert.assertEquals("CREATE TABLE `t_modify` ("
                + " id int PRIMARY KEY AUTO_INCREMENT ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());
    }

    @Test
    public void testColumnarIndex() {
        String sql = "CREATE TABLE `check_cci_meta_test_prim_auto_1` ( "
            + "`pk` int(11) NOT NULL AUTO_INCREMENT, "
            + "`c1` int(11) DEFAULT NULL, "
            + "`c2` int(11) DEFAULT NULL, "
            + "`c3` int(11) DEFAULT NULL, "
            + "PRIMARY KEY (`pk`), "
            + "CLUSTERED COLUMNAR INDEX `check_cci_meta_test_cci_auto_1`(`c2`) WITH TABLEGROUP=columnar_tg1612 IMPLICIT ) "
            + "ENGINE = 'INNODB' DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci WITH TABLEGROUP = tg1611 IMPLICIT ";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t_modify", "utf8mb4", "utf8_general_cs", sql);
        Assert.assertEquals(
            "CREATE TABLE `t_modify` ( "
                + "`pk` int(11) NOT NULL AUTO_INCREMENT, "
                + "`c1` int(11) DEFAULT NULL, "
                + "`c2` int(11) DEFAULT NULL, "
                + "`c3` int(11) DEFAULT NULL, "
                + "PRIMARY KEY (`pk`), "
                + "INDEX `check_cci_meta_test_cci_auto_1`(`c2`) ) "
                + "ENGINE = 'INNODB' DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());
    }

    @Test
    public void testDictionaryColumn() {
        String sql = "CREATE TABLE `region` ("
            + "`r_regionkey` int(11) NOT NULL, "
            + "`r_name` varchar(25) NOT NULL, "
            + "`r_comment` varchar(152) DEFAULT NULL, "
            + "PRIMARY KEY (`r_regionkey`), "
            + "INDEX `region_col_index`(`r_regionkey`) DICTIONARY_COLUMNS = 'r_name' ) "
            + "ENGINE = 'INNODB' DEFAULT CHARSET = latin1 DEFAULT COLLATE = latin1_swedish_ci";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t_modify", "utf8mb4", "utf8_general_cs", sql);
        Assert.assertEquals("CREATE TABLE `t_modify` ( "
                + "`r_regionkey` int(11) NOT NULL, "
                + "`r_name` varchar(25) NOT NULL, "
                + "`r_comment` varchar(152) DEFAULT NULL, "
                + "PRIMARY KEY (`r_regionkey`), "
                + "INDEX `region_col_index`(`r_regionkey`) ) ENGINE = 'INNODB' DEFAULT CHARSET = latin1 DEFAULT COLLATE = latin1_swedish_ci",
            sb.toString());
    }

    @Test
    public void testAddAutoShardKey() {
        String sql1 = "create table t2(id bigint primary key,name varchar(100))partition by key(name) partitions 4;";
        String sql2 = "CREATE TABLE t2 (\n"
            + "  id bigint PRIMARY KEY,\n"
            + "  name varchar(100),\n"
            + "  INDEX `auto_shard_key_name` USING BTREE(`NAME`(100))\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`\n"
            + "PARTITION BY KEY (name) PARTITIONS 4";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t_modify", "utf8mb4", "utf8_general_cs", sql1, sql2);
        Assert.assertEquals(
            "CREATE TABLE `t_modify` ( "
                + "id bigint PRIMARY KEY, "
                + "name varchar(100), "
                + "INDEX `auto_shard_key_name` USING BTREE(`NAME`(100)) ) "
                + "DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs;",
            sb.toString());

        String sql3 =
            "CREATE TABLE `__test_gsi_dml_no_unique_one_index_base` ( "
                + "`pk` bigint(12) NOT NULL, "
                + "`integer_test` int(11) DEFAULT NULL, "
                + "`varchar_test` varchar(255) DEFAULT NULL, "
                + "`char_test` char(255) DEFAULT NULL, "
                + "`blob_test` blob, "
                + "`tinyint_test` tinyint(4) DEFAULT NULL, "
                + "`tinyint_1bit_test` tinyint(1) DEFAULT NULL, "
                + "`smallint_test` smallint(6) DEFAULT NULL, "
                + "`mediumint_test` mediumint(9) DEFAULT NULL, "
                + "`bit_test` bit(1) DEFAULT NULL, "
                + "`bigint_test` bigint(20) UNSIGNED DEFAULT NULL, "
                + "`float_test` float DEFAULT NULL, "
                + "`double_test` double DEFAULT NULL, "
                + "`decimal_test` decimal(10, 0) DEFAULT NULL, "
                + "`date_test` date DEFAULT NULL, "
                + "`time_test` time DEFAULT NULL, "
                + "`datetime_test` datetime DEFAULT NULL, "
                + "`timestamp_test` timestamp NULL DEFAULT NULL, "
                + "`year_test` year(4) DEFAULT NULL, "
                + "`mediumtext_test` mediumtext, "
                + "PRIMARY KEY (`pk`), KEY `auto_shard_key_integer_test` USING BTREE (`integer_test`), "
                + "GLOBAL INDEX `__test_gsi_dml_no_unique_one_index_index1`(`bigint_test`) COVERING (`pk`, `integer_test`, `varchar_test`, `char_test`, `blob_test`, `tinyint_test`, `tinyint_1bit_test`, `smallint_test`, `mediumint_test`, `bit_test`, `float_test`, `double_test`, `decimal_test`, `date_test`, `time_test`, `datetime_test`, `timestamp_test`, `year_test`, `mediumtext_test`) "
                + "DBPARTITION BY HASH(`bigint_test`) TBPARTITION BY HASH(`bigint_test`) TBPARTITIONS 4 ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci DBPARTITION BY hash(`integer_test`) TBPARTITION BY hash(`integer_test`) TBPARTITIONS 4;";
        String sql4 = "CREATE TABLE `__test_gsi_dml_no_unique_one_index_base` (\n"
            + "  `pk` bigint(12) NOT NULL,\n"
            + "  `integer_test` int(11) DEFAULT NULL,\n"
            + "  `varchar_test` varchar(255) DEFAULT NULL,\n"
            + "  `char_test` char(255) DEFAULT NULL,\n"
            + "  `blob_test` blob,\n"
            + "  `tinyint_test` tinyint(4) DEFAULT NULL,\n"
            + "  `tinyint_1bit_test` tinyint(1) DEFAULT NULL,\n"
            + "  `smallint_test` smallint(6) DEFAULT NULL,\n"
            + "  `mediumint_test` mediumint(9) DEFAULT NULL,\n"
            + "  `bit_test` bit(1) DEFAULT NULL,\n"
            + "  `bigint_test` bigint(20) UNSIGNED DEFAULT NULL,\n"
            + "  `float_test` float DEFAULT NULL,\n"
            + "  `double_test` double DEFAULT NULL,\n"
            + "  `decimal_test` decimal(10, 0) DEFAULT NULL,\n"
            + "  `date_test` date DEFAULT NULL,\n"
            + "  `time_test` time DEFAULT NULL,\n"
            + "  `datetime_test` datetime DEFAULT NULL,\n"
            + "  `timestamp_test` timestamp NULL DEFAULT NULL,\n"
            + "  `year_test` year(4) DEFAULT NULL,\n"
            + "  `mediumtext_test` mediumtext,\n"
            + "  PRIMARY KEY (`pk`),\n"
            + "  KEY `auto_shard_key_integer_test` USING BTREE (`integer_test`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "DBPARTITION BY hash(`integer_test`)\n"
            + "TBPARTITION BY hash(`integer_test`) TBPARTITIONS 4 COLLATE `utf8mb4_general_ci`";
        StringBuilder sb2 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb2, "t_modify", "utf8mb4", "utf8_general_cs", sql3, sql4);
        Assert.assertEquals(
            "CREATE TABLE `t_modify` ( "
                + "`pk` bigint(12) NOT NULL, "
                + "`integer_test` int(11) DEFAULT NULL, "
                + "`varchar_test` varchar(255) DEFAULT NULL, "
                + "`char_test` char(255) DEFAULT NULL, "
                + "`blob_test` blob, "
                + "`tinyint_test` tinyint(4) DEFAULT NULL, "
                + "`tinyint_1bit_test` tinyint(1) DEFAULT NULL, "
                + "`smallint_test` smallint(6) DEFAULT NULL, "
                + "`mediumint_test` mediumint(9) DEFAULT NULL, "
                + "`bit_test` bit(1) DEFAULT NULL, "
                + "`bigint_test` bigint(20) UNSIGNED DEFAULT NULL, "
                + "`float_test` float DEFAULT NULL, "
                + "`double_test` double DEFAULT NULL, "
                + "`decimal_test` decimal(10, 0) DEFAULT NULL, "
                + "`date_test` date DEFAULT NULL, "
                + "`time_test` time DEFAULT NULL, "
                + "`datetime_test` datetime DEFAULT NULL, "
                + "`timestamp_test` timestamp NULL DEFAULT NULL, "
                + "`year_test` year(4) DEFAULT NULL, "
                + "`mediumtext_test` mediumtext, "
                + "PRIMARY KEY (`pk`), "
                + "KEY `auto_shard_key_integer_test` USING BTREE (`integer_test`), "
                + "INDEX `__test_gsi_dml_no_unique_one_index_index1`(`bigint_test`) ) "
                + "ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci;",
            sb2.toString());

        String sql5 = "CREATE TABLE t_normal_new_tmp_test_1713070247515 LIKE t_normal_new";
        String sql6 = "CREATE TABLE `t_normal_new_tmp_test_1713070247515` (\n"
            + "  `ID` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "  `JOB_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `EXT_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `TV_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `SCHEMA_NAME` varchar(200) NOT NULL,\n"
            + "  `TABLE_NAME` varchar(200) NOT NULL,\n"
            + "  `GMT_CREATED` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `DDL_SQL` text NOT NULL,\n"
            + "  PRIMARY KEY (`ID`),\n"
            + "  UNIQUE KEY `idx_job` (`JOB_ID`),\n"
            + "  KEY `idx1` (`SCHEMA_NAME`),\n"
            + "  KEY `auto_shard_key_job_id` USING BTREE (`JOB_ID`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 AUTO_INCREMENT = 1900011\n"
            + "DBPARTITION BY hash(`ID`)\n"
            + "TBPARTITION BY hash(`ID`) TBPARTITIONS 8 COLLATE `utf8mb4_general_ci`";
        StringBuilder sb3 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb3, "t_normal_new_tmp_test_1713070247515", "utf8mb4",
            "utf8_general_cs", sql5, sql6);
        Assert.assertEquals("CREATE TABLE t_normal_new_tmp_test_1713070247515 LIKE t_normal_new", sb3.toString());
    }

    @Test
    public void testAutoIncrementSep() {
        String sql = "create table if not exists shardingDestWithGroup5_fn4n ("
            + "c1 int auto_increment unit count 1 index 0 step 100, "
            + "c2 int, primary key (c1)) dbpartition by hash(c1)";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "shardingdestwithgroup5_fn4n", "utf8mb4",
            "utf8mb4_general_ci", sql);
        Assert.assertEquals(
            "CREATE TABLE IF NOT EXISTS `shardingdestwithgroup5_fn4n` ( "
                + "c1 int AUTO_INCREMENT, "
                + "c2 int, PRIMARY KEY (c1) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci",
            sb.toString());
    }

    @Test
    public void testTryRemoveAutoShardKey() {
        String dropIndexSql1 = "drop index auto_shard_key_xx on t1";
        String dropIndexSql2 = "alter table t1 drop index auto_shard_key_xx";
        String dropIndexSql3 = "drop index idx on t1";
        String dropIndexSql4 = "alter table t1 drop index idx";
        String str1 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql1, i -> false);
        String str2 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql2, i -> false);
        String str3 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql3, i -> false);
        String str4 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql4, i -> false);
        String str5 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql1, i -> true);
        String str6 = tryRemoveAutoShardKey("d1", "t1", dropIndexSql2, i -> true);
        Assert.assertNull(str1);
        Assert.assertEquals("ALTER TABLE t1", str2);
        Assert.assertEquals(dropIndexSql3, str3);
        Assert.assertEquals(dropIndexSql4, str4);
        Assert.assertEquals(dropIndexSql1, str5);
        Assert.assertEquals(dropIndexSql2, str6);
    }

    @Test
    public void testAlterTableGroupWithLocality() {
        String sql = "ALTER TABLEGROUP tg2241 SPLIT PARTITION pd INTO ("
            + "PARTITION p3 VALUES IN (1003) LOCALITY 'dn=xdevelop-240518031954-c338-dsj4-dn-0' SUBPARTITIONS 2, "
            + "PARTITION `pd` VALUES IN (DEFAULT) "
            + "( SUBPARTITION `pdsp1`, SUBPARTITION `pdsp2`, SUBPARTITION `pdsp3`, SUBPARTITION `pdsp4` ))";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "");
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=ALTER TABLEGROUP tg2241 SPLIT PARTITION pd INTO (PARTITION p3 VALUES IN (1003) SUBPARTITIONS 2, PARTITION `pd` VALUES IN (DEFAULT) ( SUBPARTITION `pdsp1`, SUBPARTITION `pdsp2`, SUBPARTITION `pdsp3`, SUBPARTITION `pdsp4` )) \n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n", sb.toString());
    }
}
