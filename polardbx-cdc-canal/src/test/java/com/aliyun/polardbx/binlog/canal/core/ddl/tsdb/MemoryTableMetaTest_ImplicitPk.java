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
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MemoryTableMetaTest_ImplicitPk extends MemoryTableMetaBase {

    @Test
    public void testImplicitKey() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, true);
        String sql = "CREATE TABLE `tiny_int_one_db_multi_tb` (\n"
            + "\t`tinyintr` tinyint(4) NOT NULL,\n"
            + "\t`tinyintr_1` tinyint(1) DEFAULT NULL,\n"
            + "\t`tinyintr_3` tinyint(3) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`_drds_implicit_id_`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = gbk";
        String sql2 = memoryTableMeta.tryRepairSql(sql);
        Assert.assertEquals("create table `tiny_int_one_db_multi_tb` (\n"
            + "\t`tinyintr` tinyint(4) not null,\n"
            + "\t`tinyintr_1` tinyint(1) default null,\n"
            + "\t`tinyintr_3` tinyint(3) default null,\n"
            + "\tprimary key (`_drds_implicit_id_`),\n"
            + "\t_drds_implicit_id_ bigint(20) auto_increment\n"
            + ") engine = InnoDB default charset = gbk", sql2);
    }

    @Test
    public void testDropImplicitPk() {
        String sql1 = "CREATE TABLE modify_sk_simple_checker_test_tblPF (\n"
            + "\ta int,\n"
            + "\tb int,\n"
            + "\tINDEX `auto_shard_key_a` USING BTREE(`A`),\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "\tPRIMARY KEY (_drds_implicit_id_)\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`\n"
            + "PARTITION BY HASH (`a`) PARTITIONS 3";
        String sql2 = "alter table modify_sk_simple_checker_test_tblPF add primary key(a)";
        String sql3 = "alter table modify_sk_simple_checker_test_tblPF drop column _drds_implicit_id_";
        testDropImplicitPkInternal1(sql1, sql2, sql3);

        String sql5 = "create table modify_sk_simple_checker_test_tblPF (a int, b int) "
            + "partition by hash(`a`) partitions 3";
        String sql6 = "/*+TDDL:CMD_EXTRA(GSI_BACKFILL_USE_FASTCHECKER=FALSE)*/"
            + "alter table modify_sk_simple_checker_test_tblPF add primary key(a)";
        testDropImplicitPkInternal2(sql5, sql6);
    }

    private void testDropImplicitPkInternal1(String sql1, String sql2, String sql3) {
        MemoryTableMeta m = newMemoryTableMeta();
        applySql(m, "d1", sql1);
        TableMeta tableMeta = m.find("d1", "modify_sk_simple_checker_test_tblPF");
        Assert.assertEquals(1, tableMeta.getPrimaryFields().size());
        Assert.assertEquals("_drds_implicit_id_", tableMeta.getPrimaryFields().get(0).getColumnName());

        applySql(m, "d1", sql2);
        tableMeta = m.find("d1", "modify_sk_simple_checker_test_tblPF");
        Assert.assertEquals(1, tableMeta.getPrimaryFields().size());
        Assert.assertEquals("a", tableMeta.getPrimaryFields().get(0).getColumnName());

        applySql(m, "d1", sql3);
        tableMeta = m.find("d1", "modify_sk_simple_checker_test_tblPF");
        Assert.assertEquals(1, tableMeta.getPrimaryFields().size());
        Assert.assertEquals("a", tableMeta.getPrimaryFields().get(0).getColumnName());
    }

    private void testDropImplicitPkInternal2(String sql1, String sql2) {
        MemoryTableMeta m = newMemoryTableMeta();
        applySql(m, "d1", sql1);
        TableMeta tableMeta = m.find("d1", "modify_sk_simple_checker_test_tblPF");
        Assert.assertEquals(0, tableMeta.getPrimaryFields().size());

        applySql(m, "d1", sql2);
        tableMeta = m.find("d1", "modify_sk_simple_checker_test_tblPF");
        Assert.assertEquals(1, tableMeta.getPrimaryFields().size());
        Assert.assertEquals("a", tableMeta.getPrimaryFields().get(0).getColumnName());
    }
}
