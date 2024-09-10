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
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class BinlogLogicMetaHistoryTest extends BaseTestWithGmsTables {
    @Test
    public void testWhitespaceInTable() throws Exception {
        BinlogLogicMetaHistoryMapper map = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        BinlogLogicMetaHistory history = new BinlogLogicMetaHistory();
        history.setDbName("test_db_ ");
        history.setTableName("test_tb_ ");
        history.setDdl("create table `test_tb_ `(id bigint(20) primary key auto_increment)");
        history.setDdlJobId(1L);
        history.setDdlRecordId(1L);
        history.setSqlKind("CREATE_TABLE");
        history.setTso("aaa");
        history.setType((byte) 1);
        map.insertSelective(history);
        BinlogLogicMetaHistory result =
            map.select(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.ddlJobId, isEqualTo(1L))).get(0);
        Assert.assertEquals("test_db_ ", result.getDbName());
        Assert.assertEquals("test_tb_ ", result.getTableName());
    }
}
