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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier.DependencyCheckResult.OBJ_TYPE_SEQ;
import static com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier.DependencyCheckResult.OBJ_TYPE_TABLE;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-11-23 10:01
 **/
public class ParallelSchemaApplierTest extends BaseTest {

    @Test
    public void testIsSyncPoint() {
        ParallelSchemaApplier applier = new ParallelSchemaApplier() {
            @Override
            protected void initMaxDdlTsoCheckPoint() {
            }
        };

        String syncPointOriginalDdl = "CALL trigger_sync_point_trx()";
        Assert.assertTrue(applier.isSyncPoint(syncPointOriginalDdl, "polardbx"));
        Assert.assertFalse(applier.isSyncPoint(syncPointOriginalDdl, "d1"));

        String otherProcedure = "CALL some_procedure()";
        Assert.assertFalse(applier.isSyncPoint(otherProcedure, "polardbx"));
    }

    @Test
    public void testIsCrossDatabase() {
        ParallelSchemaApplier applier = new ParallelSchemaApplier() {
            @Override
            protected void initMaxDdlTsoCheckPoint() {
            }
        };

        // basic
        String sql = "create table t1 like t2";
        boolean result = applier.isCrossDatabase(sql, "d1");
        Assert.assertFalse(result);

        sql = "create table tz(id bigint primary key, name varchar(200))";
        result = applier.isCrossDatabase(sql, "d1");
        Assert.assertFalse(result);

        // target & like has same database name
        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE cp1_ddl2_1057607069.gxw_test";
        result = applier.isCrossDatabase(sql, "cp1_ddl2_1057607069");
        Assert.assertFalse(result);

        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE cp1_ddl2_1057607069.gxw_test";
        result = applier.isCrossDatabase(sql, "d1");
        Assert.assertFalse(result);

        // target & like has different database name
        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE cp1_ddl2_1057607070.gxw_test";
        result = applier.isCrossDatabase(sql, "d1");
        Assert.assertTrue(result);

        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE cp1_ddl2_1057607070.gxw_test";
        result = applier.isCrossDatabase(sql, "cp1_ddl2_1057607069");
        Assert.assertTrue(result);

        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE cp1_ddl2_1057607070.gxw_test";
        result = applier.isCrossDatabase(sql, "cp1_ddl2_1057607070");
        Assert.assertTrue(result);

        // target has no database name & like has database name
        sql = "CREATE TABLE IF NOT EXISTS gxw_test_like LIKE cp1_ddl2_1057607070.gxw_test";
        result = applier.isCrossDatabase(sql, "d1");
        Assert.assertTrue(result);

        sql = "CREATE TABLE IF NOT EXISTS gxw_test_like LIKE cp1_ddl2_1057607070.gxw_test";
        result = applier.isCrossDatabase(sql, "cp1_ddl2_1057607070");
        Assert.assertFalse(result);

        // target has database name & like has no database name
        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE gxw_test";
        result = applier.isCrossDatabase(sql, "d1");
        Assert.assertTrue(result);

        sql = "CREATE TABLE IF NOT EXISTS cp1_ddl2_1057607069.gxw_test_like LIKE gxw_test";
        result = applier.isCrossDatabase(sql, "cp1_ddl2_1057607069");
        Assert.assertFalse(result);

        sql = "CREATE TABLE test_table_like LIKE test_like_database_2.`test_table`";
        result = applier.isCrossDatabase(sql, "test_like_database");
        Assert.assertTrue(result);
    }

    @Test
    public void testCheckDependency() {
        ParallelSchemaApplier applier = new ParallelSchemaApplier() {
            @Override
            protected void initMaxDdlTsoCheckPoint() {
            }
        };

        // check create sql with foreign key
        String sql = "  CREATE TABLE `test_Hash_tc` (\n"
            + "  `id` int(11) NOT NULL,\n"
            + "  `name` varchar(30) DEFAULT NULL,\n"
            + "  `create_time` datetime DEFAULT NULL,\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  KEY `auto_shard_key_ID` (`id`),\n"
            + "  FOREIGN KEY fk(`name`) REFERENCES test_hash_Ta (`name`) ON DELETE CASCADE\n"
            + "  ) ENGINE=InnoDB DEFAULT CHARSET=utf8 dbpartition by hash(`ID`)";
        ParallelSchemaApplier.DependencyCheckResult result = applier.checkDependency(sql);
        checkAfterCheckDependency("test_hash_tc", OBJ_TYPE_TABLE,
            Sets.newHashSet(Pair.of(OBJ_TYPE_TABLE, "test_hash_ta")), true, result);

        // check create shadow table
        sql = "CREATE SHADOW TABLE __test_truncate_gsi_test_7 (\n"
            + "  id int PRIMARY KEY,\n"
            + "  name varchar(20),\n"
            + "  GLOBAL INDEX __test_g_i_truncate_test_7(name) DBPARTITION BY hash(name)\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY hash(id)";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);

        // check drop table
        sql = "drop table tXyz";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("txyz", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check drop table with backtick
        sql = "DROP TABLE IF EXISTS ```gxw_test-minus```";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("`gxw_test-minus`", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check sql with create index
        sql = "CREATE GLOBAL INDEX `g_i_seller` ON t_orDer (`seller_id`) COVERING (order_snapshot) "
            + "dbpartition by hash(`seller_id`)";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("t_order", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check sql with drop index
        sql = "DROP INDEX g_i_seller on t_Order";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("t_order", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check alter sql with foreign key
        sql = "alter table `Device` add foreign key (`d`) REFERENCES `user20` (`c`)";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("device", OBJ_TYPE_TABLE,
            Sets.newHashSet(Pair.of(OBJ_TYPE_TABLE, "user20")), true, result);

        // check alter sql with rename index
        sql = "ALTER TABLE t_order_gsi1\n"
            + "  RENAME INDEX g_i_buyer TO g_i_buyer_target";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("t_order_gsi1", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check alter sql with alter index
        sql = "alter table tb1 alter index g1 invisible";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("tb1", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check alter index on table
        sql = "alter index gsi_lc_rc on table lc_rc_tp1\n"
            + "  rename subpartition sp4 to sp0, sp3 to sp1";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("lc_rc_tp1", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);

        // check alter table set tablegroup
        sql = "ALTER TABLE tb5 SET tablegroup = mytg1 FORCE";
        result = applier.checkDependency(sql, false);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);

        // check create sequence
        sql = "create sequence drds_polarx2_qatest_app.simple_seq_test_xiaoying_a0qe "
            + "start with 99999999996 maxvalue 99999999999";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("simple_seq_test_xiaoying_a0qe", OBJ_TYPE_SEQ, Sets.newHashSet(), true, result);

        // check drop sequence
        sql = "drop sequence drds_polarx2_part_qatest_app.create_sequence_test5_58hp";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("create_sequence_test5_58hp", OBJ_TYPE_SEQ, Sets.newHashSet(), true, result);

        // check alter sequence
        sql = "alter sequence drds_polarx2_part_qatest_app.alter_seq_test1_vnft start with 9223372036854775807";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("alter_seq_test1_vnft", OBJ_TYPE_SEQ, Sets.newHashSet(), true, result);

        // check rename sequence
        sql = "rename sequence rename_seq_test_old1_b6fh to rename_seq_test_new2_rznx";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);

        // check convert all sequence
        sql = "convert all sequences from NEW to GROUP for seq_change_test";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);

        // check truncate table
        sql = "truncate table drds_polarx2_qatest_app.tab_batch_time_seq_virf";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("tab_batch_time_seq_virf", OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);
        sql = "truncate table xxx,yyy";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);

        // check analyze table
        sql = "analyze table drds_polarx1_qatest_app.update_delete_base_autonic_multi_db_multi_tb";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("update_delete_base_autonic_multi_db_multi_tb",
            OBJ_TYPE_TABLE, Sets.newHashSet(), true, result);
        sql = "analyze table xxx, yyy";
        result = applier.checkDependency(sql);
        checkAfterCheckDependency("", "", Sets.newHashSet(), false, result);
    }

    private void checkAfterCheckDependency(String expectObjName, String expectObjType,
                                           Set<Pair<String, String>> expectDependencyObjs, boolean expectParallel,
                                           ParallelSchemaApplier.DependencyCheckResult result) {
        Assert.assertEquals(expectObjName, result.getObjName());
        Assert.assertEquals(expectObjType, result.getObjType());
        Assert.assertEquals(expectDependencyObjs, result.getDependencyObjs());
        Assert.assertEquals(expectParallel, result.isParallelPossibility());
    }
}
