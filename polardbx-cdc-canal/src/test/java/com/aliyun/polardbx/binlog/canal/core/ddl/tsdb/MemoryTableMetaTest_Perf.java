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

import org.junit.Test;

/**
 * Created by ziyang.lb
 **/
public class MemoryTableMetaTest_Perf {

    @Test
    public void testMemory() throws InterruptedException {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);

        for (int i = 0; i < 10000; i++) {
            String tableName = "test_" + i;
            String sql = "create table if not exists `" + tableName + "` (\n"
                + "        `xus8hsp` integer(1) not null comment '0rtl',\n"
                + "        `fsg0` bigint(6) unsigned zerofill not null comment 'fthandpbzn34ach',\n"
                + "        `bikb01bdmc4` int(0) unsigned not null,\n"
                + "        primary key using btree (`bikb01bdmc4`, `fsg0` asc),\n"
                + "        index `auto_shard_key_fsg0` using btree(`fsg0`)\n"
                + ") default character set = utf8mb4 default collate = utf8mb4_general_ci\n"
                + "partition by key (`fsg0`)";
            memoryTableMeta.apply(null, "d1", sql, null);
            memoryTableMeta.find("d1", tableName);

        }
        gc();
        printMemory();

        for (int i = 0; i < 10000; i++) {
            String tableName = "test_" + i;
            String sql = "drop table " + tableName;
            memoryTableMeta.apply(null, "d1", sql, null);
            memoryTableMeta.find("d1", tableName);
        }
        gc();
        printMemory();
    }

    private void gc() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            System.gc();
        }
        Thread.sleep(5000);
    }

    private void printMemory() {
        Runtime run = Runtime.getRuntime();
        double total = run.totalMemory();
        double free = run.freeMemory();
        double used = (total - free) / (1024 * 1024);
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");
        System.out.println("已使用内存 = " + df.format(used));
    }
}
