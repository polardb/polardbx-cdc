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
}
