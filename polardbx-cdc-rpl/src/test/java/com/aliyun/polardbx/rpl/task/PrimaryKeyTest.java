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
package com.aliyun.polardbx.rpl.task;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.rpl.RplTaskRunner;
import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/4/28 16:00
 * @since 5.0.0.0
 */
@Ignore
public class PrimaryKeyTest extends TestBase {
    private static final String DB_1 = "primary_key_test";

    private static final String NO_PK_NO_UK = "no_pk_no_uk";
    private static final String SINGLE_PK = "single_pk";
    private static final String MULTI_PK = "multi_pk";
    private static final String SINGLE_UK = "single_uk";
    private static final String MULTI_UK = "multi_uk";
    private static final String SINGLE_PK_SINGLE_UK = "single_pk_single_uk";

    private static final List<String> ALL_TBS =
//        Arrays.asList(NO_PK_NO_UK, SINGLE_PK, MULTI_PK, SINGLE_UK, MULTI_UK, SINGLE_PK_SINGLE_UK);
        Arrays.asList(SINGLE_PK, MULTI_PK, SINGLE_UK, MULTI_UK, SINGLE_PK_SINGLE_UK);
    private static final List<String> FIELDS = Arrays.asList("f0", "f1", "f2", "f3", "f4", "f5");

    private static final int COUNT_IN_ONE_TB = 100;

    @Before
    public void before() throws Exception {
        channel = "primary_key_test";
        super.before();
//        initDbsAndTables();
//        initBinlogPosition = getLastBinlogPosition();

        String dropSql = "drop database if exists " + DB_1;
        String createSql = "create database if not exists " + DB_1;
        execUpdate(srcConn, dropSql, null);
        execUpdate(srcConn, createSql, null);
        execUpdate(dstConn, dropSql, null);
        execUpdate(dstConn, createSql, null);
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    private void initDbsAndTables() {
        List<Connection> connections = Arrays.asList(srcConn, dstConn, mysqlDstConn);

        String dropSql = "drop database if exists " + DB_1;
        String createSql = "create database " + DB_1;

        for (Connection conn : connections) {
            execUpdate(conn, dropSql, null);
            execUpdate(conn, createSql, null);

            String createTable =
                String.format("create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int)", NO_PK_NO_UK);
            execUpdate(conn, createTable, null);

            createTable = String
                .format("create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int, primary key(f0))", SINGLE_PK);
            execUpdate(conn, createTable, null);

            createTable = String
                .format("create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int, primary key(f0, f1))",
                    MULTI_PK);
            execUpdate(conn, createTable, null);

            createTable = String
                .format("create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int, unique key uk(id))",
                    SINGLE_UK);
            execUpdate(conn, createTable, null);

            createTable = String
                .format("create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int, primary key(f0, f1))",
                    MULTI_UK);
            execUpdate(conn, createTable, null);

            createTable = String
                .format(
                    "create table %s (f0 int, f1 int, f2 int, f3 int, f4 int, f5 int, primary key(f0, f1), unique key uk(f2, f3))",
                    SINGLE_PK_SINGLE_UK);
            execUpdate(conn, createTable, null);
        }
    }

    /**
     * 源为 Polarx，有隐藏主键
     */
    @Test
    public void primary_Key_Test() throws Exception {
        setupService(channel, initBinlogPosition, null);
        ExecutorService executorService = Executors.newFixedThreadPool(10, new NamedThreadFactory(""));
        List<Future> futures = new ArrayList<>();

        for (String tb : ALL_TBS) {
            final String table = DB_1 + "." + tb;
            Future future = executorService.submit(() -> {
                int i = 0;
                while (i < COUNT_IN_ONE_TB) {
                    StringBuilder sb = new StringBuilder();

                    String values = "('%d', '%d', '%d', '%d', '%d', '%d'),";
                    int j = 0;
                    for (; j < 1000; j++) {
                        int value = i + j;
                        sb.append(String.format(values, value, value, value, value, value, value));
                    }
                    i += j + 1;

                    String sql = String.format("insert into %s values %s", table, sb);
                    sql = sql.substring(0, sql.length() - 1);

                    execUpdate(srcConn, sql, null);
                }
            });
            futures.add(future);
        }

        checkFinish(futures);
        futures.clear();

        // 更新主键
        for (String tb : ALL_TBS) {
            final String table = DB_1 + "." + tb;
            Future future = executorService.submit(() -> {
                int i = 0;
                int oldValue = i;
                int newValue = i + 1;
                while (i < COUNT_IN_ONE_TB / 2) {
                    String sql =
                        String.format("update %s set f0=%d, f1=%d, f2=%d, f3=%d, f4=%d, f5=%d where f0=%d", table,
                            newValue,
                            newValue,
                            newValue,
                            newValue,
                            newValue,
                            newValue,
                            oldValue);
                    sql = sql.substring(0, sql.length() - 1);
                    execUpdate(srcConn, sql, null);
                    i++;
                }
            });
            futures.add(future);
        }
        checkFinish(futures);

        // 交换 pk，但 uk 不变
        for (String tb : ALL_TBS) {
            checkTwoDstsSame(DB_1, tb, FIELDS, "f0");
        }
    }

    @Test
    public void back_Tracking_Test() throws Exception {
        String createSql1 = String
            .format("create table %s.%s (f0 int, f1 int, f2 int, primary key(f0)) ",
                DB_1, SINGLE_PK);
        String createSql2 = String
            .format("create table %s.%s (f0 int, f1 int, f2 int, primary key(f0), unique key uk(f1)) ",
                DB_1, SINGLE_PK_SINGLE_UK);

        String selectSql = String.format("select * from %s.%s order by f0", DB_1, SINGLE_PK_SINGLE_UK);

        execUpdate(srcConn, createSql1, null);
        execUpdate(dstConn, createSql1, null);
        execUpdate(srcConn, createSql2, null);
        execUpdate(dstConn, createSql2, null);

        // 对于 SINGLE_PK_SINGLE_UK 表，所有 sql 不修改 uk/pk
        String sql1_1 = String.format("insert into %s.%s values(1,1,1)", DB_1, SINGLE_PK);
        String sql1_2 = String.format("insert into %s.%s values(2,2,2)", DB_1, SINGLE_PK);
        String sql1_3 = String.format("update %s.%s set f1 = 3 where f0 = 1", DB_1, SINGLE_PK);
        String sql1_4 = String.format("update %s.%s set f1 = 1 where f0 = 2", DB_1, SINGLE_PK);

        // 对于 SINGLE_PK_SINGLE_UK 表，sql2_3 修改 f1（uk）, sql2_4 修改 f1（uk）
        String sql2_1 = String.format("insert into %s.%s values(1,1,1)", DB_1, SINGLE_PK_SINGLE_UK);
        String sql2_2 = String.format("insert into %s.%s values(2,2,2)", DB_1, SINGLE_PK_SINGLE_UK);
        String sql2_3 = String.format("update %s.%s set f1 = 3 where f0 = 1", DB_1, SINGLE_PK_SINGLE_UK);
        String sql2_4 = String.format("update %s.%s set f1 = 1 where f0 = 2", DB_1, SINGLE_PK_SINGLE_UK);

        // execute sqls
        BinlogPosition position0 = getLastBinlogPosition();
        execUpdate(srcConn, sql1_1, null);
        execUpdate(srcConn, sql2_1, null);

        BinlogPosition position1 = getLastBinlogPosition();
        execUpdate(srcConn, sql1_2, null);
        execUpdate(srcConn, sql2_2, null);

        BinlogPosition position2 = getLastBinlogPosition();
        execUpdate(srcConn, sql1_3, null);
        execUpdate(srcConn, sql2_3, null);

        BinlogPosition position3 = getLastBinlogPosition();
        execUpdate(srcConn, sql1_4, null);
        execUpdate(srcConn, sql2_4, null);

        // start runner
        setupService(position0);
        runnerThread.start();
        wait(WAIT_TASK_SECOND * 2);
        checkBackTrackingTestResult(selectSql);

        // 情况 1
        // 已经记录的位点
        // insert (1, 1, 1)，当前: (1, 1, 1)
        // insert (2, 2, 2)，当前: (1, 1, 1), (2, 2, 2)
        // update (1, 1, 1) to (1, 3, 1), 当前: (1, 3, 1), (2, 2, 2)
        // update (2, 2, 2) to (2, 1, 2), 当前: (1, 3, 1), (2, 1, 2)
        // 实际执行完的地方

        // stop runner
        super.after();
        // start runner
        setupService(position0);
        runnerThread.start();
        wait(WAIT_TASK_SECOND * 2);
        checkBackTrackingTestResult(selectSql);

        // 情况 2
        // insert (1, 1, 1)，当前: (1, 1, 1)
        // 已经记录的位点
        // insert (2, 2, 2)，当前: (1, 1, 1), (2, 2, 2)
        // update (1, 1, 1) to (1, 3, 1), 当前: (1, 3, 1), (2, 2, 2)
        // update (2, 2, 2) to (2, 1, 2), 当前: (1, 3, 1), (2, 1, 2)
        // 实际执行完的地方

        // stop runner
        super.after();
        // start runner
        setupService(position1);
        runnerThread.start();
        wait(WAIT_TASK_SECOND * 2);
        checkBackTrackingTestResult(selectSql);

        // 情况 3
        // insert (1, 1, 1)，当前: (1, 1, 1)
        // insert (2, 2, 2)，当前: (1, 1, 1), (2, 2, 2)
        // 已经记录的位点
        // update (1, 1, 1) to (1, 3, 1), 当前: (1, 3, 1), (2, 2, 2)
        // update (2, 2, 2) to (2, 1, 2), 当前: (1, 3, 1), (2, 1, 2)
        // 实际执行完的地方

        // stop runner
        super.after();
        // start runner
        setupService(position2);
        runnerThread.start();
        wait(WAIT_TASK_SECOND * 2);
        checkBackTrackingTestResult(selectSql);

        // 情况 4
        // insert (1, 1, 1)，当前: (1, 1, 1)
        // insert (2, 2, 2)，当前: (1, 1, 1), (2, 2, 2)
        // update (1, 1, 1) to (1, 3, 1), 当前: (1, 3, 1), (2, 2, 2)
        // 已经记录的位点
        // update (2, 2, 2) to (2, 1, 2), 当前: (1, 3, 1), (2, 1, 2)
        // 实际执行完的地方

        // stop runner
        super.after();
        // start runner
        setupService(position3);
        runnerThread.start();
        wait(WAIT_TASK_SECOND * 2);
        checkBackTrackingTestResult(selectSql);
    }

    private void setupService(BinlogPosition position) throws Exception {
        setupService(channel, position, null);
        rplTaskRunner = new RplTaskRunner(getTaskId(channel));
        runnerThread = new Thread(() -> rplTaskRunner.start());
    }

    private void checkBackTrackingTestResult(String selectSql) {
        List<String> fields = Arrays.asList("f0", "f1", "f2");
        List<Map<String, String>> dstRes = execQuery(dstConn, selectSql, fields);
        Assert.assertEquals(dstRes.size(), 2);
        Assert.assertEquals(dstRes.get(0).get("f0"), "1");
        Assert.assertEquals(dstRes.get(0).get("f1"), "3");
        Assert.assertEquals(dstRes.get(0).get("f2"), "1");
        Assert.assertEquals(dstRes.get(1).get("f0"), "2");
        Assert.assertEquals(dstRes.get(1).get("f1"), "1");
        Assert.assertEquals(dstRes.get(1).get("f2"), "2");
    }
}
