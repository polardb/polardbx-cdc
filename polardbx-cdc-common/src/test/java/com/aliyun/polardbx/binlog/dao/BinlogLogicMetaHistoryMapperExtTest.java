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

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

public class BinlogLogicMetaHistoryMapperExtTest {
    private ApplicationContext context;

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void test() {
        BinlogLogicMetaHistoryMapperExtend binlogLogicMetaHistoryMapperExtend =
            SpringContextHolder.getObject(BinlogLogicMetaHistoryMapperExtend.class);
        int n = binlogLogicMetaHistoryMapperExtend.softClean("697865176674939705615097169811991429120000000000000000");
        Assert.assertEquals(n, 3);
    }
}
