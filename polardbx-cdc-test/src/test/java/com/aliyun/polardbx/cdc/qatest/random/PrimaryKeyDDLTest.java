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
package com.aliyun.polardbx.cdc.qatest.random;

import com.alibaba.druid.util.StringUtils;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PrimaryKeyDDLTest extends RplBaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(PrimaryKeyDDLTest.class);

    private static final String PK_TEST_DB = "pk_test_db";
    private static final String TEST_TABLE = "test_table";

    private static final String CREATE_TABLE =
        "create table test_table(id bigint primary key, name varchar(20), age int) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 64";

    private static final String DML_PATTERN = "insert into test_table(id, name, age) values(%s, '%s', %s)";

    private static final String RANDOM_WORD = "abcdefghijklmnopqrstuvwxyz";

    private static final String DROP_PK = "alter table test_table drop primary key";

    private static final String ADD_PK = "alter table test_table add primary key(id)";
    private static final String CREATE_DB = "create database " + PK_TEST_DB;
    private AtomicLong idGenerator = new AtomicLong(0);
    private volatile boolean running = true;

    private ExecutorService executorService = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pk_test_thread");
            t.setDaemon(true);
            return t;
        }
    });

    public void dml() {
        Connection conn = getPolardbxConnection(PK_TEST_DB);
        while (running) {
            try {
                String dml =
                    String.format(DML_PATTERN, idGenerator.incrementAndGet(), RandomStringUtils.random(8, RANDOM_WORD),
                        RandomUtils.nextInt());
                JdbcUtil.executeSuccess(conn, dml);
            } catch (Throwable e) {

            }
        }

    }

    public void ddl() {
        Connection conn = getPolardbxConnection(PK_TEST_DB);
        boolean hasPk = true;
        while (running) {
            try {
                String ddl;
                if (hasPk) {
                    ddl = DROP_PK;
                } else {
                    ddl = ADD_PK;
                }
                JdbcUtil.executeSuccess(conn, ddl);
                hasPk = !hasPk;
            } catch (Throwable e) {

            }
        }
    }

    private boolean testSupportImplicitPrimaryKey(Connection connection) throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("show global variables  like 'implicit_primary_key'");
        if (rs.next()) {
            String value = rs.getString(2);
            return StringUtils.equalsIgnoreCase("ON", value);
        }
        throw new UnsupportedOperationException(
            "test RDS support implicit_primary_key failed, check RDS implicit_primary_key config");
    }

    private void setImplicitConfig(Connection connection) throws SQLException {
        Statement st = connection.createStatement();
        st.execute("set global implicit_primary_key = 'ON'");
    }

    private void checkAndSet() throws ExecutionException, RetryException {
        //set dn1

        Retryer retryer = RetryerBuilder.<Boolean>
            newBuilder().withStopStrategy(StopStrategies.stopAfterAttempt(5)).retryIfResult(
            o -> Objects.equals(o, false))
            .retryIfException().build();
        retryer.call(() -> {
            final Connection conn1 = getDataNodeConnection();
            try {
                setImplicitConfig(conn1);
                return testSupportImplicitPrimaryKey(conn1);
            } finally {
                if (conn1 != null) {
                    conn1.close();
                }
            }
        });
        retryer.call(() -> {
            final Connection conn2 = getDataNodeConnectionSecond();
            try {
                setImplicitConfig(conn2);
                return testSupportImplicitPrimaryKey(conn2);
            } finally {
                if (conn2 != null) {
                    conn2.close();
                }
            }
        });
    }

    private void init() throws SQLException {
        Connection conn = getPolardbxConnection();
        try {
            JdbcUtil.executeSuccess(conn, CREATE_DB);
            useDb(conn, PK_TEST_DB);
            JdbcUtil.executeSuccess(conn, CREATE_TABLE);
        } finally {
            conn.close();
        }
    }

    public void testResult() {

    }

    @Test
    public void test() throws SQLException, InterruptedException, ExecutionException, RetryException {
        logger.info("test RDS has implicit pk feature!");
        checkAndSet();
        logger.info("create db and table");
        init();
        logger.info("execute async task with 10 min!");
        CountDownLatch latch = new CountDownLatch(2);
        executorService.execute(() -> {
            dml();
            latch.countDown();
            logger.info("dml thread is finished.");
        });
        executorService.execute(() -> {
            ddl();
            latch.countDown();
            logger.info("ddl thread is finished.");
        });

        // 跑10分钟
        Thread.sleep(TimeUnit.MINUTES.toMillis(10));
        running = false;
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new PolardbxException("wait all async task finish failed!");
        }
        CheckParameter checkParameter =
            CheckParameter.builder()
                .dbName(PK_TEST_DB)
                .tbName(TEST_TABLE)
                .directCompareDetail(true)
                .compareDetailOneByOne(false)
                .loopWaitTimeoutMs(TimeUnit.MINUTES.toMillis(5)).build();
        waitAndCheck(checkParameter);
    }

    @After
    public void after() {
        running = false;
        executorService.shutdownNow();
    }
}
