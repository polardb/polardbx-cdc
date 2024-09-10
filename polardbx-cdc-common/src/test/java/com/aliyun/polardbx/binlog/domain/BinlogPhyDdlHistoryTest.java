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
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class BinlogPhyDdlHistoryTest extends BaseTestWithGmsTables {
    @Test
    public void testWhitespaceInTable() throws Exception {
        BinlogPhyDdlHistoryMapper map = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistory history = new BinlogPhyDdlHistory();
        history.setDbName("test_db_ ");

        history.setDdl("create table `test_tb_ `(id bigint(20) primary key auto_increment)");
        history.setBinlogFile("f.01");
        history.setPos(100);
        history.setClusterId("ccc");
        history.setStorageInstId("dn-1");
        history.setTso("aaa");
        map.insertSelective(history);
        BinlogPhyDdlHistory result =
            map.select(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso, isEqualTo("aaa"))).get(0);
        Assert.assertEquals("test_db_ ", result.getDbName());
    }
}
