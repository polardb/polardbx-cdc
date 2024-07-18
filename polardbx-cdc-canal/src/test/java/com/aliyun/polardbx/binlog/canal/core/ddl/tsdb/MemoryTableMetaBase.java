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
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MemoryTableMetaBase extends BaseTest {

    protected MemoryTableMeta newMemoryTableMeta() {
        return new MemoryTableMeta(log, false);
    }

    protected void applySql(MemoryTableMeta m, String db, String sql) {
        m.apply(null, db, sql, null);
    }

    @Test
    public void testGenerateColumnIndex() {
        String sql1 = "CREATE TABLE expr_multi_column_tbl (\n"
            + "  a int PRIMARY KEY,\n"
            + "  b int,\n"
            + "  c int,\n"
            + "  d varchar(64),\n"
            + "  e varchar(64)\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY hash(a)";
        String sql2 = "alter table expr_multi_column_tbl "
            + "add local unique index expr_multi_column_tbl_idx((a+1) desc,b,c-1,substr(d,-2) asc,a+b+c*2)";

        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", sql1);
        applySql(memoryTableMeta, "d1", sql2);
        memoryTableMeta.find("d1", "expr_multi_column_tbl");
    }

    @Test
    public void testCreateTableIfNotExist() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();

        String sql1 = "create table if not exists `tT``g`(id bigint)";
        applySql(memoryTableMeta, "d1", sql1);
        TableMeta tableMeta = memoryTableMeta.find("d1", "tt`g");
        Assert.assertNotNull(tableMeta);
        Assert.assertEquals(tableMeta.getFieldMetaByName("id").getColumnType(), "bigint");

        String sql2 = "create table if not exists `Tt``G`(id varchar)";
        applySql(memoryTableMeta, "d1", sql2);
        tableMeta = memoryTableMeta.find("d1", "tt`g");
        Assert.assertNotNull(tableMeta);
        Assert.assertEquals(tableMeta.getFieldMetaByName("id").getColumnType(), "bigint");

        memoryTableMeta.setForceReplace(true);
        applySql(memoryTableMeta, "d1", sql2);
        tableMeta = memoryTableMeta.find("d1", "tt`g");
        Assert.assertNotNull(tableMeta);
        Assert.assertEquals(tableMeta.getFieldMetaByName("id").getColumnType(), "varchar");
    }

    @Test
    public void testTableNameNoTrim() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();

        String sql1 = "CREATE TABLE ` engineer` (\n"
            + "  id INTEGER NOT NULL,\n"
            + "  engineer_name VARCHAR(50),\n"
            + "  PRIMARY KEY (id)\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`";
        String sql2 = "CREATE TABLE `engineer` (\n"
            + "  id INTEGER NOT NULL,\n"
            + "  engineer_name VARCHAR(50),\n"
            + "  engineer_type VARCHAR(50),\n"
            + "  PRIMARY KEY (id)\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`";
        applySql(memoryTableMeta, "d1", sql1);
        applySql(memoryTableMeta, "d1", sql2);
        TableMeta tableMeta1 = memoryTableMeta.find("d1", " engineer");
        TableMeta tableMeta2 = memoryTableMeta.find("d1", "engineer");
        Assert.assertEquals("[id, engineer_name]", tableMeta1.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());
        Assert.assertEquals("[id, engineer_name, engineer_type]", tableMeta2.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());

        String sql3 = "alter table ` engineer` add column engineer_age bigint";
        String sql4 = "alter table `engineer` add column engineer_age bigint";
        applySql(memoryTableMeta, "d1", sql3);
        applySql(memoryTableMeta, "d1", sql4);
        TableMeta tableMeta3 = memoryTableMeta.find("d1", " engineer");
        TableMeta tableMeta4 = memoryTableMeta.find("d1", "engineer");
        Assert.assertEquals("[id, engineer_name, engineer_age]", tableMeta3.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());
        Assert.assertEquals("[id, engineer_name, engineer_type, engineer_age]", tableMeta4.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());

        String sql5 = "rename table ` engineer` to ` engineer_new`";
        String sql6 = "rename table `engineer` to `engineer_new`";
        applySql(memoryTableMeta, "d1", sql5);
        applySql(memoryTableMeta, "d1", sql6);
        TableMeta tableMeta5 = memoryTableMeta.find("d1", " engineer_new");
        TableMeta tableMeta6 = memoryTableMeta.find("d1", "engineer_new");
        Assert.assertEquals("[id, engineer_name, engineer_age]", tableMeta5.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());
        Assert.assertEquals("[id, engineer_name, engineer_type, engineer_age]", tableMeta6.getFields().stream()
            .map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()).toString());

        String sql7 = "drop table ` engineer_new`";
        String sql8 = "drop table `engineer_new`";
        applySql(memoryTableMeta, "d1", sql7);
        applySql(memoryTableMeta, "d1", sql8);
        TableMeta tableMeta7 = memoryTableMeta.find("d1", " engineer");
        TableMeta tableMeta8 = memoryTableMeta.find("d1", "engineer");
        Assert.assertNull(tableMeta7);
        Assert.assertNull(tableMeta8);
    }

    @Test
    public void testColumnWithWhitSpace() {
        String sql = "create table if not exists `default_as_expr_value_test_auto` (\n"
            + "  `pk` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "  `c1` int(11) NULL,\n"
            + "  `c2` int(11) NOT NULL DEFAULT (MOD(10, 3)),\n"
            + "  `c3` int(11) NOT NULL DEFAULT (ABS(-2)),\n"
            + "  `c4` int(11) NOT NULL DEFAULT (CAST(3 AS SIGNED)),\n"
            + "  `c5` int(11) NOT NULL DEFAULT (ROUND(3.7)),\n"
            + "  `c6` varchar(20) NOT NULL DEFAULT (CONCAT('1', '23')),\n"
            + "  `c7` varchar(20) NOT NULL DEFAULT (DATE_FORMAT(now(), _utf8mb4 '%Y-%m-%d')),\n"
            + "  ` c7_1` varchar(20) NOT NULL DEFAULT (DATE_FORMAT(now(), _utf8mb4 '%Y-%m-%d')),\n"
            + "  `C8` FLOAT NOT NULL DEFAULT (SQRT(10)),\n"
            + "  `C9` JSON NOT NULL DEFAULT (JSON_ARRAY()),\n"
            + "  `C10` TIMESTAMP NOT NULL DEFAULT (TIMESTAMPADD(MINUTE, 5, '2003-01-02')),\n"
            + "  `C11` DATE NOT NULL DEFAULT (CURRENT_DATE + INTERVAL 1 YEAR),\n"
            + "  `C12` varchar(20) NOT NULL DEFAULT (LPAD('string', 5, '0')),\n"
            + "  `C13` tinyint(1) NOT NULL DEFAULT (true),\n"
            + "  PRIMARY KEY (`pk`)\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY hash(`c12`)\n"
            + "TBPARTITION BY hash(`c3`) TBPARTITIONS 4";

        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "default_as_expr_value_test_auto");
        Assert.assertNotNull(tableMeta.getFieldMetaByName(" c7_1"));
        Assert.assertNull(tableMeta.getFieldMetaByName("c7_1", true));

        String sql2 = "alter table default_as_expr_value_test_auto add column ` cx_1` bigint not null";
        applySql(memoryTableMeta, "d1", sql2);
        tableMeta = memoryTableMeta.find("d1", "default_as_expr_value_test_auto");
        Assert.assertNotNull(tableMeta.getFieldMetaByName(" cx_1"));
        Assert.assertNull(tableMeta.getFieldMetaByName("cx_1", true));
    }
}
