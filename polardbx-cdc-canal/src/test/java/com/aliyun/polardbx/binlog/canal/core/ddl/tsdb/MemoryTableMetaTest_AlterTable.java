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
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MemoryTableMetaTest_AlterTable extends MemoryTableMetaBase {

    @Test
    public void testChangeColumn() {
        String sql = "create table if not exists `yrxsv7kevh4ce9` (\n"
            + "        `d` int unsigned not null comment 'as',\n"
            + "        `vmk` bigint(1) not null comment '8kenylgzvnb05',\n"
            + "        `w7r1ydbmolofjfm` integer unsigned not null comment 'pwlqfhqi2ncdq',\n"
            + "        index `auto_shard_key_w7r1ydbmolofjfm` using btree(`w7r1ydbmolofjfm`),\n"
            + "        _drds_implicit_id_ bigint auto_increment,\n"
            + "        primary key (_drds_implicit_id_)\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", sql);

        sql = "alter table `yrxsv7kevh4ce9` add column `7f3i` tinyint comment 'qa4qh1w', "
            + "change column `vmk` `o13nfnt8n` integer(6) unsigned unique comment 'ioj'";
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "yrxsv7kevh4ce9");
        Assert.assertNotNull(tableMeta.getFieldMetaByName("7f3i"));
        Assert.assertEquals("tinyint(4)", tableMeta.getFieldMetaByName("7f3i").getColumnType());
        Assert.assertNull(tableMeta.getFieldMetaByName("vmk", true));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("o13nfnt8n"));
        Assert.assertEquals("int(6) unsigned", tableMeta.getFieldMetaByName("o13nfnt8n").getColumnType());

        sql = "alter table `yrxsv7kevh4ce9` change column `7f3i` `tmu6ki0zxzslb` varbinary(1) null unique";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "yRxSV7kEvh4Ce9");
        Assert.assertNotNull(tableMeta.getFieldMetaByName("tmu6ki0zxzslb"));
        Assert.assertNull(tableMeta.getFieldMetaByName("7f3i", true));
        Assert.assertEquals("varbinary(1)", tableMeta.getFieldMetaByName("tmu6ki0zxzslb").getColumnType());
    }

    @Test
    public void testRemovePK_1() {
        String ddl = " create table t("
            + " id bigint primary key , "
            + " content text)";
        removePkAndCheck(ddl);
    }

    @Test
    public void testRemovePK_2() {
        String ddl = " create table t("
            + " id bigint , "
            + " content text, "
            + " primary key(id))";
        removePkAndCheck(ddl);
    }

    @Test
    public void testModifyColumnWithSequence_1() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta,
            "srzp_job_single",
            "create table `group_dissolution_log_lnsi` (\n"
                + " `id` int(11) not null auto_increment comment '',\n"
                + " `group_id` int(11) not null comment '',\n"
                + " `admin_id` int(11) not null default '0' comment '',\n"
                + " `type` tinyint(3) not null default '0' comment '',\n"
                + " `created_at` timestamp not null default current_timestamp comment '',\n"
                + " `updated_at` timestamp not null default current_timestamp on update current_timestamp comment '',\n"
                + " `deleted_at` timestamp null comment '',\n"
                + "  primary key (`id`),\n"
                + "  index `idx_group_id`(`group_id`)\n"
                + ") engine = innodb auto_increment = 1 default charset = `utf8mb4` default collate = `utf8mb4_bin` row_format = dynamic comment '?????'");

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `id` int(11) not null auto_increment comment '主键id' first");
        TableMeta tableMeta = memoryTableMeta.find("srzp_job_single", "group_dissolution_log_lnsi");
        Assert.assertEquals("id", tableMeta.getFields().get(0).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `group_id` int(11) not null comment '群id' after `id`,\n"
                + " modify column `admin_id` int(11) not null default 0 comment 'a端操作人id' after `group_id`");
        Assert.assertEquals("group_id", tableMeta.getFields().get(1).getColumnName());
        Assert.assertEquals("admin_id", tableMeta.getFields().get(2).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `type` tinyint(3) not null default 0 comment '类型 1:管理员直接解散 2:用户申请的解释' after `admin_id`,\n"
                + " modify column `created_at` timestamp(0) not null default current_timestamp(0) comment '创建时间' after `type`");
        Assert.assertEquals("type", tableMeta.getFields().get(3).getColumnName());
        Assert.assertEquals("created_at", tableMeta.getFields().get(4).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `updated_at` timestamp(0) not null default current_timestamp(0) on update current_timestamp(0) comment '更新时间' after `created_at`");
        Assert.assertEquals("updated_at", tableMeta.getFields().get(5).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `deleted_at` timestamp(0) null default null comment '删除时间' after `updated_at`");
        Assert.assertEquals("deleted_at", tableMeta.getFields().get(6).getColumnName());

    }

    @Test
    public void testModifyColumnWithSequence_2() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1",
            "create table `test_compat_yha2_7ajh_00002` (\n"
                + "  a int,\n"
                + "  b double,\n"
                + "  c varchar(10),\n"
                + "  d bigint,\n"
                + "  _drds_implicit_id_ bigint auto_increment,\n"
                + "  primary key (_drds_implicit_id_)\n"
                + ")");
        applySql(memoryTableMeta, "d1",
            "alter table `test_compat_yha2_7ajh_00002`\n"
                + "  modify column a int after b,\n"
                + "  drop column b,\n"
                + "  change column c b int,\n"
                + "  add column c int");
        memoryTableMeta.find("d1", "test_compat_yha2_7ajh_00002");

        TableMeta tableMeta = memoryTableMeta.find("d1", "test_compat_yha2_7ajh_00002");
        Assert.assertEquals(Lists.newArrayList("b", "a", "d", "_drds_implicit_id_", "c"),
            tableMeta.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
    }

    @Test
    public void testModifyColumnWithSequence_3() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1",
            "create table `t25` (\n"
                + "  a int,\n"
                + "  b double,\n"
                + "  c varchar(10),\n"
                + "  _drds_implicit_id_ bigint auto_increment,\n"
                + "  d bigint,\n"
                + "  primary key (_drds_implicit_id_)\n"
                + ")");
        String expectedDDL = "CREATE TABLE `t25` (\n"
            + "\tc varchar(10),\n"
            + "\tb double,\n"
            + "\ta int,\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "\td bigint,\n"
            + "\tPRIMARY KEY (_drds_implicit_id_)\n"
            + ")";
        applySql(memoryTableMeta, "d1",
            "alter table t25 modify column a int after c,modify column c varchar(10) first");
        TableMeta tableMeta = memoryTableMeta.find("d1", "t25");
        Assert.assertEquals(Lists.newArrayList("c", "b", "a", "_drds_implicit_id_", "d"),
            tableMeta.getFields().stream().map(
                TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
    }

    private void removePkAndCheck(String createSql) {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", createSql);

        TableMeta meta = memoryTableMeta.find("d1", "t");
        Assert.assertEquals(1, meta.getPrimaryFields().size());
        String dropPk = "alter table t drop primary key";
        applySql(memoryTableMeta, "d1", dropPk);

        meta = memoryTableMeta.find("d1", "t");
        Assert.assertTrue(meta.getPrimaryFields().isEmpty());
    }
}
