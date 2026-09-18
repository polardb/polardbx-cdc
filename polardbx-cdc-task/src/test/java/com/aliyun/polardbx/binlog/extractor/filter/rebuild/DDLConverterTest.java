/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalize;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_HINT_BLACKLIST;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSql;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForMysqlPart;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSqlForPolarPart;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.removeAsyncDdlFlags;
import static com.aliyun.polardbx.binlog.util.CommonUtils.extractPolarxOriginSql;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class DDLConverterTest extends BaseTest {

    @Test
    public void testTryRemoveDropImplicitPk() {
        String sql = "alter table modify_sk_simple_checker_test_tblPF drop column _drds_implicit_id_";

        SQLAlterTableStatement sqlStatement = SQLUtils.parseSQLStatement(sql);
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
            "CREATE TABLE `zqhz0kzsecxfgdf` ( `zsjzmjsoidxxtr` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `7jg0ekks` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `3pf6xdowmaf` int(6) UNSIGNED ZEROFILL DEFAULT NULL, `hkqh6gd` int(6) UNSIGNED ZEROFILL DEFAULT NULL ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4",
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
            "CREATE TABLE `bt` ( `id` int(11) NOT NULL AUTO_INCREMENT, `name` varchar(20) DEFAULT NULL, PRIMARY KEY (`id`) ) ENGINE = InnoDB AUTO_INCREMENT = 200006 DEFAULT CHARSET = utf8mb4",
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
            "/* CDC_TOKEN : 0644b5a0-1ce9-43f9-8b62-62d0a0f59d72 */\n"
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
                + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4",
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
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "", false, null);
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
                + "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*/ /*tddl:enable_recyclebin=true*/ DROP TABLE test_recyclebin_tb",
            sql);
    }

    @Test
    public void testBuildDdlEventSqlForPolarPartWithHashLineComment() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        mockConfig(BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED, "true");

        String tableName = "t_zzz";
        String ddl = "/*DDL_ID=7481263829662302272*/\n"
            + "# ===== hash line comment =====\n"
            + "/*+TDDL:cmd_extra(SEQUENTIAL_CONCURRENT_POLICY=true)*/\n"
            + String.format(
            "CREATE TABLE `%s` (id bigint, data_source TINYINT NOT NULL DEFAULT 0 COMMENT 'test', PRIMARY KEY(id))",
            tableName);

        String expectedSql = "/* ===== hash line comment =====*/ "
            + "/*+TDDL:cmd_extra(SEQUENTIAL_CONCURRENT_POLICY=true)*/ "
            + "CREATE TABLE `t_zzz` ( id bigint, data_source TINYINT NOT NULL DEFAULT 0 COMMENT 'test', "
            + "PRIMARY KEY (id) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs";

        StringBuilder polarBuilder = new StringBuilder();
        buildDdlEventSqlForPolarPart(polarBuilder, ddl, "utf8mb4", "utf8_general_cs", "111111", false, null);
        Assert.assertEquals("# POLARX_ORIGIN_SQL=" + expectedSql + "\n"
            + "# POLARX_TSO=111111\n"
            + "# POLARX_DDL_ID=7481263829662302272\n", polarBuilder.toString());

        StringBuilder mysqlBuilder = new StringBuilder();
        buildDdlEventSqlForMysqlPart(mysqlBuilder, tableName, "utf8mb4", "utf8_general_cs", ddl);
        Assert.assertEquals(expectedSql, mysqlBuilder.toString());
    }

    @Test
    public void testPrivateDDLSwitch() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String sql1 =
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4";
        String sql2 = buildDdlEventSql("", sql1, null, "", "",
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4");
        Assert.assertTrue(sql2.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(sql2.contains("# POLARX_TSO="));

        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "false");
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

    /**
     * 回归：源 DDL 的 CHARSET/COLLATE 取值为反引号包裹的 `binary`。
     * 修复前 tryAttacheCharacterInfo 会把 CHARSET/COLLATE 值归一化为裸词 binary，Druid 再次解析时
     * 会把它当成 BINARY token：要么抛 ParserException，要么把后续 option 静默吞并导致 COLLATE 丢失、
     * charset 元数据被污染；修复后两个值都保留反引号，reformat 产物可被再次正确解析。
     */
    @Test
    public void testProcessDdlSqlCharactersWithBinaryCollation() {
        // CHARSET/COLLATE 保留字白名单由配置维护，默认值见 config.properties
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        String sql = "CREATE TABLE `__orca_in_polardb_m_`.`orca_meta_0000_0000` (\n"
            + "        `redis_key` varbinary(2560) NOT NULL,\n"
            + "        `redis_blob_flag` tinyint(3) UNSIGNED NOT NULL,\n"
            + "        `redis_blob_key` longblob NULL,\n"
            + "        `redis_type` tinyint(3) UNSIGNED NOT NULL,\n"
            + "        `redis_ttl` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_field_cnt` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_ttl_field_cnt` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_max_fields_expiry` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_unique_id` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_first_list_id` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_last_list_id` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_max_list_id` bigint(20) UNSIGNED NULL DEFAULT '0',\n"
            + "        `redis_reserve` blob NULL,\n"
            + "        PRIMARY KEY (`redis_key`, `redis_blob_flag`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = `binary` DEFAULT COLLATE = `binary` "
            + "ROW_FORMAT = Dynamic COMMENT 'Redis Meta'";

        // 源 DDL 自身可被正常解析（反引号包裹的 binary 是合法标识符）
        Assert.assertNotNull(SQLUtils.parseSQLStatement(sql));

        // 经过 reformat（tbCollation = binary），CHARSET 与 COLLATE 值都应保留反引号
        String convertSql = DDLConverter.processDdlSqlCharacters("orca_meta_0000_0000", sql, "binary", "binary");
        log.info("converted sql: {}", convertSql);
        Assert.assertTrue("CHARSET 值应保留反引号", convertSql.contains("CHARSET = `binary`"));
        Assert.assertTrue("COLLATE 值应保留反引号", convertSql.contains("COLLATE = `binary`"));

        // 关键回归：reformat 后的 DDL 应能被再次解析（等同 checkBeforeApply 的行为），
        // 且 CHARSET/COLLATE/ROW_FORMAT 选项不能被 BINARY token 吞并
        assertCharacterOptions(convertSql, "binary", "binary");

        // 下游元数据提取不能被反引号污染
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "__orca_in_polardb_m_", convertSql, null);
        TableMeta tableMeta = memoryTableMeta.find("__orca_in_polardb_m_", "orca_meta_0000_0000");
        Assert.assertEquals("binary", tableMeta.getCharset());
    }

    /**
     * 回归：源 DDL 只有 CHARSET = `binary`、没有显式 COLLATE，且后面还跟着其它 option。
     * 这种形态下裸词化 CHARSET 会直接导致 ParserException（token =）。
     */
    @Test
    public void testProcessDdlSqlCharactersWithBinaryCharsetOnly() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        String sql = "CREATE TABLE `t_binary_charset_only` (\n"
            + "  `id` bigint(20) NOT NULL,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = `binary` ROW_FORMAT = Dynamic COMMENT 'x'";

        String convertSql =
            DDLConverter.processDdlSqlCharacters("t_binary_charset_only", sql, "binary", "binary");
        log.info("converted sql: {}", convertSql);
        assertCharacterOptions(convertSql, "binary", "binary");
    }

    /**
     * 回归：源 DDL 完全没有 CHARSET/COLLATE，全靠 addOption 补全，且 tbCollation 为保留字 binary。
     */
    @Test
    public void testProcessDdlSqlCharactersAttachBinaryCollation() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        String sql = "CREATE TABLE `t_binary_attach` (\n"
            + "  `id` bigint(20) NOT NULL,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB ROW_FORMAT = Dynamic";

        String convertSql = DDLConverter.processDdlSqlCharacters("t_binary_attach", sql, "binary", "binary");
        log.info("converted sql: {}", convertSql);
        assertCharacterOptions(convertSql, "binary", "binary");
    }

    /**
     * 断言 reformat 产物可被再次解析，且 CHARSET、COLLATE 两个选项均以独立 option 形式存在、取值正确。
     * 单纯的 parseSQLStatement 不抛异常不足以证明正确：裸词 binary 会作为 BINARY 一元操作符
     * 静默吞掉后续的 COLLATE 选项。
     */
    private void assertCharacterOptions(String ddl, String expectCharset, String expectCollate) {
        SQLStatement statement = SQLUtils.parseSQLStatement(ddl);
        Assert.assertTrue(statement instanceof MySqlCreateTableStatement);
        MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) statement;

        String actualCharset = null;
        String actualCollate = null;
        for (SQLAssignItem option : createTableStatement.getTableOptions()) {
            String target = StringUtils.upperCase(normalize(option.getTarget().toString()));
            if (StringUtils.equalsAny(target, "CHARACTER SET", "CHARSET")) {
                actualCharset = normalize(option.getValue().toString());
            } else if (StringUtils.equals(target, "COLLATE")) {
                actualCollate = normalize(option.getValue().toString());
            }
        }
        Assert.assertEquals("charset option 解析结果不符合预期", expectCharset, actualCharset);
        Assert.assertEquals("collate option 解析结果不符合预期", expectCollate, actualCollate);
    }

    /**
     * CHARSET/COLLATE 保留字白名单由配置 {@code task_reformat_ddl_character_quote_keywords} 驱动，
     * 覆盖三条分支：配置为空、配置命中（大小写不敏感）、配置未命中。
     */
    @Test
    public void testCollateQuoteKeywordsDrivenByConfig() {
        String binaryDdl = "CREATE TABLE `t_collate_cfg` (\n"
            + "  `id` bigint(20) NOT NULL,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = `binary` DEFAULT COLLATE = `binary`";

        // 分支1：配置为空 -> 白名单为空集合，不加反引号（退化为修复前行为）
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "");
        String sqlWithBlankConfig =
            DDLConverter.processDdlSqlCharacters("t_collate_cfg", binaryDdl, "binary", "binary");
        Assert.assertTrue(sqlWithBlankConfig.contains("COLLATE = binary"));
        Assert.assertFalse(sqlWithBlankConfig.contains("COLLATE = `binary`"));

        // 分支2：配置命中且大小写不敏感（配置写大写 BINARY 也应命中）-> 保留反引号
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "BINARY");
        String sqlWithUpperConfig =
            DDLConverter.processDdlSqlCharacters("t_collate_cfg", binaryDdl, "binary", "binary");
        Assert.assertTrue(sqlWithUpperConfig.contains("CHARSET = `binary`"));
        Assert.assertTrue(sqlWithUpperConfig.contains("COLLATE = `binary`"));
        assertCharacterOptions(sqlWithUpperConfig, "binary", "binary");

        // 分支3：配置未命中（普通 collation）-> 不加反引号，保持原有行为
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");
        String normalDdl = "CREATE TABLE `t_collate_normal` (\n"
            + "  `id` bigint(20) NOT NULL,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci";
        String sqlNormalCollation =
            DDLConverter.processDdlSqlCharacters("t_collate_normal", normalDdl, "utf8mb4", "utf8mb4_general_ci");
        Assert.assertTrue(sqlNormalCollation.contains("COLLATE = utf8mb4_general_ci"));
        Assert.assertFalse(sqlNormalCollation.contains("COLLATE = `utf8mb4_general_ci`"));
        assertCharacterOptions(sqlNormalCollation, "utf8mb4", "utf8mb4_general_ci");
    }

    // ==================== tryAttacheCharacterInfo 直接驱动的用例 ====================

    /**
     * 已显式指定反引号包裹的保留字 charset/collate：原样保留，且不重复追加 option。
     */
    @Test
    public void testAttacheCharacterInfoKeepBacktickedBinary() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB "
                + "DEFAULT CHARSET = `binary` DEFAULT COLLATE = `binary` ROW_FORMAT = Dynamic",
            "binary");

        Assert.assertEquals("`binary`", options.get("CHARSET"));
        Assert.assertEquals("`binary`", options.get("COLLATE"));
        // 不能因为补全逻辑而多出 CHARACTER SET
        Assert.assertFalse(options.containsKey("CHARACTER SET"));
        Assert.assertEquals(4, options.size());
    }

    /**
     * 显式指定的 charset/collate 是字符串字面量（SQLCharExpr）时，归一化为裸词。
     */
    @Test
    public void testAttacheCharacterInfoNormalizeStringLiteral() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) "
                + "DEFAULT CHARSET = 'utf8mb4' DEFAULT COLLATE = 'utf8mb4_general_ci'",
            "utf8mb4_general_ci");

        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));

        // 字符串字面量形式的保留字 collation 同样要加反引号
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = 'binary'", "binary");
        Assert.assertEquals("`binary`", options.get("CHARSET"));
        Assert.assertEquals("`binary`", options.get("COLLATE"));
    }

    /**
     * DDL 中完全没有 charset/collate：两者都按 tbCollation 补全，保留字场景带反引号。
     */
    @Test
    public void testAttacheCharacterInfoAttachBoth() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");
        String ddl = "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB ROW_FORMAT = Dynamic";

        Map<String, String> options = attachAndCollectOptions(ddl, "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARACTER SET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));

        options = attachAndCollectOptions(ddl, "binary");
        Assert.assertEquals("`binary`", options.get("CHARACTER SET"));
        Assert.assertEquals("`binary`", options.get("COLLATE"));
    }

    /**
     * 仅显式指定 charset 时，collate 只在 charset 与 tbCollation 对应 charset 一致时才补全。
     */
    @Test
    public void testAttacheCharacterInfoCollateAttachDependsOnCharset() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        // charset 不一致（utf8 vs utf8mb4）：不补 collate，避免与显式 charset 冲突
        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8", "utf8mb4_general_ci");
        Assert.assertEquals("utf8", options.get("CHARSET"));
        Assert.assertFalse("charset 不一致时不应补 collate", options.containsKey("COLLATE"));

        // charset 一致：补 collate
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8mb4", "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));
    }

    /**
     * CHARACTER SET 写法与仅指定 COLLATE 的写法都应被正确识别，不产生重复 option。
     */
    @Test
    public void testAttacheCharacterInfoRecognizeAllOptionForms() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        // CHARACTER SET 形式已存在 -> 不再追加 charset，只补 collate
        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARACTER SET = utf8mb4", "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARACTER SET"));
        Assert.assertFalse(options.containsKey("CHARSET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));

        // 只有 COLLATE -> 补 charset，且 collate 不重复
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT COLLATE = utf8mb4_general_ci", "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARACTER SET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));
        Assert.assertEquals(2, options.size());
    }

    /**
     * tbCollation 为空，以及 CREATE TABLE ... LIKE：都不做任何补全。
     */
    @Test
    public void testAttacheCharacterInfoSkipAttach() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");
        String ddl = "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB";

        Assert.assertEquals(1, attachAndCollectOptions(ddl, null).size());
        Assert.assertEquals(1, attachAndCollectOptions(ddl, "  ").size());

        MySqlCreateTableStatement likeStatement = SQLUtils.parseSQLStatement("CREATE TABLE `t2` LIKE `t1`");
        Assert.assertNotNull(likeStatement);
        DDLConverter.tryAttacheCharacterInfo(likeStatement, "utf8mb4_general_ci");
        Assert.assertTrue("CREATE TABLE LIKE 不应补 charset/collate", likeStatement.getTableOptions().isEmpty());
    }

    /**
     * 现状刻画：源 DDL 用裸词保留字 binary 作为 charset（仅在其后没有其它 option 时 Druid 才能解析成功），
     * Druid 会把取值大写成 BINARY，当前实现原样保留该大小写，导致产物中 CHARSET 与 COLLATE 大小写不一致。
     * 内部消费方（CharsetConversion / ConsistencyChecker / columnTypeMatch）均大小写不敏感，故不影响功能，
     * 但产物文本不规范，如后续修正为统一小写，本用例期望值需同步调整。
     */
    @Test
    public void testAttacheCharacterInfoBareReservedWordCharsetCaseLeak() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB DEFAULT CHARSET = binary", "binary");

        Assert.assertEquals("`BINARY`", options.get("CHARSET"));
        Assert.assertEquals("`binary`", options.get("COLLATE"));
    }

    /**
     * 现状刻画：tbCollation 无法在 CharsetConversion 中查到对应 charset 时，
     * CHARACTER SET 不补（charset 为空），但 COLLATE 仍被补上，产出"只有未知 collate"的 DDL。
     */
    @Test
    public void testAttacheCharacterInfoUnknownCollation() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB", "not_exist_collation_ci");

        Assert.assertFalse(options.containsKey("CHARACTER SET"));
        Assert.assertEquals("not_exist_collation_ci", options.get("COLLATE"));
    }

    /**
     * 现状刻画：charset option 取值不是 SQLIdentifierExpr / SQLCharExpr 时（如 SQLBinaryOpExpr），
     * 归一化被跳过（表达式结构不被破坏，符合预期），但 optionCharset 也随之保持为空，
     * 使后续补全逻辑误判为"DDL 未显式指定 charset"，从而补上一个 COLLATE。
     */
    @Test
    public void testAttacheCharacterInfoUnsupportedValueExpr() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        MySqlCreateTableStatement statement = SQLUtils.parseSQLStatement(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8");
        Assert.assertNotNull(statement);
        SQLAssignItem charsetOption = statement.getTableOptions().get(0);
        Assert.assertEquals("CHARSET", StringUtils.upperCase(normalize(charsetOption.getTarget().toString())));
        charsetOption.setValue(new SQLBinaryOpExpr(new SQLIdentifierExpr("utf8"), SQLBinaryOperator.Equality,
            new SQLIdentifierExpr("x")));

        DDLConverter.tryAttacheCharacterInfo(statement, "utf8mb4_general_ci");

        // 表达式保持原样，没有被拍平成一个标识符
        Assert.assertTrue(charsetOption.getValue() instanceof SQLBinaryOpExpr);
        // 已显式声明 charset(utf8)，与 utf8mb4_general_ci 对应的 utf8mb4 并不一致，却仍补上了 collate
        Assert.assertTrue(statement.getTableOptions().stream()
            .anyMatch(i -> "COLLATE".equalsIgnoreCase(normalize(i.getTarget().toString()))));
    }

    /**
     * charset 一致性比较必须大小写不敏感：DDL 写 UTF8MB4、tbCollation 对应 utf8mb4，仍应视为一致并补全 collate。
     * 同理，tbCollation 本身大写时也应能查到 charset、并命中保留字白名单。
     */
    @Test
    public void testAttacheCharacterInfoCompareCharsetIgnoreCase() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = UTF8MB4", "utf8mb4_general_ci");
        Assert.assertEquals("UTF8MB4", options.get("CHARSET"));
        Assert.assertEquals("大小写不同也应视为 charset 一致，从而补全 collate",
            "utf8mb4_general_ci", options.get("COLLATE"));

        // tbCollation 大写：collation -> charset 查询与保留字白名单匹配都应大小写不敏感。
        // 同时刻画一个现象：CHARACTER SET 的值来自 CharsetConversion 查表返回的规范小写名，
        // 而 COLLATE 的值是 tbCollation 原文透传，因此两者大小写可能不一致。
        options = attachAndCollectOptions("CREATE TABLE `t` (`id` bigint(20) NOT NULL)", "BINARY");
        Assert.assertEquals("`binary`", options.get("CHARACTER SET"));
        Assert.assertEquals("`BINARY`", options.get("COLLATE"));
    }

    /**
     * 核心语义：只补全缺失项，绝不用 tbCollation 覆盖 DDL 中已显式指定的值（以 DDL 为准），
     * 也不介入 DDL 自身 charset 与 collate 的不匹配。
     */
    @Test
    public void testAttacheCharacterInfoNeverOverrideExistingValue() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        // 已有 collate 与 tbCollation 不同 -> 保持 DDL 中的 utf8mb4_bin
        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_bin",
            "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4_bin", options.get("COLLATE"));

        // DDL 自身 charset 与 collate 矛盾 -> 不做任何纠正
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8mb4 COLLATE = utf8_general_ci",
            "utf8mb4_general_ci");
        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8_general_ci", options.get("COLLATE"));
    }

    /**
     * 归一化主线：取值带反引号但不在保留字白名单时，反引号应被剔除（不能把反引号带到产物里）。
     */
    @Test
    public void testAttacheCharacterInfoStripBacktickForNonKeyword() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) "
                + "DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`",
            "utf8mb4_general_ci");

        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));
    }

    /**
     * 幂等性：reformat 产物再次进入本方法（CDC 内 DDL 可能被多个路径处理）结果必须稳定，
     * 尤其不能出现反引号嵌套或 option 重复追加。
     */
    @Test
    public void testAttacheCharacterInfoIdempotent() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        String[] ddls = new String[] {
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) ENGINE = InnoDB",
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = `binary` DEFAULT COLLATE = `binary`",
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = 'utf8mb4'"
        };
        for (String ddl : ddls) {
            MySqlCreateTableStatement statement = SQLUtils.parseSQLStatement(ddl);
            Assert.assertNotNull(statement);
            DDLConverter.tryAttacheCharacterInfo(statement, "binary");
            String firstRound = statement.toUnformattedString();

            DDLConverter.tryAttacheCharacterInfo(statement, "binary");
            Assert.assertEquals("重复处理应幂等: " + ddl, firstRound, statement.toUnformattedString());

            // 对产物重新解析后再跑一轮，同样应稳定
            MySqlCreateTableStatement reparsed = SQLUtils.parseSQLStatement(firstRound);
            Assert.assertNotNull(reparsed);
            DDLConverter.tryAttacheCharacterInfo(reparsed, "binary");
            Assert.assertEquals("重新解析后再处理应幂等: " + ddl, firstRound, reparsed.toUnformattedString());
        }
    }

    /**
     * 边界：已显式指定 charset，但 tbCollation 查不到对应 charset（charset 为 null）。
     * 此时 optionCharset.equalsIgnoreCase(null) 应安全返回 false（不能 NPE）且不补 collate。
     */
    @Test
    public void testAttacheCharacterInfoExplicitCharsetWithUnknownCollation() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8mb4", "not_exist_collation_ci");

        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertFalse("未知 collation 无法与显式 charset 比对，不应补 collate", options.containsKey("COLLATE"));
    }

    /**
     * 现状刻画：CHARSET 与 CHARACTER SET 同时出现时，optionCharset 取最后一个，
     * 故是否补 collate 由最后一个 charset option 决定。
     */
    @Test
    public void testAttacheCharacterInfoDuplicatedCharsetOptions() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        // 最后一个是 utf8mb4，与 tbCollation 对应 charset 一致 -> 补 collate
        Map<String, String> options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARSET = utf8 DEFAULT CHARACTER SET = utf8mb4",
            "utf8mb4_general_ci");
        Assert.assertEquals("utf8", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4", options.get("CHARACTER SET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));

        // 最后一个是 utf8，与 tbCollation 对应 charset 不一致 -> 不补 collate
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) DEFAULT CHARACTER SET = utf8mb4 DEFAULT CHARSET = utf8",
            "utf8mb4_general_ci");
        Assert.assertFalse(options.containsKey("COLLATE"));
    }

    /**
     * 作用域约束：本方法只处理 table option，不得改动列级的 CHARACTER SET / COLLATE。
     */
    @Test
    public void testAttacheCharacterInfoNotTouchColumnLevelCharacter() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        MySqlCreateTableStatement statement = SQLUtils.parseSQLStatement(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL, `c` varchar(10) CHARACTER SET `binary`) "
                + "DEFAULT CHARSET = utf8mb4");
        Assert.assertNotNull(statement);
        String columnBefore = statement.getTableElementList().get(1).toString();

        DDLConverter.tryAttacheCharacterInfo(statement, "utf8mb4_general_ci");

        Assert.assertEquals("列级 charset 不应被本方法修改",
            columnBefore, statement.getTableElementList().get(1).toString());
    }

    /**
     * 边界：table option 列表为空（循环不执行），以及小写 option 名写法。
     */
    @Test
    public void testAttacheCharacterInfoEmptyOptionsAndLowerCaseForm() {
        mockConfig(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS, "binary");

        Map<String, String> options =
            attachAndCollectOptions("CREATE TABLE `t` (`id` bigint(20) NOT NULL)", "utf8mb4_general_ci");
        Assert.assertEquals(2, options.size());
        Assert.assertEquals("utf8mb4", options.get("CHARACTER SET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));

        // 小写写法应被识别为已指定，不产生重复 option
        options = attachAndCollectOptions(
            "CREATE TABLE `t` (`id` bigint(20) NOT NULL) default charset=utf8mb4 collate=utf8mb4_general_ci",
            "utf8mb4_general_ci");
        Assert.assertEquals(2, options.size());
        Assert.assertEquals("utf8mb4", options.get("CHARSET"));
        Assert.assertEquals("utf8mb4_general_ci", options.get("COLLATE"));
    }

    /**
     * 直接驱动 tryAttacheCharacterInfo，返回处理后的 table option 原始文本（不做 normalize），
     * 便于精确断言反引号与大小写；同时校验产物可被再次解析（等同下游 checkBeforeApply 的行为）。
     */
    private Map<String, String> attachAndCollectOptions(String ddl, String tbCollation) {
        MySqlCreateTableStatement statement = SQLUtils.parseSQLStatement(ddl);
        Assert.assertNotNull(statement);
        DDLConverter.tryAttacheCharacterInfo(statement, tbCollation);

        Map<String, String> options = new LinkedHashMap<>();
        for (SQLAssignItem item : statement.getTableOptions()) {
            options.put(StringUtils.upperCase(normalize(item.getTarget().toString())), item.getValue().toString());
        }
        log.info("converted sql: {}", statement.toUnformattedString());
        Assert.assertNotNull(SQLUtils.parseSQLStatement(statement.toUnformattedString()));
        return options;
    }

    @Test
    public void testHintsFilter() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        mockConfig(TASK_REFORMAT_DDL_HINT_BLACKLIST,
            "GSI_BACKFILL_POSITION_MARK,GSI_BACKFILL_BATCH_SIZE,ALLOW_ADD_GSI");
        String sql =
            "/*+TDDL:CMD_EXTRA(GSI_BACKFILL_BATCH_SIZE=2, gsi_backfill_position_mark = \"[{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100001\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_0\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100002\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_1\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000000_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_2\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_3\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"-1\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_4\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"},{\\\"columnIndex\\\":0,\\\"endTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"extra\\\":\\\"{\\\\\\\"testCaseName\\\\\\\":\\\\\\\"GsiBackfillResumeTest\\\\\\\"}\\\",\\\"id\\\":-1,\\\"indexName\\\":\\\"g_resume_id\\\",\\\"indexSchema\\\":\\\"cp1_ddl1_3343801\\\",\\\"jobId\\\":-1,\\\"lastValue\\\":\\\"100000\\\",\\\"message\\\":\\\"\\\",\\\"parameterMethod\\\":\\\"setString\\\",\\\"physicalDb\\\":\\\"CP1_DDL1_3343801_000001_GROUP\\\",\\\"physicalTable\\\":\\\"gsi_backfill_resume_primary_Khjv_5\\\",\\\"startTime\\\":\\\"2023-09-12 21:07:51\\\",\\\"status\\\":-1,\\\"successRowCount\\\":0,\\\"tableName\\\":\\\"gsi_backfill_resume_primary\\\",\\\"tableSchema\\\":\\\"cp1_ddl1_3343801\\\"}]\", ALLOW_ADD_GSI=TRUE)*/ "
                + "CREATE GLOBAL INDEX g_resume_id ON gsi_backfill_resume_primary (id) COVERING (c_bit_1, c_bit_8, c_bit_16, c_bit_32, c_bit_64, c_tinyint_1, c_tinyint_1_un, c_tinyint_4, c_tinyint_4_un, c_tinyint_8, c_tinyint_8_un, c_smallint_1, c_smallint_16, c_smallint_16_un, c_mediumint_1, c_mediumint_24, c_mediumint_24_un, c_int_1, c_int_32, c_int_32_un, c_bigint_1, c_bigint_64, c_bigint_64_un, c_decimal, c_decimal_pr, c_float, c_float_pr, c_float_un, c_double, c_double_pr, c_double_un, c_date, c_datetime, c_datetime_1, c_datetime_3, c_datetime_6, c_timestamp_1, c_timestamp_3, c_timestamp_6, c_time, c_time_1, c_time_3, c_time_6, c_year, c_year_4, c_char, c_varchar, c_binary, c_varbinary, c_blob_tiny, c_blob, c_blob_medium, c_blob_long, c_text_tiny, c_text, c_text_medium, c_text_long, c_enum, c_set, c_json, c_geometory, c_point, c_linestring, c_polygon, c_multipoint, c_multilinestring, c_multipolygon) DBPARTITION BY HASH(id) TBPARTITION BY HASH(id) TBPARTITIONS 7";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        String expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:CMD_EXTRA(  )*/ CREATE GLOBAL INDEX g_resume_id ON gsi_backfill_resume_primary (id) COVERING (c_bit_1, c_bit_8, c_bit_16, c_bit_32, c_bit_64, c_tinyint_1, c_tinyint_1_un, c_tinyint_4, c_tinyint_4_un, c_tinyint_8, c_tinyint_8_un, c_smallint_1, c_smallint_16, c_smallint_16_un, c_mediumint_1, c_mediumint_24, c_mediumint_24_un, c_int_1, c_int_32, c_int_32_un, c_bigint_1, c_bigint_64, c_bigint_64_un, c_decimal, c_decimal_pr, c_float, c_float_pr, c_float_un, c_double, c_double_pr, c_double_un, c_date, c_datetime, c_datetime_1, c_datetime_3, c_datetime_6, c_timestamp_1, c_timestamp_3, c_timestamp_6, c_time, c_time_1, c_time_3, c_time_6, c_year, c_year_4, c_char, c_varchar, c_binary, c_varbinary, c_blob_tiny, c_blob, c_blob_medium, c_blob_long, c_text_tiny, c_text, c_text_medium, c_text_long, c_enum, c_set, c_json, c_geometory, c_point, c_linestring, c_polygon, c_multipoint, c_multilinestring, c_multipolygon) DBPARTITION BY HASH(id) TBPARTITION BY HASH(id) TBPARTITIONS 7\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        mockConfig(TASK_REFORMAT_DDL_HINT_BLACKLIST,
            "GSI_BACKFILL_POSITION_MARK,FP_PAUSE_AFTER_DDL_TASK_EXECUTION,FP_STATISTIC_SAMPLE_ERROR");
        sql = "/*+TDDL:cmd_extra(FP_PAUSE_AFTER_DDL_TASK_EXECUTION='AlterTablePhyDdlTask')*/ "
            + "ALTER TABLE wumu_test DROP COLUMN b";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:cmd_extra()*/ ALTER TABLE wumu_test DROP COLUMN b\n"
                + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "/*+TDDL:cmd_extra(FP_STATISTIC_SAMPLE_ERROR=true)*/ "
            + "ALTER TABLE t1 ADD GLOBAL INDEX gsi1 (a) PARTITION BY KEY (a) PARTITIONS 5 WITH TABLEGROUP= tg4723 IMPLICIT";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        expectSql =
            "# POLARX_ORIGIN_SQL=/*+TDDL:cmd_extra()*/ ALTER TABLE t1 ADD GLOBAL INDEX gsi1 (a) PARTITION BY KEY (a) PARTITIONS 5 WITH TABLEGROUP= tg4723 IMPLICIT\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testRemoveLocalityForCreateTableGroup() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String sql = "CREATE TABLEGROUP tg1 "
            + "LOCALITY = 'dn=xgdn-ddl-230916222943-5eb4-xv8f-dn-0, xgdn-ddl-230916222943-5eb4-xv8f-dn-1'";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        String expectSql = "# POLARX_ORIGIN_SQL=CREATE TABLEGROUP tg1\n" + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "CREATE TABLEGROUP sellerid_tg "
            + "PARTITION BY LIST COLUMNS ( BIGINT) SUBPARTITION BY KEY ( BIGINT,  BIGINT) "
            + "( PARTITION p1 VALUES IN (1, 2) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-0' SUBPARTITIONS 1,  "
            + "  PARTITION p2 VALUES IN (3, 4) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-1' SUBPARTITIONS 2,  "
            + "  PARTITION p3 VALUES IN (5, 6) LOCALITY 'dn=ziyang-116-do-not-delete-kwmg-dn-0' SUBPARTITIONS 4,  "
            + "  PARTITION p_default VALUES IN (DEFAULT) LOCALITY'dn=ziyang-116-do-not-delete-kwmg-dn-1' SUBPARTITIONS 4 )";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
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
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        // create index
        String sql = "CREATE UNIQUE GLOBAL INDEX `W9H4uo` ON `8f6` (`Du3z` DESC)"
            + "PARTITION BY LIST (`Du3z`) ( "
            + "     PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ',  "
            + "     PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1 , ziyang-107-do-not-delete-l4rm-dn-1, ziyang-107-do-not-delete-l4rm-dn-0 ',  "
            + "     PARTITION `BmEnjPq` VALUES IN (68, 118) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-0  ', "
            + "     PARTITION `t61YgnWpjT` VALUES IN (47) LOCALITY 'dn= ziyang-107-do-not-delete-l4rm-dn-1  ' ) "
            + "USING HASH";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
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
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
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
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        expectSql =
            "# POLARX_ORIGIN_SQL=CREATE TABLE t1 ( id bigint PRIMARY KEY, Du3z bigint NOT NULL, GLOBAL INDEX `W9H4uo` USING HASH(`Du3z` DESC) PARTITION BY LIST (`Du3z`) ( PARTITION `4JUbhOvlVLPrXZ` VALUES IN (11),  PARTITION `GcFjzi29FV0Nr` VALUES IN (86, 72),  PARTITION `BmEnjPq` VALUES IN (68, 118),  PARTITION `t61YgnWpjT` VALUES IN (47) ) ) DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_cs\n"
                + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testRemoveLocalityForPartitionBy() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        String sql = "ALTER TABLE t1 PARTITION BY HASH (a) "
            + "PARTITIONS 16 LOCALITY = 'DN=ZIYANG-128-DO-NOT-DELETE-JCCK-DN-1' WITH TABLEGROUP=tg3588 IMPLICIT";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        String expectSql = "# POLARX_ORIGIN_SQL=ALTER TABLE t1 PARTITION BY HASH (a) PARTITIONS 16 "
            + "WITH TABLEGROUP=tg3588 IMPLICIT\n" + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());

        sql = "ALTER TABLE t1 SINGLE "
            + "LOCALITY = 'DN=ZIYANG-129-DO-NOT-DELETE-RLP2-DN-1' WITH TABLEGROUP=single_tg4465 IMPLICIT";
        sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        expectSql = "# POLARX_ORIGIN_SQL=ALTER TABLE t1 SINGLE WITH TABLEGROUP=single_tg4465 IMPLICIT\n"
            + "# POLARX_TSO=\n" + "# POLARX_DDL_ID=0\n";
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testProcedure() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");
        String ddl = "CREATE PROCEDURE `dkpt`.`report_Turnover_List1_copy1` (\n"
            + "        IN `sTime` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,\n"
            + "        IN `eTime` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,\n"
            + "        IN `operationMan` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,\n"
            + "        IN `companyId` bigint(20),\n"
            + "        IN `regionId` bigint(20),\n"
            + "        IN `pointId` bigint(20),\n"
            + "        IN `sStationId` bigint(20),\n"
            + "        IN `cStationId` bigint(20),\n"
            + "        IN `orgList` varchar(10000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci\n"
            + ")\n"
            + "BEGIN\n"
            + "        DECLARE selFlag VARCHAR(1) DEFAULT '0';\n"
            + "        IF companyId IS NULL\n"
            + "        AND regionId IS NULL\n"
            + "        AND pointId IS NULL\n"
            + "        AND sStationId IS NULL\n"
            + "        AND cStationId IS NULL THEN\n"
            + "                SET selFlag = '1';\n"
            + "        END IF;\n"
            + "        IF sTime IS NOT NULL\n"
            + "        AND eTime IS NOT NULL\n"
            + "        AND DATE_FORMAT(sTime, '%Y-%m') <> DATE_FORMAT(eTime, '%Y-%m') THEN\n"
            + "                SET eTime = date_add(sTime, INTERVAL 31 DAY);\n"
            + "        END IF;\n"
            + "        IF selFlag = '1' THEN\n"
            + "                -- EXPLAIN\n"
            + "                SELECT SQL_NO_CACHE t1.*, t2.*\n"
            + "                FROM (\n"
            + "                        SELECT bill.id AS id1, company.`NAME` AS \"公司\", bill.COMPANY_ID AS cid, point.`NAME` AS \"网点\", region.`NAME` AS \"大区\"\n"
            + "                                , bill.CREATOR AS \"制单人\", bill.`CODE` AS \"运单号\", DATE_FORMAT(bill.ORDER_DATE, '%Y-%m-%d') AS \"开单日期\"\n"
            + "                                , sStation.`NAME` AS \"始发站\", changeStation.`NAME` AS \"中转站\", billExtended.WITH_PAID_AMOUNT AS \"提付\", billExtended.CASH_FEE AS \"现付\", billExtended.MONTHLY_FEE AS \"月结\"\n"
            + "                                , billExtended.SHORT_AMOUNT AS \"短欠\", billExtended.DISCOUNT_FEE AS \"折扣折让\", billExtended.COLLECTION_GOODS_FEE AS \"代收货款\", billExtended.TOTAL_SHIP_FEE AS \"总运费\", billExtended.RECEIVED_TRANSFER_FEE AS \"实收运费\"\n"
            + "                                , bill.SHIP_MAN AS \"发货人\", bill.SHIP_COMPANY AS \"发货公司\", company.name AS \"开单公司\", bill.BILLING_VOLUMN AS \"计费体积\", billExtended.SETTLE_WEGHT AS \"结算重量\"\n"
            + "                        FROM Thorn_Base_Organization point\n"
            + "                                LEFT JOIN kms_op_waybill bill ON point.id = bill.START_POINT_ID\n"
            + "                                LEFT JOIN Kms_Op_WayBill_Extended billExtended ON billExtended.WAY_BILL_ID = bill.id\n"
            + "                                LEFT JOIN Thorn_Base_Organization company ON company.ID = bill.COMPANY_ID\n"
            + "                                LEFT JOIN kms_base_region region ON region.id = point.REGION_ID\n"
            + "                                LEFT JOIN Kms_Base_Station sStation ON sStation.id = bill.START_STATION_ID\n"
            + "                                LEFT JOIN Kms_Base_Station changeStation ON changeStation.id = bill.TRANSIT_STATION_ID\n"
            + "                        WHERE 1 = 1\n"
            + "                                AND bill.STATUS <> 'W' --       AND (DATE_FORMAT(bill.ORDER_DATE,'%Y-%m-%d %H:%i') >=DATE_FORMAT(sTime,'%Y-%m-%d %H:%i') OR sTime IS NULL )\n"
            + "                                --      AND (DATE_FORMAT(bill.ORDER_DATE,'%Y-%m-%d %H:%i') <=DATE_FORMAT(eTime,'%Y-%m-%d %H:%i') OR eTime IS NULL )\n"
            + "                                AND (sTime IS NULL\n"
            + "                                        OR bill.ORDER_DATE >= sTime)\n"
            + "                                AND (eTime IS NULL\n"
            + "                                        OR bill.ORDER_DATE <= eTime)\n"
            + "                                AND (bill.CREATOR = operationMan\n"
            + "                                        OR operationMan IS NULL)\n"
            + "                                AND FIND_IN_SET(point.ID, orgList)\n"
            + "                ) t1\n"
            + "                        LEFT JOIN (\n"
            + "                                SELECT apple.APPLE_OBJECT_ID AS id2, apple.ADD_CHANGE_FEE AS \"非提付异动金额\"\n"
            + "                                FROM KMS_OP_CHANGE_APPLE apple\n"
            + "                                WHERE apple.TYPE = 'YD'\n"
            + "                                        AND apple.PAY_CUSTOMER = 'P'\n"
            + "                                        AND apple.`STATUS` = 'ZX'\n"
            + "                        ) t2\n"
            + "                        ON t1.id1 = t2.id2;\n"
            + "        ELSE\n"
            + "                -- EXPLAIN\n"
            + "                SELECT SQL_NO_CACHE t1.*, t2.*\n"
            + "                FROM (\n"
            + "                        SELECT bill.id AS id1, company.`NAME` AS \"公司\", bill.COMPANY_ID AS cid, point.`NAME` AS \"网点\", region.`NAME` AS \"大区\"\n"
            + "                                , bill.CREATOR AS \"制单人\", bill.`CODE` AS \"运单号\", DATE_FORMAT(bill.ORDER_DATE, '%Y-%m-%d') AS \"开单日期\"\n"
            + "                                , sStation.`NAME` AS \"始发站\", changeStation.`NAME` AS \"中转站\", billExtended.WITH_PAID_AMOUNT AS \"提付\", billExtended.CASH_FEE AS \"现付\", billExtended.MONTHLY_FEE AS \"月结\"\n"
            + "                                , billExtended.SHORT_AMOUNT AS \"短欠\", billExtended.DISCOUNT_FEE AS \"折扣折让\", billExtended.COLLECTION_GOODS_FEE AS \"代收货款\", billExtended.TOTAL_SHIP_FEE AS \"总运费\", billExtended.RECEIVED_TRANSFER_FEE AS \"实收运费\"\n"
            + "                                , bill.SHIP_MAN AS \"发货人\", bill.SHIP_COMPANY AS \"发货公司\", company.name AS \"开单公司\", bill.BILLING_VOLUMN AS \"计费体积\", billExtended.SETTLE_WEGHT AS \"结算重量\"\n"
            + "                        FROM Thorn_Base_Organization point\n"
            + "                                LEFT JOIN kms_op_waybill bill ON point.id = bill.START_POINT_ID\n"
            + "                                LEFT JOIN Kms_Op_WayBill_Extended billExtended ON billExtended.WAY_BILL_ID = bill.id\n"
            + "                                LEFT JOIN Thorn_Base_Organization company ON company.ID = bill.COMPANY_ID\n"
            + "                                LEFT JOIN kms_base_region region ON region.id = point.REGION_ID\n"
            + "                                LEFT JOIN Kms_Base_Station sStation ON sStation.id = bill.START_STATION_ID\n"
            + "                                LEFT JOIN Kms_Base_Station changeStation ON changeStation.id = bill.TRANSIT_STATION_ID\n"
            + "                        WHERE 1 = 1\n"
            + "                                AND bill.STATUS <> 'W' --   AND (DATE_FORMAT(bill.ORDER_DATE,'%Y-%m-%d %H:%i') >=DATE_FORMAT(sTime,'%Y-%m-%d %H:%i') OR sTime IS NULL )\n"
            + "                                --      AND (DATE_FORMAT(bill.ORDER_DATE,'%Y-%m-%d %H:%i') <=DATE_FORMAT(eTime,'%Y-%m-%d %H:%i') OR eTime IS NULL )\n"
            + "                                AND (sTime IS NULL\n"
            + "                                        OR bill.ORDER_DATE >= sTime)\n"
            + "                                AND (eTime IS NULL\n"
            + "                                        OR bill.ORDER_DATE <= eTime) --         AND (bill.CREATOR=operationMan or operationMan  is NULL)\n"
            + "                                AND (companyId IS NULL\n"
            + "                                        OR FIND_IN_SET(company.ID, (\n"
            + "                                                SELECT tbo1.ORG_LIST\n"
            + "                                                FROM thorn_base_organization tbo1\n"
            + "                                                WHERE tbo1.ID = companyId\n"
            + "                                        ))\n"
            + "                                        AND point.COMPANY_ID = companyId)\n"
            + "                ) t1\n"
            + "                        LEFT JOIN (\n"
            + "                                SELECT apple.APPLE_OBJECT_ID AS id2, apple.ADD_CHANGE_FEE AS \"非提付异动金额\"\n"
            + "                                FROM KMS_OP_CHANGE_APPLE apple\n"
            + "                                WHERE apple.TYPE = 'YD'\n"
            + "                                        AND apple.PAY_CUSTOMER = 'P'\n"
            + "                                        AND apple.`STATUS` = 'ZX'\n"
            + "                        ) t2\n"
            + "                        ON t1.id1 = t2.id2;\n"
            + "        END IF;\n"
            + "END;\n";
        String expected = "# POLARX_ORIGIN_SQL_ENCODE=BASE64\n"
            + "# POLARX_ORIGIN_SQL=Q1JFQVRFIFBST0NFRFVSRSBgZGtwdGAuYHJlcG9ydF9UdXJub3Zlcl9MaXN0MV9jb3B5MWAgKAoJSU4gYHNUaW1lYCB2YXJjaGFyKDEwMCkgQ0hBUkFDVEVSIFNFVCB1dGY4bWI0IENPTExBVEUgdXRmOG1iNF8wOTAwX2FpX2NpLCAKCUlOIGBlVGltZWAgdmFyY2hhcigxMDApIENIQVJBQ1RFUiBTRVQgdXRmOG1iNCBDT0xMQVRFIHV0ZjhtYjRfMDkwMF9haV9jaSwgCglJTiBgb3BlcmF0aW9uTWFuYCB2YXJjaGFyKDEwMCkgQ0hBUkFDVEVSIFNFVCB1dGY4bWI0IENPTExBVEUgdXRmOG1iNF8wOTAwX2FpX2NpLCAKCUlOIGBjb21wYW55SWRgIGJpZ2ludCgyMCksIAoJSU4gYHJlZ2lvbklkYCBiaWdpbnQoMjApLCAKCUlOIGBwb2ludElkYCBiaWdpbnQoMjApLCAKCUlOIGBzU3RhdGlvbklkYCBiaWdpbnQoMjApLCAKCUlOIGBjU3RhdGlvbklkYCBiaWdpbnQoMjApLCAKCUlOIGBvcmdMaXN0YCB2YXJjaGFyKDEwMDAwKSBDSEFSQUNURVIgU0VUIHV0ZjhtYjQgQ09MTEFURSB1dGY4bWI0XzA5MDBfYWlfY2kKKQpDT05UQUlOUyBTUUwKU1FMIFNFQ1VSSVRZIERFRklORVIKQkVHSU4KCURFQ0xBUkUgc2VsRmxhZyBWQVJDSEFSKDEpIERFRkFVTFQgJzAnOwoJSUYgY29tcGFueUlkIElTIE5VTEwKCUFORCByZWdpb25JZCBJUyBOVUxMCglBTkQgcG9pbnRJZCBJUyBOVUxMCglBTkQgc1N0YXRpb25JZCBJUyBOVUxMCglBTkQgY1N0YXRpb25JZCBJUyBOVUxMIFRIRU4KCQlTRVQgc2VsRmxhZyA9ICcxJzsKCUVORCBJRjsKCUlGIHNUaW1lIElTIE5PVCBOVUxMCglBTkQgZVRpbWUgSVMgTk9UIE5VTEwKCUFORCBEQVRFX0ZPUk1BVChzVGltZSwgJyVZLSVtJykgPD4gREFURV9GT1JNQVQoZVRpbWUsICclWS0lbScpIFRIRU4KCQlTRVQgZVRpbWUgPSBkYXRlX2FkZChzVGltZSwgSU5URVJWQUwgMzEgREFZKTsKCUVORCBJRjsKCUlGIHNlbEZsYWcgPSAnMScgVEhFTgoJCS0tIEVYUExBSU4KCQlTRUxFQ1QgU1FMX05PX0NBQ0hFIHQxLiosIHQyLioKCQlGUk9NICgKCQkJU0VMRUNUIGJpbGwuaWQgQVMgaWQxLCBjb21wYW55LmBOQU1FYCBBUyAi5YWs5Y+4IiwgYmlsbC5DT01QQU5ZX0lEIEFTIGNpZCwgcG9pbnQuYE5BTUVgIEFTICLnvZHngrkiLCByZWdpb24uYE5BTUVgIEFTICLlpKfljLoiCgkJCQksIGJpbGwuQ1JFQVRPUiBBUyAi5Yi25Y2V5Lq6IiwgYmlsbC5gQ09ERWAgQVMgIui/kOWNleWPtyIsIERBVEVfRk9STUFUKGJpbGwuT1JERVJfREFURSwgJyVZLSVtLSVkJykgQVMgIuW8gOWNleaXpeacnyIKCQkJCSwgc1N0YXRpb24uYE5BTUVgIEFTICLlp4vlj5Hnq5kiLCBjaGFuZ2VTdGF0aW9uLmBOQU1FYCBBUyAi5Lit6L2s56uZIiwgYmlsbEV4dGVuZGVkLldJVEhfUEFJRF9BTU9VTlQgQVMgIuaPkOS7mCIsIGJpbGxFeHRlbmRlZC5DQVNIX0ZFRSBBUyAi546w5LuYIiwgYmlsbEV4dGVuZGVkLk1PTlRITFlfRkVFIEFTICLmnIjnu5MiCgkJCQksIGJpbGxFeHRlbmRlZC5TSE9SVF9BTU9VTlQgQVMgIuefreasoCIsIGJpbGxFeHRlbmRlZC5ESVNDT1VOVF9GRUUgQVMgIuaKmOaJo+aKmOiuqSIsIGJpbGxFeHRlbmRlZC5DT0xMRUNUSU9OX0dPT0RTX0ZFRSBBUyAi5Luj5pS26LSn5qy+IiwgYmlsbEV4dGVuZGVkLlRPVEFMX1NISVBfRkVFIEFTICLmgLvov5DotLkiLCBiaWxsRXh0ZW5kZWQuUkVDRUlWRURfVFJBTlNGRVJfRkVFIEFTICLlrp7mlLbov5DotLkiCgkJCQksIGJpbGwuU0hJUF9NQU4gQVMgIuWPkei0p+S6uiIsIGJpbGwuU0hJUF9DT01QQU5ZIEFTICLlj5HotKflhazlj7giLCBjb21wYW55Lm5hbWUgQVMgIuW8gOWNleWFrOWPuCIsIGJpbGwuQklMTElOR19WT0xVTU4gQVMgIuiuoei0ueS9k+enryIsIGJpbGxFeHRlbmRlZC5TRVRUTEVfV0VHSFQgQVMgIue7k+eul+mHjemHjyIKCQkJRlJPTSBUaG9ybl9CYXNlX09yZ2FuaXphdGlvbiBwb2ludAoJCQkJTEVGVCBKT0lOIGttc19vcF93YXliaWxsIGJpbGwgT04gcG9pbnQuaWQgPSBiaWxsLlNUQVJUX1BPSU5UX0lECgkJCQlMRUZUIEpPSU4gS21zX09wX1dheUJpbGxfRXh0ZW5kZWQgYmlsbEV4dGVuZGVkIE9OIGJpbGxFeHRlbmRlZC5XQVlfQklMTF9JRCA9IGJpbGwuaWQKCQkJCUxFRlQgSk9JTiBUaG9ybl9CYXNlX09yZ2FuaXphdGlvbiBjb21wYW55IE9OIGNvbXBhbnkuSUQgPSBiaWxsLkNPTVBBTllfSUQKCQkJCUxFRlQgSk9JTiBrbXNfYmFzZV9yZWdpb24gcmVnaW9uIE9OIHJlZ2lvbi5pZCA9IHBvaW50LlJFR0lPTl9JRAoJCQkJTEVGVCBKT0lOIEttc19CYXNlX1N0YXRpb24gc1N0YXRpb24gT04gc1N0YXRpb24uaWQgPSBiaWxsLlNUQVJUX1NUQVRJT05fSUQKCQkJCUxFRlQgSk9JTiBLbXNfQmFzZV9TdGF0aW9uIGNoYW5nZVN0YXRpb24gT04gY2hhbmdlU3RhdGlvbi5pZCA9IGJpbGwuVFJBTlNJVF9TVEFUSU9OX0lECgkJCVdIRVJFIDEgPSAxCgkJCQlBTkQgYmlsbC5TVEFUVVMgPD4gJ1cnIC0tICAgICAgIEFORCAoREFURV9GT1JNQVQoYmlsbC5PUkRFUl9EQVRFLCclWS0lbS0lZCAlSDolaScpID49REFURV9GT1JNQVQoc1RpbWUsJyVZLSVtLSVkICVIOiVpJykgT1Igc1RpbWUgSVMgTlVMTCApCgkJCQktLSAgICAgIEFORCAoREFURV9GT1JNQVQoYmlsbC5PUkRFUl9EQVRFLCclWS0lbS0lZCAlSDolaScpIDw9REFURV9GT1JNQVQoZVRpbWUsJyVZLSVtLSVkICVIOiVpJykgT1IgZVRpbWUgSVMgTlVMTCApCgkJCQlBTkQgKHNUaW1lIElTIE5VTEwKCQkJCQlPUiBiaWxsLk9SREVSX0RBVEUgPj0gc1RpbWUpCgkJCQlBTkQgKGVUaW1lIElTIE5VTEwKCQkJCQlPUiBiaWxsLk9SREVSX0RBVEUgPD0gZVRpbWUpCgkJCQlBTkQgKGJpbGwuQ1JFQVRPUiA9IG9wZXJhdGlvbk1hbgoJCQkJCU9SIG9wZXJhdGlvbk1hbiBJUyBOVUxMKQoJCQkJQU5EIEZJTkRfSU5fU0VUKHBvaW50LklELCBvcmdMaXN0KQoJCSkgdDEKCQkJTEVGVCBKT0lOICgKCQkJCVNFTEVDVCBhcHBsZS5BUFBMRV9PQkpFQ1RfSUQgQVMgaWQyLCBhcHBsZS5BRERfQ0hBTkdFX0ZFRSBBUyAi6Z2e5o+Q5LuY5byC5Yqo6YeR6aKdIgoJCQkJRlJPTSBLTVNfT1BfQ0hBTkdFX0FQUExFIGFwcGxlCgkJCQlXSEVSRSBhcHBsZS5UWVBFID0gJ1lEJwoJCQkJCUFORCBhcHBsZS5QQVlfQ1VTVE9NRVIgPSAnUCcKCQkJCQlBTkQgYXBwbGUuYFNUQVRVU2AgPSAnWlgnCgkJCSkgdDIKCQkJT04gdDEuaWQxID0gdDIuaWQyOwoJRUxTRSAKCQktLSBFWFBMQUlOCgkJU0VMRUNUIFNRTF9OT19DQUNIRSB0MS4qLCB0Mi4qCgkJRlJPTSAoCgkJCVNFTEVDVCBiaWxsLmlkIEFTIGlkMSwgY29tcGFueS5gTkFNRWAgQVMgIuWFrOWPuCIsIGJpbGwuQ09NUEFOWV9JRCBBUyBjaWQsIHBvaW50LmBOQU1FYCBBUyAi572R54K5IiwgcmVnaW9uLmBOQU1FYCBBUyAi5aSn5Yy6IgoJCQkJLCBiaWxsLkNSRUFUT1IgQVMgIuWItuWNleS6uiIsIGJpbGwuYENPREVgIEFTICLov5DljZXlj7ciLCBEQVRFX0ZPUk1BVChiaWxsLk9SREVSX0RBVEUsICclWS0lbS0lZCcpIEFTICLlvIDljZXml6XmnJ8iCgkJCQksIHNTdGF0aW9uLmBOQU1FYCBBUyAi5aeL5Y+R56uZIiwgY2hhbmdlU3RhdGlvbi5gTkFNRWAgQVMgIuS4rei9rOermSIsIGJpbGxFeHRlbmRlZC5XSVRIX1BBSURfQU1PVU5UIEFTICLmj5Dku5giLCBiaWxsRXh0ZW5kZWQuQ0FTSF9GRUUgQVMgIueOsOS7mCIsIGJpbGxFeHRlbmRlZC5NT05USExZX0ZFRSBBUyAi5pyI57uTIgoJCQkJLCBiaWxsRXh0ZW5kZWQuU0hPUlRfQU1PVU5UIEFTICLnn63mrKAiLCBiaWxsRXh0ZW5kZWQuRElTQ09VTlRfRkVFIEFTICLmipjmiaPmipjorqkiLCBiaWxsRXh0ZW5kZWQuQ09MTEVDVElPTl9HT09EU19GRUUgQVMgIuS7o+aUtui0p+asviIsIGJpbGxFeHRlbmRlZC5UT1RBTF9TSElQX0ZFRSBBUyAi5oC76L+Q6LS5IiwgYmlsbEV4dGVuZGVkLlJFQ0VJVkVEX1RSQU5TRkVSX0ZFRSBBUyAi5a6e5pS26L+Q6LS5IgoJCQkJLCBiaWxsLlNISVBfTUFOIEFTICLlj5HotKfkuroiLCBiaWxsLlNISVBfQ09NUEFOWSBBUyAi5Y+R6LSn5YWs5Y+4IiwgY29tcGFueS5uYW1lIEFTICLlvIDljZXlhazlj7giLCBiaWxsLkJJTExJTkdfVk9MVU1OIEFTICLorqHotLnkvZPnp68iLCBiaWxsRXh0ZW5kZWQuU0VUVExFX1dFR0hUIEFTICLnu5Pnrpfph43ph48iCgkJCUZST00gVGhvcm5fQmFzZV9Pcmdhbml6YXRpb24gcG9pbnQKCQkJCUxFRlQgSk9JTiBrbXNfb3Bfd2F5YmlsbCBiaWxsIE9OIHBvaW50LmlkID0gYmlsbC5TVEFSVF9QT0lOVF9JRAoJCQkJTEVGVCBKT0lOIEttc19PcF9XYXlCaWxsX0V4dGVuZGVkIGJpbGxFeHRlbmRlZCBPTiBiaWxsRXh0ZW5kZWQuV0FZX0JJTExfSUQgPSBiaWxsLmlkCgkJCQlMRUZUIEpPSU4gVGhvcm5fQmFzZV9Pcmdhbml6YXRpb24gY29tcGFueSBPTiBjb21wYW55LklEID0gYmlsbC5DT01QQU5ZX0lECgkJCQlMRUZUIEpPSU4ga21zX2Jhc2VfcmVnaW9uIHJlZ2lvbiBPTiByZWdpb24uaWQgPSBwb2ludC5SRUdJT05fSUQKCQkJCUxFRlQgSk9JTiBLbXNfQmFzZV9TdGF0aW9uIHNTdGF0aW9uIE9OIHNTdGF0aW9uLmlkID0gYmlsbC5TVEFSVF9TVEFUSU9OX0lECgkJCQlMRUZUIEpPSU4gS21zX0Jhc2VfU3RhdGlvbiBjaGFuZ2VTdGF0aW9uIE9OIGNoYW5nZVN0YXRpb24uaWQgPSBiaWxsLlRSQU5TSVRfU1RBVElPTl9JRAoJCQlXSEVSRSAxID0gMQoJCQkJQU5EIGJpbGwuU1RBVFVTIDw+ICdXJyAtLSAgIEFORCAoREFURV9GT1JNQVQoYmlsbC5PUkRFUl9EQVRFLCclWS0lbS0lZCAlSDolaScpID49REFURV9GT1JNQVQoc1RpbWUsJyVZLSVtLSVkICVIOiVpJykgT1Igc1RpbWUgSVMgTlVMTCApCgkJCQktLSAgICAgIEFORCAoREFURV9GT1JNQVQoYmlsbC5PUkRFUl9EQVRFLCclWS0lbS0lZCAlSDolaScpIDw9REFURV9GT1JNQVQoZVRpbWUsJyVZLSVtLSVkICVIOiVpJykgT1IgZVRpbWUgSVMgTlVMTCApCgkJCQlBTkQgKHNUaW1lIElTIE5VTEwKCQkJCQlPUiBiaWxsLk9SREVSX0RBVEUgPj0gc1RpbWUpCgkJCQlBTkQgKGVUaW1lIElTIE5VTEwKCQkJCQlPUiBiaWxsLk9SREVSX0RBVEUgPD0gZVRpbWUpIC0tICAgICAgICAgQU5EIChiaWxsLkNSRUFUT1I9b3BlcmF0aW9uTWFuIG9yIG9wZXJhdGlvbk1hbiAgaXMgTlVMTCkKCQkJCUFORCAoY29tcGFueUlkIElTIE5VTEwKCQkJCQlPUiBGSU5EX0lOX1NFVChjb21wYW55LklELCAoCgkJCQkJCVNFTEVDVCB0Ym8xLk9SR19MSVNUCgkJCQkJCUZST00gdGhvcm5fYmFzZV9vcmdhbml6YXRpb24gdGJvMQoJCQkJCQlXSEVSRSB0Ym8xLklEID0gY29tcGFueUlkCgkJCQkJKSkKCQkJCQlBTkQgcG9pbnQuQ09NUEFOWV9JRCA9IGNvbXBhbnlJZCkKCQkpIHQxCgkJCUxFRlQgSk9JTiAoCgkJCQlTRUxFQ1QgYXBwbGUuQVBQTEVfT0JKRUNUX0lEIEFTIGlkMiwgYXBwbGUuQUREX0NIQU5HRV9GRUUgQVMgIumdnuaPkOS7mOW8guWKqOmHkeminSIKCQkJCUZST00gS01TX09QX0NIQU5HRV9BUFBMRSBhcHBsZQoJCQkJV0hFUkUgYXBwbGUuVFlQRSA9ICdZRCcKCQkJCQlBTkQgYXBwbGUuUEFZX0NVU1RPTUVSID0gJ1AnCgkJCQkJQU5EIGFwcGxlLmBTVEFUVVNgID0gJ1pYJwoJCQkpIHQyCgkJCU9OIHQxLmlkMSA9IHQyLmlkMjsKCUVORCBJRjsKRU5EOw==\n"
            + "# POLARX_TSO=\n"
            + "# POLARX_DDL_ID=0\n";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "", false, null);
        Assert.assertEquals(expected, sb.toString());
    }

    @Test
    public void testNewlineEscape() {
        String sql = "create table t1 \n "
            + "(id bigint comment 'ssdd\ndddd' \n,"
            + "name varchar(100) comment 'uiui\nwerw' \n,"
            + " primary key(id) \n"
            + ")";
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
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
    public void testTryRemoveDropIndex() {
        String dropIndexSql1 = "drop index auto_shard_key_xx on t1";
        String dropIndexSql2 = "alter table t1 drop index auto_shard_key_xx";
        String dropIndexSql3 = "drop index idx on t1";
        String dropIndexSql4 = "alter table t1 drop index idx";

        Set<String> suppressAutoShard = new HashSet<>();
        suppressAutoShard.add("auto_shard_key_xx");
        Set<String> suppressIdx = new HashSet<>();
        suppressIdx.add("idx");
        Set<String> empty = new HashSet<>();

        // 场景1: 索引在 suppress 集合中 → 抑制
        Assert.assertNull("DROP INDEX 在 suppress 集合中应返回 null",
            DDLConverter.tryRemoveDropIndex(dropIndexSql1, suppressAutoShard));
        Assert.assertEquals("ALTER TABLE 中 DROP 项被移除后保留为空 ALTER TABLE（MySQL 接受为 no-op）",
            "ALTER TABLE t1", DDLConverter.tryRemoveDropIndex(dropIndexSql2, suppressAutoShard));

        // 场景2: 索引不在 suppress 集合中 → 正常透传
        Assert.assertEquals(dropIndexSql1, DDLConverter.tryRemoveDropIndex(dropIndexSql1, empty));
        Assert.assertEquals(dropIndexSql2, DDLConverter.tryRemoveDropIndex(dropIndexSql2, empty));

        // 场景3: 普通索引不在 suppress 集合中 → 正常透传
        Assert.assertEquals(dropIndexSql3, DDLConverter.tryRemoveDropIndex(dropIndexSql3, suppressAutoShard));
        Assert.assertEquals(dropIndexSql4, DDLConverter.tryRemoveDropIndex(dropIndexSql4, suppressAutoShard));

        // 场景4: 普通索引在 suppress 集合中 → 抑制
        Assert.assertNull(DDLConverter.tryRemoveDropIndex(dropIndexSql3, suppressIdx));
        Assert.assertEquals("ALTER TABLE 中 DROP 项被移除后保留为空 ALTER TABLE",
            "ALTER TABLE t1", DDLConverter.tryRemoveDropIndex(dropIndexSql4, suppressIdx));

        // 场景5: null/empty 集合 → 原样返回
        Assert.assertEquals(dropIndexSql1, DDLConverter.tryRemoveDropIndex(dropIndexSql1, null));
        Assert.assertEquals(dropIndexSql1, DDLConverter.tryRemoveDropIndex(dropIndexSql1, new HashSet<>()));

        // 场景6: extractDroppedIndexNames 正确提取索引名
        Set<String> names1 = DDLConverter.extractDroppedIndexNames(dropIndexSql1);
        Assert.assertTrue(names1.contains("auto_shard_key_xx"));
        Set<String> names2 = DDLConverter.extractDroppedIndexNames(dropIndexSql2);
        Assert.assertTrue(names2.contains("auto_shard_key_xx"));
        Set<String> names3 = DDLConverter.extractDroppedIndexNames("select 1");
        Assert.assertTrue(names3.isEmpty());
    }

    @Test
    public void testAlterTableGroupWithLocality() {
        String sql = "ALTER TABLEGROUP tg2241 SPLIT PARTITION pd INTO ("
            + "PARTITION p3 VALUES IN (1003) LOCALITY 'dn=xdevelop-240518031954-c338-dsj4-dn-0' SUBPARTITIONS 2, "
            + "PARTITION `pd` VALUES IN (DEFAULT) "
            + "( SUBPARTITION `pdsp1`, SUBPARTITION `pdsp2`, SUBPARTITION `pdsp3`, SUBPARTITION `pdsp4` ))";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", false, null);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=ALTER TABLEGROUP tg2241 SPLIT PARTITION pd INTO (PARTITION p3 VALUES IN (1003) SUBPARTITIONS 2, PARTITION `pd` VALUES IN (DEFAULT) ( SUBPARTITION `pdsp1`, SUBPARTITION `pdsp2`, SUBPARTITION `pdsp3`, SUBPARTITION `pdsp4` )) \n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n", sb.toString());
    }

    @Test
    public void testAlterCciTable() {
        String sql =
            "/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1505 IMPLICIT";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", true, null);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1505 IMPLICIT\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n"
                + "# POLARX_DDL_TYPES=CCI\n", sb.toString());
    }

    @Test
    public void testAlterCciTableWithVariables() {
        String sql =
            "/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1505 IMPLICIT";
        StringBuilder sb = new StringBuilder();
        Map<String, Object> variables = new HashMap<>();
        variables.put("FP_OVERRIDE_NOW", "2024-08-18 10:10:10");
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", true, variables);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1505 IMPLICIT\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n"
                + "# POLARX_DDL_TYPES=CCI\n"
                + "# POLARX_VARIABLES={\"FP_OVERRIDE_NOW\":\"2024-08-18 10:10:10\"}\n", sb.toString());
    }

    @Test
    public void testAlterLocalTable() {
        String sql =
            "ALTER TABLE t_ttl_single\n"
                + "LOCAL PARTITION BY RANGE (gmt_modified)\n"
                + "STARTWITH '2023-08-20'\n"
                + "INTERVAL 1 MONTH\n"
                + "EXPIRE AFTER 1\n"
                + "PRE ALLOCATE 3\n"
                + "PIVOTDATE now()";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8_general_cs", "", true, null);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=ALTER TABLE t_ttl_single  LOCAL PARTITION BY RANGE (gmt_modified)  STARTWITH '2023-08-20'  INTERVAL 1 MONTH  EXPIRE AFTER 1  PRE ALLOCATE 3  PIVOTDATE now()\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n"
                + "# POLARX_DDL_TYPES=CCI\n", sb.toString());
    }

    /**
     * 测试在创建表时，如果传入的tbCollation与原本表中的charSet冲突，则不会将其补全到表中
     */
    @Test
    public void testCreateTableDoubleTimes() {
        String sql = "create table if not exists `test_charset` (\n"
            + "        `table_name` varchar(45) not null CHARACTER SET 'latin1' COLLATE 'latin1_swedish_ci',\n"
            + "        `table_version` varchar(45) not null default '',\n"
            + "        `data_version` varchar(45) not null default '',\n"
            + "        `last_update_time` bigint,\n"
            + "        primary key (`table_name`)\n"
            + ") engine = 'innodb' default character set = 'utf8'";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8mb4", "utf8mb4_general_ci", "", true, null);
        Assert.assertFalse(sb.toString().contains("utf8mb4_general_ci"));
        sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, sql, "utf8", "utf8_general_ci", "", true, null);
        Assert.assertTrue(sb.toString().contains("utf8_general_ci"));
    }

    @Test
    public void testRebuildCci() {
        String sql1 =
            "/*DDL_ID=7335922456495915072*//*EXTRA_DDL=CREATE CLUSTERED COLUMNAR INDEX `cc22c777-cd7f-4783-8d6f-833bc6c00140` ON `accounts` (`balance`) PARTITION BY HASH (`ID`) PARTITIONS 4 ENGINE = `OSS` COMMENT 'Created by transfer-test'*/";
        String sql2 =
            "/*DDL_ID=7335922456533663808*//*EXTRA_DDL=DROP INDEX `cc22c777-cd7f-4783-8d6f-833bc6c00140` ON `accounts`*/";
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb1, sql1, "utf8mb4", "utf8_general_cs", "", true, null);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=7335922456495915072\n"
                + "# POLARX_DDL_TYPES=CCI\n"
                + "# POLARX_EXTRA_DDL=CREATE CLUSTERED COLUMNAR INDEX `cc22c777-cd7f-4783-8d6f-833bc6c00140` ON `accounts` (`balance`) PARTITION BY HASH (`ID`) PARTITIONS 4 ENGINE = `OSS` COMMENT 'Created by transfer-test'\n",
            sb1.toString());
        buildDdlEventSqlForPolarPart(sb2, sql2, "utf8mb4", "utf8_general_cs", "", true, null);
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=7335922456533663808\n"
                + "# POLARX_DDL_TYPES=CCI\n"
                + "# POLARX_EXTRA_DDL=DROP INDEX `cc22c777-cd7f-4783-8d6f-833bc6c00140` ON `accounts`\n",
            sb2.toString());
    }

    @Test
    public void testCreateTableWithHashOrderIdx() {
        String sql1 = "create table if not exists `hash_order` (\n"
            + "  `id` int, \n"
            + "  `a` int, \n"
            + "  `b` int,\n"
            + "  INDEX `index_a_b` USING HASH(`a`, `b` ASC), \n"
            + "  INDEX `index_id_a` (`id`,`a`), \n"
            + "  INDEX `index_id_b` (`id`,`b` ASC) \n"
            + "\n"
            + ")";
        StringBuilder sb1 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb1, "hash_order", "utf8", "utf8_general_ci", sql1);
        log.info(sb1.toString());
    }

    @Test
    public void testCreateTableWithHashOrderKey() {
        String sql1 = "create table if not exists `hash_order` (\n"
            + "  `id` int, \n"
            + "  `a` int, \n"
            + "  `b` int,\n"
            + "  KEY `index_a_b` USING HASH(`a`, `b` ASC), \n"
            + "  KEY `index_id_a` (`id`,`a`), \n"
            + "  KEY `index_id_b` (`id`,`b` ASC) \n"
            + ")";
        StringBuilder sb1 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb1, "hash_order", "utf8", "utf8_general_ci", sql1);
        log.info(sb1.toString());
    }

    @Test
    public void testCharsetCollationOrder() {
        String ddl = "create table if not exists `collation_order`(\n"
            + "    `id` int,\n"
            + "    `a` int\n"
            + ")COLLATE = utf8mb4_unicode_ci CHARACTER SET = utf8mb4";
        StringBuilder sb1 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb1, "collation_order", "utf8", "utf8_unicode_ci", ddl);
        log.info(sb1.toString());
        Assert.assertEquals(
            "CREATE TABLE IF NOT EXISTS `collation_order` ( `id` int, `a` int ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_ci",
            sb1.toString());
    }

    @Test
    public void testSpecialTableName() {
        String ddl1 = "create table `zimian_p00001`.```omc_column_name``_00001` ("
            + " a int primary key,\n"
            + " b int\n"
            + ") default charset = utf8mb4 default collate = utf8mb4_general_ci";
        String ddl2 =
            "create  table `zimian_p00001`.```omc_column_name``_00001_omc` like `zimian_p00001`.```omc_column_name``_00001`";
        String ddl3 =
            "alter table `zimian_p00001`.```omc_column_name``_00001_omc`  change column b ```c``` bigint not null";
        String ddl4 =
            "create  table `zimian_p00001`.```omc_column_name``_00001_del` (id int auto_increment primary key) engine=innodb comment='omc sentry table of ```omc_column_name``_00001`'";
        String ddl5 =
            "alter  table `zimian_p00001`.```omc_column_name``_00001_omc` add column omc_tmp_qksz tinyint default null, algorithm=instant";
        String ddl6 =
            "alter  table `zimian_p00001`.```omc_column_name``_00001_omc` drop column omc_tmp_qksz, algorithm=instant";
        String ddl7 = "drop table if exists ```omc_column_name``_00001_del`";
        String ddl8 =
            "rename  table `zimian_p00001`.```omc_column_name``_00001` to `zimian_p00001`.```omc_column_name``_00001_del`, `zimian_p00001`.```omc_column_name``_00001_omc` to `zimian_p00001`.```omc_column_name``_00001`";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "", ddl1, null);
        memoryTableMeta.apply(null, "", ddl2, null);
        memoryTableMeta.apply(null, "", ddl3, null);
        TableMeta meta = memoryTableMeta.find("zimian_p00001", "`omc_column_name`_00001_omc");
        TableMeta.FieldMeta fieldMeta = meta.getFieldMetaByName("`c`", true);
        Assert.assertNotNull(fieldMeta);
        memoryTableMeta.apply(null, "", ddl4, null);
        memoryTableMeta.apply(null, "", ddl5, null);
        memoryTableMeta.apply(null, "", ddl6, null);
        memoryTableMeta.apply(null, "", ddl7, null);
        memoryTableMeta.apply(null, "", ddl8, null);
        meta = memoryTableMeta.find("zimian_p00001", "`omc_column_name`_00001");
        fieldMeta = meta.getFieldMetaByName("`c`", true);
        Assert.assertNotNull(fieldMeta);
    }

    /**
     * DBLE 复制表语法，可以指定locality的表，对mysql过滤
     * <a href="https://aliyuque.antfin.com/coronadb/design/wh5lbx3b722geqkg#0ff7ea86">...</a>
     */
    @Test
    public void testCreateReplicasTable() {
        String ddl = "CREATE TABLE `ofst_tr_floor_pbl` (\n"
            + "    PBL_ID VARCHAR(50) PRIMARY KEY,\n"
            + "    IMG_ID VARCHAR(100),\n"
            + "    PBL_COLOR VARCHAR(20),\n"
            + "    MAIN_TITLE VARCHAR(200),\n"
            + "    SUB_TITLE VARCHAR(200),\n"
            + "    BUTTON_NAME VARCHAR(50),\n"
            + "    ZONE_ID VARCHAR(20),\n"
            + "    BRCH_ID VARCHAR(20),\n"
            + "    STRU_LEVEL VARCHAR(10),\n"
            + "    CREATE_TIME DATETIME,\n"
            + "    MODI_TIME DATETIME,\n"
            + "    POS_NO INT,\n"
            + "    STATUS VARCHAR(1),\n"
            + "    APPROVE_STATUS VARCHAR(10),\n"
            + "    ACT_ID VARCHAR(50),\n"
            + "    PBL_ACT_ID VARCHAR(50),\n"
            + "    BACKOF1 VARCHAR(100),\n"
            + "    BACKOF2 VARCHAR(100),\n"
            + "    BACKOF3 VARCHAR(100),\n"
            + "    BACKOF4 VARCHAR(100),\n"
            + "    HEAD_TOP VARCHAR(10),\n"
            + "    BACKUP2 VARCHAR(10),\n"
            + "    BACKUP3 VARCHAR(10),\n"
            + "    BACKUP4 VARCHAR(10)\n"
            + ") REPLICAS ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_0900_ai_ci;";
        StringBuilder sb1 = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb1, "ofst_tr_floor_pbl", "utf8mb4", "utf8mb4_0900_ai_ci", ddl);
        log.info(sb1.toString());
        Assert.assertEquals(
            "CREATE TABLE `ofst_tr_floor_pbl` ( PBL_ID VARCHAR(50) PRIMARY KEY, IMG_ID VARCHAR(100), PBL_COLOR VARCHAR(20), MAIN_TITLE VARCHAR(200), SUB_TITLE VARCHAR(200), BUTTON_NAME VARCHAR(50), ZONE_ID VARCHAR(20), BRCH_ID VARCHAR(20), STRU_LEVEL VARCHAR(10), CREATE_TIME DATETIME, MODI_TIME DATETIME, POS_NO INT, STATUS VARCHAR(1), APPROVE_STATUS VARCHAR(10), ACT_ID VARCHAR(50), PBL_ACT_ID VARCHAR(50), BACKOF1 VARCHAR(100), BACKOF2 VARCHAR(100), BACKOF3 VARCHAR(100), BACKOF4 VARCHAR(100), HEAD_TOP VARCHAR(10), BACKUP2 VARCHAR(10), BACKUP3 VARCHAR(10), BACKUP4 VARCHAR(10) ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_0900_ai_ci;",
            sb1.toString());
    }

    /**
     * 测试PURE_ASYNC_DDL_MODE hint在PolarX部分被正确去掉
     */
    @Test
    public void testRemoveAsyncDdlHintForPolarPart() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        // 带 PURE_ASYNC_DDL_MODE 的 ALTER TABLE ADD INDEX
        String ddl = "/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=true)*/ALTER TABLE t1 ADD INDEX idx_col1 (col1)";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "222222", false, null);
        String result = sb.toString();
        Assert.assertFalse("PolarX part should not contain PURE_ASYNC_DDL_MODE",
            result.contains("PURE_ASYNC_DDL_MODE"));
        Assert.assertTrue(result.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(result.contains("# POLARX_TSO=222222"));
    }

    /**
     * 测试PURE_ASYNC_DDL_MODE hint在MySQL部分被正确去掉
     */
    @Test
    public void testRemoveAsyncDdlHintForMysqlPart() {
        // 带 PURE_ASYNC_DDL_MODE 的 ALTER TABLE ADD INDEX
        String ddl = "/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=true)*/ALTER TABLE t1 ADD INDEX idx_col1 (col1)";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t1", "utf8mb4", "utf8_general_cs", ddl);
        String result = sb.toString();
        Assert.assertFalse("MySQL part should not contain PURE_ASYNC_DDL_MODE",
            result.contains("PURE_ASYNC_DDL_MODE"));
        Assert.assertTrue("MySQL part should contain ALTER TABLE",
            result.toUpperCase().contains("ALTER TABLE"));
    }

    /**
     * 测试PURE_ASYNC_DDL_MODE hint混合其他hint时只移除PURE_ASYNC_DDL_MODE
     */
    @Test
    public void testRemoveAsyncDdlHintMixedWithOtherHints() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        String ddl = "/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=true, ALLOW_ADD_GSI=TRUE)*/"
            + "ALTER TABLE t1 ADD INDEX idx_col1 (col1)";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "333333", false, null);
        String result = sb.toString();
        Assert.assertFalse("Should not contain PURE_ASYNC_DDL_MODE",
            result.contains("PURE_ASYNC_DDL_MODE"));
        Assert.assertTrue("Should still contain ALLOW_ADD_GSI",
            result.contains("ALLOW_ADD_GSI"));
    }

    /**
     * 测试async=true标志在MySQL部分被正确去掉
     */
    @Test
    public void testRemoveAsyncFlagForMysqlPart() {
        String ddl = "ALTER TABLE t1 ADD INDEX idx_col1 (col1) async=true";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "t1", "utf8mb4", "utf8_general_cs", ddl);
        String result = sb.toString();
        Assert.assertFalse("MySQL part should not contain async",
            result.toLowerCase().contains("async"));
    }

    /**
     * 测试async=true标志在PolarX部分被正确去掉
     */
    @Test
    public void testRemoveAsyncFlagForPolarPart() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        String ddl = "ALTER TABLE t1 ADD INDEX idx_col1 (col1) async=true";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForPolarPart(sb, ddl, "utf8mb4", "utf8_general_cs", "444444", false, null);
        String result = sb.toString();
        Assert.assertFalse("PolarX part should not contain async=true",
            result.toLowerCase().contains("async"));
    }

    /**
     * 测试removeAsyncDdlFlags直接方法
     */
    @Test
    public void testRemoveAsyncDdlFlags() {
        // ALTER TABLE with async=true
        String sql = "ALTER TABLE t1 ADD INDEX idx_col1 (col1) async=true";
        com.alibaba.polardbx.druid.sql.ast.SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        removeAsyncDdlFlags(stmt);
        String result = stmt.toString();
        Assert.assertFalse("Should not contain async", result.toLowerCase().contains("async"));

        // CREATE INDEX with async=true
        sql = "CREATE INDEX idx_col1 ON t1 (col1) async=true";
        stmt = SQLUtils.parseSQLStatement(sql);
        removeAsyncDdlFlags(stmt);
        result = stmt.toString();
        Assert.assertFalse("Should not contain async", result.toLowerCase().contains("async"));
    }

    /**
     * 测试完整的buildDdlEventSql同时去掉hint和async标志
     */
    @Test
    public void testBuildDdlEventSqlRemovesAsyncDdl() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        // 同时带有 PURE_ASYNC_DDL_MODE hint 和 async=true
        String ddlForPolar =
            "/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=true)*/ALTER TABLE t1 ADD INDEX idx_col1 (col1) async=true";
        String ddlForMysql = "ALTER TABLE t1 ADD INDEX idx_col1 (col1) async=true";
        String result = buildDdlEventSql("t1", ddlForPolar, "utf8mb4", "utf8_general_cs",
            "555555", ddlForMysql);
        Assert.assertFalse("Should not contain PURE_ASYNC_DDL_MODE",
            result.contains("PURE_ASYNC_DDL_MODE"));
        Assert.assertFalse("Should not contain async",
            StringUtils.containsIgnoreCase(result, "async=true"));
        Assert.assertTrue("Should contain POLARX_ORIGIN_SQL",
            result.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue("Should contain ALTER TABLE",
            result.toUpperCase().contains("ALTER TABLE"));
    }

    /**
     * 测试不带async属性的正常DDL不受影响
     */
    @Test
    public void testNormalDdlNotAffected() {
        mockConfig(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED, "true");

        String ddl = "ALTER TABLE t1 ADD INDEX idx_col1 (col1)";
        String result = buildDdlEventSql("t1", ddl, "utf8mb4", "utf8_general_cs", "666666", ddl);
        Assert.assertTrue("Should contain ALTER TABLE",
            result.toUpperCase().contains("ALTER TABLE"));
        Assert.assertTrue(result.contains("# POLARX_ORIGIN_SQL="));
    }

    /**
     * 测试 VECTOR 列类型转换为 VARBINARY 以及 VECTOR INDEX 的移除
     */
    @Test
    public void testVectorDdlConversion() {
        // Case 1: CREATE TABLE - VECTOR(128) 转换为 VARBINARY(512)，VECTOR INDEX 被移除
        StringBuilder sb = new StringBuilder();
        String ddl = "CREATE TABLE test_vector (\n"
            + "  id bigint NOT NULL,\n"
            + "  embedding VECTOR(128),\n"
            + "  PRIMARY KEY (id),\n"
            + "  VECTOR INDEX vec_idx (embedding) DISTANCE=COSINE M=16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4";
        buildDdlEventSqlForMysqlPart(sb, "test_vector", "utf8mb4", "utf8mb4_general_ci", ddl);
        String result = sb.toString();
        Assert.assertTrue("VECTOR 列应被转换为 VARBINARY(512)", result.contains("VARBINARY(512)"));
        Assert.assertFalse("输出中不应包含 VECTOR(128) 类型", result.toUpperCase().contains("VECTOR(128)"));
        Assert.assertFalse("VECTOR INDEX 应被移除", result.toUpperCase().contains("VECTOR INDEX"));

        // Case 2: ALTER TABLE ADD COLUMN VECTOR(64) → VARBINARY(256)
        sb = new StringBuilder();
        ddl = "ALTER TABLE test_vector ADD COLUMN vec VECTOR(64)";
        buildDdlEventSqlForMysqlPart(sb, "test_vector", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals(
            "ALTER TABLE test_vector ADD COLUMN vec VARBINARY(256)",
            sb.toString());

        // Case 3: ALTER TABLE MODIFY COLUMN VECTOR(128) → VARBINARY(512)
        sb = new StringBuilder();
        ddl = "ALTER TABLE test_vector MODIFY COLUMN embedding VECTOR(128)";
        buildDdlEventSqlForMysqlPart(sb, "test_vector", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals(
            "ALTER TABLE test_vector MODIFY COLUMN embedding VARBINARY(512)",
            sb.toString());

        // Case 4: ALTER TABLE ADD VECTOR INDEX → 索引项被移除，剩余空 ALTER TABLE（MySQL 接受为 no-op）
        sb = new StringBuilder();
        ddl = "ALTER TABLE test_vector ADD VECTOR INDEX vec_idx (embedding) DISTANCE=COSINE M=16";
        buildDdlEventSqlForMysqlPart(sb, "test_vector", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals("ALTER TABLE test_vector", sb.toString());

        // Case 5: CREATE VECTOR INDEX → 整个语句被抑制，MySQL 输出为空
        sb = new StringBuilder();
        ddl = "CREATE VECTOR INDEX vec_idx ON test_vector (embedding) DISTANCE=COSINE M=16";
        buildDdlEventSqlForMysqlPart(sb, "test_vector", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals("CREATE VECTOR INDEX 在 MySQL 输出中应为空", "", sb.toString());
    }

    /**
     * 端到端生命周期测试：使用真实 MemoryTableMeta 模拟 VECTOR INDEX 从建表到 DROP INDEX 的完整流程。
     * <p>
     * 此测试模拟 LogicDDLHandler 的真实行为：
     * 1. 用 MemoryTableMeta 维护源端 schema（模拟 CDC 内部元数据）
     * 2. 回调函数从 MemoryTableMeta 读取索引信息（而非硬编码）
     * 3. 验证 indexExistenceChecker 对 VECTOR 类型索引返回 false（核心修复逻辑）
     */
    @Test
    public void testVectorIndexLifecycle() {
        // ========== 初始化：用真实 MemoryTableMeta 维护 schema ==========
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log, true);
        String schema = "vec_test_ddl";

        // ========== Step 1: CREATE TABLE 带 VECTOR 列和 VECTOR INDEX ==========
        String createTableDdl = "CREATE TABLE test_lifecycle (\n"
            + "  id bigint NOT NULL,\n"
            + "  embedding VECTOR(128),\n"
            + "  description varchar(255),\n"
            + "  PRIMARY KEY (id),\n"
            + "  VECTOR INDEX vec_idx_lifecycle (embedding) DISTANCE=COSINE M=16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4";

        // 对 MySQL 下游输出（strip VECTOR）
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "test_lifecycle", "utf8mb4", "utf8mb4_general_ci", createTableDdl);
        String createResult = sb.toString();
        Assert.assertTrue("Step1: VECTOR(128) 应转换为 VARBINARY(512)", createResult.contains("VARBINARY(512)"));
        Assert.assertFalse("Step1: VECTOR INDEX 应被移除", createResult.toUpperCase().contains("VECTOR INDEX"));

        // 同时 apply 原始 DDL 到 CDC 内部元数据（模拟 doApplyAndRebuildFilter）
        memoryTableMeta.apply(null, schema, createTableDdl, null);

        // 验证元数据中确实存储了 VECTOR INDEX
        TableMeta tableMeta = memoryTableMeta.find(schema, "test_lifecycle");
        Assert.assertNotNull("元数据应能找到表", tableMeta);
        TableMeta.IndexMeta indexMeta = tableMeta.getIndexes().get("vec_idx_lifecycle");
        Assert.assertNotNull("元数据中应存在 vec_idx_lifecycle 索引", indexMeta);
        Assert.assertEquals("索引类型应为 VECTOR", "VECTOR", indexMeta.getIndexType());

        // ========== Step 2: CREATE VECTOR INDEX → MySQL 输出被抑制 ==========
        sb = new StringBuilder();
        String createVectorIndexDdl =
            "CREATE VECTOR INDEX vec_idx_lifecycle ON test_lifecycle (embedding) DISTANCE=COSINE M=16";
        buildDdlEventSqlForMysqlPart(sb, "test_lifecycle", "utf8mb4", "utf8mb4_general_ci", createVectorIndexDdl);
        Assert.assertEquals("Step2: CREATE VECTOR INDEX 应被完全抑制", "", sb.toString());

        // ========== Step 3: DROP INDEX — 与 LogicDDLHandler.resolveIndexesToSuppress 一致的逻辑 ==========
        String dropIndexSql = "drop index vec_idx_lifecycle on test_lifecycle";
        Set<String> indexesToSuppress =
            resolveIndexesToSuppress(memoryTableMeta, schema, "test_lifecycle", dropIndexSql);
        String dropResult = DDLConverter.tryRemoveDropIndex(dropIndexSql, indexesToSuppress);
        Assert.assertNull("Step3: VECTOR INDEX DROP 应被抑制", dropResult);

        // ALTER TABLE DROP INDEX 形式
        String alterDropIndexSql = "alter table test_lifecycle drop index vec_idx_lifecycle";
        indexesToSuppress = resolveIndexesToSuppress(memoryTableMeta, schema, "test_lifecycle", alterDropIndexSql);
        String alterDropResult = DDLConverter.tryRemoveDropIndex(alterDropIndexSql, indexesToSuppress);
        Assert.assertEquals("Step3: ALTER TABLE 中 VECTOR DROP 项被移除（MySQL 接受空 ALTER TABLE）",
            "ALTER TABLE test_lifecycle", alterDropResult);

        // ========== 对照组: 普通索引不应被抑制 ==========
        memoryTableMeta.apply(null, schema,
            "CREATE INDEX normal_idx ON test_lifecycle (description)", null);
        TableMeta tableMetaAfter = memoryTableMeta.find(schema, "test_lifecycle");
        Assert.assertNotNull("普通索引应存在于元数据", tableMetaAfter.getIndexes().get("normal_idx"));

        String dropNormalIndexSql = "drop index normal_idx on test_lifecycle";
        indexesToSuppress = resolveIndexesToSuppress(memoryTableMeta, schema, "test_lifecycle", dropNormalIndexSql);
        String normalDropResult = DDLConverter.tryRemoveDropIndex(dropNormalIndexSql, indexesToSuppress);
        Assert.assertEquals("对照组: 普通索引 DROP 不应被抑制", dropNormalIndexSql, normalDropResult);
    }

    /**
     * 模拟 LogicDDLHandler.resolveIndexesToSuppress 的判断逻辑。
     */
    private Set<String> resolveIndexesToSuppress(MemoryTableMeta meta, String schema, String table, String sql) {
        Set<String> toSuppress = new HashSet<>();
        for (String indexName : DDLConverter.extractDroppedIndexNames(sql)) {
            if (DDLConverter.isAutoShardKey(indexName)) {
                boolean existsInMeta = meta.find(schema, table).getIndexes().keySet().stream()
                    .anyMatch(i -> StringUtils.equalsIgnoreCase(indexName, i));
                if (!existsInMeta) {
                    toSuppress.add(indexName);
                }
                continue;
            }
            TableMeta tableMeta = meta.find(schema, table);
            if (tableMeta != null) {
                TableMeta.IndexMeta idx = tableMeta.getIndexes().get(indexName);
                if (idx != null && "VECTOR".equalsIgnoreCase(idx.getIndexType())) {
                    toSuppress.add(indexName);
                }
            }
        }
        return toSuppress;
    }

    @Test
    public void testExternalizeKeywordStrippedInCreateTable() {
        String ddl = "CREATE TABLE `ext_test` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `name` varchar(64),\n"
            + "  `content` LONGTEXT EXTERNALIZE,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "ext_test", "utf8mb4", "utf8mb4_general_ci", ddl);
        String result = sb.toString();
        Assert.assertFalse("EXTERNALIZE keyword should be stripped", result.contains("EXTERNALIZE"));
        Assert.assertTrue("LONGTEXT column should remain", result.contains("LONGTEXT"));
        Assert.assertTrue("column name should remain", result.contains("`content`"));
    }

    @Test
    public void testExternalizeKeywordStrippedInAlterAddColumn() {
        String ddl = "ALTER TABLE `ext_test` ADD COLUMN `body` LONGTEXT EXTERNALIZE";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "ext_test", "utf8mb4", "utf8mb4_general_ci", ddl);
        String result = sb.toString();
        Assert.assertFalse("EXTERNALIZE keyword should be stripped", result.contains("EXTERNALIZE"));
        Assert.assertTrue("LONGTEXT column should remain", result.contains("LONGTEXT"));
        Assert.assertTrue("column name should remain", result.contains("`body`"));
    }

    @Test
    public void testExternalizeKeywordStrippedInAlterModifyColumn() {
        String ddl = "ALTER TABLE `ext_test` MODIFY COLUMN `content` LONGBLOB EXTERNALIZE";
        StringBuilder sb = new StringBuilder();
        buildDdlEventSqlForMysqlPart(sb, "ext_test", "utf8mb4", "utf8mb4_general_ci", ddl);
        String result = sb.toString();
        Assert.assertFalse("EXTERNALIZE keyword should be stripped", result.contains("EXTERNALIZE"));
        Assert.assertTrue("LONGBLOB column should remain", result.contains("LONGBLOB"));
    }

    @Test
    public void testAddCharacterToGenerateColumn() {
        /*
         test generated column with character set
         */
        StringBuilder sb = new StringBuilder();
        String ddl = "ALTER TABLE fi3 ADD COLUMN `func_index$0` CHAR(16) CHARACTER SET UTF8MB4 "
            + "GENERATED ALWAYS AS (SUBSTRING(col3, 1, 2)), ADD UNIQUE INDEX func_index (`func_index$0`)";
        buildDdlEventSqlForMysqlPart(sb, "fi3", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals(
            "ALTER TABLE fi3 ADD COLUMN `func_index$0` CHAR(16) CHARACTER SET UTF8MB4, ADD UNIQUE INDEX func_index (`func_index$0`)",
            sb.toString());
    }

    @Test
    public void testModifyCharacterToGenerateColumn() {
        /*
         test modify column with character set on generated column
         */
        StringBuilder sb = new StringBuilder();
        String ddl = "ALTER TABLE fi3 MODIFY COLUMN `func_index$0` VARCHAR(32) CHARACTER SET UTF8MB4 "
            + "GENERATED ALWAYS AS (SUBSTRING(col3, 1, 4))";
        buildDdlEventSqlForMysqlPart(sb, "fi3", "utf8mb4", "utf8mb4_general_ci", ddl);
        Assert.assertEquals(
            "ALTER TABLE fi3 MODIFY COLUMN `func_index$0` VARCHAR(32) CHARACTER SET UTF8MB4",
            sb.toString());
    }
}
