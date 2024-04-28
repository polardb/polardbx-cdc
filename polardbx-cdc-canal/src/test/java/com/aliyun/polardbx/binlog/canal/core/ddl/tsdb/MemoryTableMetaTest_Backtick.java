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
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.SQLUtils.buildCreateLikeSql;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-21 23:48
 **/
public class MemoryTableMetaTest_Backtick extends MemoryTableMetaBase {

    @Test
    public void testBacktick_1() {
        String sql = "create table if not exists `gxw_testbacktick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        Assert.assertNotNull(tableMeta);
        Assert.assertNotNull(tableMeta.getFieldMetaByName("col-minus"));
        Assert.assertNull(tableMeta.getFieldMetaByName("`col-minus`", true));

        memoryTableMeta.apply(null, "ddl1_1573919240", "drop table if exists `gxw_testbacktick`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        Assert.assertNull(tableMeta);
    }

    @Test
    public void testBacktick_2() {
        String sql = "create table if not exists `gxw_test``backtick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        Assert.assertNotNull(tableMeta);

        memoryTableMeta.apply(null, "ddl1_1573919240", "drop table if exists `gxw_test``backtick`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        Assert.assertNull(tableMeta);
    }

    @Test
    public void testBacktick_3() {
        String sql1 = "/*drds /11.122.76.56/13e123c82c802001/null// */"
            + "create table if not exists `gxw_test``backtick_bpzj` ( "
            + "`col-minus` int, "
            + "c2 int, "
            + "_drds_implicit_id_ bigint auto_increment, "
            + "primary key (_drds_implicit_id_) )";
        String sql2 = "/*drds /11.122.76.56/13e123c894402001/null// */"
            + "alter table `gxw_test``backtick_bpzj` add column c3 int";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql1, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick_bpzj");
        Assert.assertNotNull(tableMeta);

        memoryTableMeta.apply(null, "d1", sql2, null);
        tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick_bpzj");
        Assert.assertNotNull(tableMeta.getFieldMetaByName("c3"));
    }

    @Test
    public void testBacktickRename() {
        String sql = "create table if not exists `gxw_test``backtick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        Assert.assertNotNull(tableMeta);

        memoryTableMeta.apply(null, "ddl1_1573919240",
            "rename table `gxw_test``backtick` to `gxw_testbacktick_new`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        Assert.assertNull(tableMeta);

        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick_new");
        Assert.assertNotNull(tableMeta);
    }

    @Test
    public void testBacktickRename2() {
        String sql = "create table if not exists `gxw_testbacktick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        Assert.assertNotNull(tableMeta);

        memoryTableMeta.apply(null, "ddl1_1573919240",
            "rename table `gxw_testbacktick` to `gxw_testbacktick_new`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        Assert.assertNull(tableMeta);

        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick_new");
        Assert.assertNotNull(tableMeta);
    }

    @Test
    public void testCreateTableLike() {
        String t0 = "test_table";
        String t1 = "`gsi-`test_table`";
        String t2 = "`gsi-`test_table`_0h4o_009";

        createTableLike(t0, t2);
        createTableLike(t1, t2);
    }

    private void createTableLike(String baseTable, String targetTable) {
        String createSql = "create table %s (id bigint primary key)";
        String createLikeSql = buildCreateLikeSql(targetTable, "d1", baseTable);

        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", String.format(createSql, "`" + escape(baseTable) + "`"));
        TableMeta tableMeta = memoryTableMeta.find("d1", baseTable);
        Assert.assertNotNull(tableMeta);

        applySql(memoryTableMeta, "d1", createLikeSql);
        tableMeta = memoryTableMeta.find("d1", targetTable);
        Assert.assertNotNull(tableMeta);
    }
}
