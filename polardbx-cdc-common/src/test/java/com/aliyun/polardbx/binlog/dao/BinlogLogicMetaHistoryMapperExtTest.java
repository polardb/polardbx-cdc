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
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

public class BinlogLogicMetaHistoryMapperExtTest extends BaseTestWithGmsTables {

    @Test
    public void test() {
        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);

        BinlogLogicMetaHistory history1 = new BinlogLogicMetaHistory();
        history1.setDdl("create table t1(id bigint primary key)");
        history1.setTableName("t1");
        history1.setDbName("xxx");
        history1.setType((byte) 2);
        history1.setTso("101");
        history1.setSqlKind("CREATE_TABLE");
        history1.setDelete(true);
        mapper.insertSelective(history1);

        BinlogLogicMetaHistory history2 = new BinlogLogicMetaHistory();
        history2.setDdl("create table t2(id bigint primary key)");
        history2.setTableName("t2");
        history2.setDbName("xxx");
        history2.setType((byte) 2);
        history2.setTso("102");
        history2.setSqlKind("CREATE_TABLE");
        history2.setDelete(true);
        mapper.insertSelective(history2);

        BinlogLogicMetaHistoryMapperExtend binlogLogicMetaHistoryMapperExtend =
            SpringContextHolder.getObject(BinlogLogicMetaHistoryMapperExtend.class);
        int n = binlogLogicMetaHistoryMapperExtend.softClean("103");
        Assert.assertEquals(2, n);
    }
}
