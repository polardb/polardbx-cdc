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
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

public class BinlogPhyDdlHistoryMapperTest extends BaseTestWithGmsTables {
    @Test
    public void testInsert() {
        BinlogPhyDdlHistory record = new BinlogPhyDdlHistory();
        record.setDdl("ddl");
        record.setDbName("dbName");
        record.setTso("tso");
        record.setStorageInstId("s1");
        record.setClusterId("cluster-1");
        record.setPos(11);
        record.setBinlogFile("bf.1");
        record.setGmtCreated(new java.util.Date());
        record.setGmtModified(new java.util.Date());

        BinlogPhyDdlHistoryMapper mapper = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        int result = mapper.insert(record);
        Assert.assertEquals(1, result);
    }

    @Test
    public void testUpdateAll() {
        BinlogPhyDdlHistory record = new BinlogPhyDdlHistory();
        record.setDdl("ddl");
        record.setDbName("dbName");
        record.setTso("tso1");
        record.setStorageInstId("s1");
        record.setClusterId("cluster-1");
        record.setPos(11);
        record.setBinlogFile("bf.1");
        record.setGmtCreated(new java.util.Date());
        record.setGmtModified(new java.util.Date());

        BinlogPhyDdlHistoryMapper mapper = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        int result = mapper.insert(record);
        Assert.assertEquals(1, result);

        record.setDdl("alter table test");
        record.setId(1);
        result = mapper.updateByPrimaryKey(record);
        Assert.assertEquals(1, result);

        result = mapper.updateByPrimaryKeySelective(record);
        Assert.assertEquals(1, result);
    }
}
