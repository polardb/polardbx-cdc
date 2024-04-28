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
public class MemoryTableMetaTest_DataType extends MemoryTableMetaBase {

    @Test
    public void testNvarchar() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "create table `t1` (\n"
            + "        a nvarchar(255),\n"
            + "        b varchar(255),\n"
            + "        c nchar(255),\n"
            + "        d char(255)\n"
            + ") default collate = utf8mb4_bin default character set = utf8mb4 default collate = utf8mb4_bin";
        memoryTableMeta.apply(null, "d1", sql, null);

        TableMeta tableMeta = memoryTableMeta.find("d1", "t1");
        tableMeta.getFields().forEach(f -> {
            String name = f.getColumnName();
            if (name.equalsIgnoreCase("a")) {
                Assert.assertEquals("varchar(255)", f.getColumnType());
                Assert.assertEquals("utf8", f.getCharset());
            } else if (name.equalsIgnoreCase("b")) {
                Assert.assertEquals("utf8mb4", f.getCharset());
            } else if (name.equalsIgnoreCase("c")) {
                Assert.assertEquals("char(255)", f.getColumnType());
                Assert.assertEquals("utf8", f.getCharset());
            } else if (name.equalsIgnoreCase("d")) {
                Assert.assertEquals("utf8mb4", f.getCharset());
            }
        });
    }

    @Test
    public void testDefaultNull() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "create table t1("
            + "id bigint primary key, "
            + "c1 varchar(100) default null, "
            + "c2 varchar(100) default 'null')";
        applySql(memoryTableMeta, "d1", sql);
        TableMeta tableMeta = memoryTableMeta.find("d1", "t1");
        TableMeta.FieldMeta c1 = tableMeta.getFieldMetaByName("c1");
        TableMeta.FieldMeta c2 = tableMeta.getFieldMetaByName("c2");
        Assert.assertNull(c1.getDefaultValue());
        Assert.assertEquals("null", c2.getDefaultValue());
        Assert.assertEquals("null", String.valueOf(c1.getDefaultValue()));
        Assert.assertEquals("null", String.valueOf(c2.getDefaultValue()));
    }

    @Test
    public void testCharsetInherit() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "create table t1("
            + "id bigint primary key, "
            + "c1 varchar(100) default null, "
            + "c2 varchar(100) default 'null') "
            + "default collate = utf8mb4_bin default character set = utf8mb4";
        applySql(memoryTableMeta, "d1", sql);
        applySql(memoryTableMeta, "d1", "create table t2 like t1");
        TableMeta tableMeta1 = memoryTableMeta.find("d1", "t1");
        TableMeta tableMeta2 = memoryTableMeta.find("d1", "t2");
        Assert.assertEquals("utf8mb4", tableMeta1.getCharset());
        Assert.assertEquals("utf8mb4", tableMeta2.getCharset());
    }
}
