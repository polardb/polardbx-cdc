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
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultQueryLogTest {

    @Test
    public void testSerialize() {
        List<TableMeta.FieldMeta> fieldMetas = Lists.newArrayList(
            new TableMeta.FieldMeta("c1", "char", true, true, "xxx", false, "xxx"),
            new TableMeta.FieldMeta("c2", "char", true, true, "xxx", false, "xxx"),
            new TableMeta.FieldMeta("c3", "char", true, true, "xxx", false, "xxx")
        );

        Map<String, TableMeta.IndexMeta> indexMetaMap = Maps.newHashMap();
        indexMetaMap.put("idx1", new TableMeta.IndexMeta("idx1", "INDEX", false));
        indexMetaMap.put("idx2", new TableMeta.IndexMeta("idx2", "INDEX", false));
        indexMetaMap.put("idx3", new TableMeta.IndexMeta("idx3", "INDEX", false));

        TableMeta tableMeta = new TableMeta();
        tableMeta.setTable("t1");
        tableMeta.setDdl("create table t1(id bigint)");
        tableMeta.setCharset("utf8");
        tableMeta.setSchema("d1");
        tableMeta.setUseImplicitPk(true);
        tableMeta.setFields(fieldMetas);
        tableMeta.setIndexes(indexMetaMap);

        DefaultQueryLog defaultQueryLog = new DefaultQueryLog(
            "d1",
            "create table t1",
            new Timestamp(System.currentTimeMillis()),
            10000000,
            1,
            DBMSAction.CREATE,
            System.currentTimeMillis());
        defaultQueryLog.setFirstDdl(new AtomicBoolean(true));
        defaultQueryLog.setParallelSeq(23);
        defaultQueryLog.setOptionValue("opt1", 200);
        defaultQueryLog.setTableMeta(tableMeta);

        byte[] data = SerializationUtils.serialize(defaultQueryLog);
        DefaultQueryLog defaultQueryLog1 = SerializationUtils.deserialize(data);
        Assert.assertEquals(defaultQueryLog, defaultQueryLog1);
    }
}
