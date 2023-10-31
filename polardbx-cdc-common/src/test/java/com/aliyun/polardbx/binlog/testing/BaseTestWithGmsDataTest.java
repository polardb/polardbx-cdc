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
package com.aliyun.polardbx.binlog.testing;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.List;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-15 14:34
 **/
@Slf4j
public class BaseTestWithGmsDataTest extends BaseTestWithGmsData {

    @Test
    public void testBasic() {
        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        long count = mapper.count(s -> s);
        Assert.assertTrue(count > 0);
    }

    @Test
    public void testUnEscape() {
        //see com.aliyun.polardbx.binlog.testing.h2.H2Util.convertSql # unescapeJava
        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        List<BinlogLogicMetaHistory> list = mapper.select(s
            -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.type, SqlBuilder.isEqualTo((byte) 2)));
        list.forEach(i -> JSONObject.parseObject(i.getTopology()));
    }
}
