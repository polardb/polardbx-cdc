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

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTestWithGmsTablesTest extends BaseTestWithGmsTables {

    @Override
    public void before() {
        Assert.assertTrue(SPRING_BOOTED.get());
        log.info("execute before method for base test with gms test ");
    }

    @Override
    protected boolean truncateGmsTableAtEachBefore() {
        return false;
    }

    @Test
    public void testInitGmsTables() throws SQLException {
        // check tables is initialize
        int count = 0;
        try (Connection connection = getGmsDataSource().getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("show tables from `polardbx_meta_db`");
                while (rs.next()) {
                    count++;
                    if (log.isDebugEnabled()) {
                        log.debug("gms table [{}] is initialized", rs.getString(1));
                    }
                }
            }
        }
        Assert.assertTrue(count > 0);
    }

    @Test
    public void testMappers_1() {
        // check mybatis mappers is working
        SystemConfigInfoMapper mapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        SystemConfigInfo systemConfigInfo = new SystemConfigInfo();
        systemConfigInfo.setId(1L);
        systemConfigInfo.setConfigKey("key");
        systemConfigInfo.setConfigValue("value");
        mapper.insertSelective(systemConfigInfo);
        commonCheck();
    }

    @Test
    public void testMappers_2() {
        commonCheck();
    }

    @Test
    public void testMappers_3() throws Exception {
        truncateGmsTables();
    }

    @Test
    public void testMappers_4() {
        SystemConfigInfoMapper mapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        List<SystemConfigInfo> list = mapper.select(s -> s);
        Assert.assertEquals(0, list.size());
    }

    private void commonCheck() {
        SystemConfigInfoMapper mapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        List<SystemConfigInfo> list = mapper.select(s -> s);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("key", list.get(0).getConfigKey());
        Assert.assertEquals("value", list.get(0).getConfigValue());
    }
}
