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
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TableBlackListTest extends RplBaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(TableBlackListTest.class);

    private static final String BLACK_DB = "cdc_blacklist_db";
    private static final String BLACK_TABLE = "cdc_black_table";
    private static final String NORMAL_TABLE = "cdc_normal_table";

    private static final String CREATE_TABLE_FORMAT =
        "create table `%s`(id bigint primary key, name varchar(20), age int) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 64";

    private static final String CREATE_BLACK_DB = "create database " + BLACK_DB;

    private static final String DML = "insert into `%s`(id, name, age) values (%s,'%s', %s)";
    private static final String RANDOM_WORD = "abcdefghijklmnopqrstuvwxyz";
    ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "cdc_black_list_t");
            t.setDaemon(true);
            return t;
        }
    });
    private volatile boolean running = true;
    private AtomicLong idGenerator = new AtomicLong(0);

    private CountDownLatch countDownLatch = new CountDownLatch(2);

    private void init() throws SQLException {
        Connection conn = getPolardbxConnection();
        try {
            JdbcUtil.executeSuccess(conn, CREATE_BLACK_DB);
            useDb(conn, BLACK_DB);
            JdbcUtil.executeSuccess(conn, String.format(CREATE_TABLE_FORMAT, BLACK_TABLE));
            JdbcUtil.executeSuccess(conn, String.format(CREATE_TABLE_FORMAT, NORMAL_TABLE));
            logger.info("init black list db and table success");
        } finally {
            conn.close();
        }
    }

    private void dmlWithTable(String tableName) {

        Connection conn = getPolardbxConnection(BLACK_DB);
        while (running) {
            try {
                String dml =
                    String
                        .format(DML, tableName, idGenerator.incrementAndGet(), RandomStringUtils.random(8, RANDOM_WORD),
                            RandomUtils.nextInt());
                JdbcUtil.executeSuccess(conn, dml);
            } catch (Throwable e) {

            }
        }

        countDownLatch.countDown();
    }

    private int queryBlackTableDataCount(Connection polarxConn) throws SQLException {
        JdbcUtil.useDb(polarxConn, BLACK_DB);
        ResultSet resultSet = JdbcUtil.executeQuery("select count(*) as c from " + BLACK_TABLE, polarxConn);
        resultSet.next();
        return resultSet.getInt("c");
    }

    private void checkBlackTable() throws Exception {

        Connection mysqlConn = getCdcSyncDbConnection();
        try {
            int count = queryBlackTableDataCount(mysqlConn);
            Assert.assertEquals(0, count);
        } finally {
            mysqlConn.close();
        }

        Connection polarxConn = getPolardbxConnection();
        try {
            int count = queryBlackTableDataCount(polarxConn);
            Assert.assertTrue(count > 0);
        } finally {
            polarxConn.close();
        }
    }

    @Test
    public void testBlackTable() throws Exception {
        init();
        executor.execute(() -> dmlWithTable(BLACK_TABLE));
        executor.execute(() -> dmlWithTable(NORMAL_TABLE));
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        } catch (InterruptedException e) {
        }
        running = false;
        countDownLatch.await(5, TimeUnit.MINUTES);
        CheckParameter checkParameter =
            CheckParameter.builder()
                .dbName(BLACK_DB)
                .tbName(NORMAL_TABLE)
                .directCompareDetail(true)
                .compareDetailOneByOne(false)
                .loopWaitTimeoutMs(TimeUnit.MINUTES.toMillis(5)).build();
        waitAndCheck(checkParameter);
        checkBlackTable();
    }
}
