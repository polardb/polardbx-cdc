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

import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
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
