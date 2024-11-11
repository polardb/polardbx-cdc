/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-21 23:31
 **/
public class MemoryTableMetaTest_CaseSensitive extends MemoryTableMetaBase {

    private static final String create_sql = "create table if not exists Abc (\n"
        + "        `id` int(11) unsigned not null comment 'id',\n"
        + "        `xx` int(11) unsigned not null comment 'xx',\n"
        + "        `vv` varchar(30) not null comment 'vv',\n"
        + "        `bb` varchar(300) not null comment 'bb',\n"
        + "        `gg` varchar(100) not null comment 'gg',\n"
        + "        `jj` varchar(100) not null comment 'jj',\n"
        + "        `ll` varchar(100) not null comment 'll',\n"
        + "        `kk` varchar(60) not null comment 'kk',\n"
        + "        `oo` smallint(5) unsigned not null comment 'oo',\n"
        + "        `pp` tinyint(3) unsigned not null comment 'pp',\n"
        + "        `yy` varchar(100) not null comment 'yy',\n"
        + "        primary key (`id`)\n"
        + ") engine = innodb default charset = utf8mb4 default character set = utf8mb4 default collate = utf8mb4_general_ci comment 'xxyyzz'";

    @Test
    public void testCaseSensitive1() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB", create_sql.toLowerCase(), null);
        assertR(memoryTableMeta);

        String ddlSql = "alter table `Abc`\n"
            + "        add column `GKHOUQO` text(0) comment 'k',\n"
            + "        add key `UQN3J` (`qkf7de1vjwy9b8f`, `beo7` desc, `itg32` asc)";
        memoryTableMeta.apply(null, "DbDB", ddlSql, null);
        TableMeta tm = memoryTableMeta.find("DbDB", "Abc");
        Assert.assertNotNull(tm.getFieldMetaByName("GKHOUQO"));
        Assert.assertNotNull(tm.getFieldMetaByName("gkhouqo"));

        Set<String> sets = memoryTableMeta.find("DbDB", "abc").getIndexes().keySet();
        Assert.assertEquals(Sets.newHashSet("uqn3j"), sets);
    }

    @Test
    public void testCaseSensitive2() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB", create_sql, null);
        assertR(memoryTableMeta);
    }

    @Test
    public void testCaseSensitive4() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB".toLowerCase(), create_sql, null);
        assertR(memoryTableMeta);
    }

    private void assertR(MemoryTableMeta memoryTableMeta) {
        TableMeta t1 = memoryTableMeta.find("DbDB", "Abc");
        TableMeta t2 = memoryTableMeta.find("DbDB", "abc");
        TableMeta t3 = memoryTableMeta.find("dbdb", "Abc");
        TableMeta t4 = memoryTableMeta.find("dbdb", "abc");

        Assert.assertNotNull(t1);
        Assert.assertNotNull(t2);
        Assert.assertNotNull(t3);
        Assert.assertNotNull(t4);
    }
}
