/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-22 19:41
 **/
@Slf4j
public class BaseTestTest extends BaseTest {

    @Test
    public void testConnection_1() throws SQLException {
        Connection connection = getGmsDataSource().getConnection();
        H2Util.executeUpdate(connection, "create table t1(name varchar(200))");
        commonExecute(connection);
        commonExecute(null);
    }

    @Test
    public void testConnection_2() throws SQLException {
        commonExecute(null);
    }

    @Test
    public void testConnection_3() throws InterruptedException {
        int threadCount = 100;
        List<Thread> list = new ArrayList<>(threadCount);
        AtomicLong count = new AtomicLong();
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            list.add(new Thread(() -> {
                try {
                    commonExecute(null);
                } catch (Throwable t) {
                    log.error("common execute error !", t);
                    count.incrementAndGet();
                } finally {
                    countDownLatch.countDown();
                }
            }));
        }

        list.forEach(Thread::start);
        countDownLatch.await(30, TimeUnit.SECONDS);
        Assert.assertEquals(0, count.get());
    }

    @Test
    public void testInitGmsTables() throws SQLException {
        // check tables is initialize
        int count = 0;
        try (Connection connection = getGmsDataSource().getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("show tables");
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
        List<SystemConfigInfo> list = mapper.select(s -> s);
        Assert.assertEquals(0, list.size());
        SystemConfigInfo systemConfigInfo = new SystemConfigInfo();
        systemConfigInfo.setId(1L);
        systemConfigInfo.setConfigKey("key");
        systemConfigInfo.setConfigValue("value");
        mapper.insertSelective(systemConfigInfo);
        commonCheck();
    }

    private void commonCheck() {
        SystemConfigInfoMapper mapper = SpringContextHolder.getObject(SystemConfigInfoMapper.class);
        List<SystemConfigInfo> list = mapper.select(s -> s);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("key", list.get(0).getConfigKey());
        Assert.assertEquals("value", list.get(0).getConfigValue());
    }

    private void commonExecute(Connection connection) throws SQLException {
        if (connection == null) {
            connection = getGmsDataSource().getConnection();
        }
        commonAssert(connection);
        connection.close();
    }

    private void commonAssert(Connection connection) {
        List<String> tables = H2Util.showTables(connection, null);
        Assert.assertTrue(tables.contains("T1"));
    }
}
