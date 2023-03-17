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

import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/4/28 11:30
 * @since 5.0.0.0
 */
public class DmlPerfTest extends TestBase {
    private static final String DB_1 = "perf_db1";
    private static final String DB_2 = "perf_db2";
    private static final String DB_3 = "perf_db3";
    private static final String DB_4 = "perf_db4";

    private static final String TB_1 = "t1";
    private static final String TB_2 = "t2";
    private static final String TB_3 = "t3";
    private static final String TB_4 = "t4";
    private static final String TB_5 = "t5";
    private static final String TB_6 = "t6";
    private static final String TB_7 = "t7";
    private static final String TB_8 = "t8";

    private static final List<String> ALL_DBS = Arrays.asList(DB_1, DB_2, DB_3, DB_4);
    private static final List<String> ALL_TBS = Arrays.asList(TB_1, TB_2, TB_3, TB_4, TB_5, TB_6, TB_7, TB_8);

    private static final int COUNT_IN_ONE_TB = 1000;
    private static final int WAIT_DML_SECOND = 60;

    @Before
    public void before() throws Exception {
        channel = "dml_perf_test";
        super.before();
        initDbsAndTables();
        initBinlogPosition = getLastBinlogPosition();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    private void initDbsAndTables() {
        List<Connection> connections = Arrays.asList(srcConn, dstConn);

        for (String db : ALL_DBS) {
            String dropSql = "drop database if exists " + db;
            String createSql = "create database " + db;

            for (Connection conn : connections) {
                execUpdate(conn, dropSql, null);
                execUpdate(conn, createSql, null);

                for (String tb : ALL_TBS) {
                    String createTable = String
                        .format(
                            "create table %s.%s(id int, f1 int, f2 int, f3 int, f4 int, f5 int, f6 int, f7 int, f8 int, f9 int, f10 int, f11 int, f12 int, f13 int, f14 int, f15 int, f16 int, f17 int, f18 int, f19 int, f20 int, f21 int, f22 int, f23 int, f24 int, f25 int, f26 int, f27 int, f28 int, f29 int, f30 int, primary key(id))",
                            db, tb);
                    execUpdate(conn, createTable, null);
                }
            }
        }
    }

    @Test
    public void test() throws Exception {
        // mysql_bin.000017:7257190
        setupService(channel, initBinlogPosition, null);
        ExecutorService executorService = Executors.newFixedThreadPool(10, new NamedThreadFactory(""));
        List<Future> futures = new ArrayList<>();

        for (String db : ALL_DBS) {
            for (String tb : ALL_TBS) {
                final String table = db + "." + tb;
                Future future = executorService.submit(() -> {
                    int i = 0;
                    while (i < COUNT_IN_ONE_TB) {
                        StringBuilder sb = new StringBuilder();

                        String values =
                            "('%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d', '%d'),";
                        int j = 0;
                        for (; j < 1000; j++) {
                            int field = i + j;
                            sb.append(String
                                .format(values, field, field, field, field, field, field, field, field, field, field,
                                    field, field, field, field, field, field, field, field, field, field, field, field,
                                    field, field, field, field, field, field, field, field, field));
                        }
                        i += j + 1;

                        String sql = String.format("insert into %s values %s", table, sb);
                        sql = sql.substring(0, sql.length() - 1);

                        execUpdate(srcConn, sql, null);
                    }
                });
                futures.add(future);
            }
        }

        checkFinish(futures);

        futures.clear();
        for (String db : ALL_DBS) {
            for (String tb : ALL_TBS) {
                final String table = db + "." + tb;
                Future future = executorService.submit(() -> {
                    String sql =
                        String.format("update %s set f1=%d where id<%d", table, COUNT_IN_ONE_TB * 2,
                            COUNT_IN_ONE_TB / 2);
                    sql = sql.substring(0, sql.length() - 1);
                    execUpdate(srcConn, sql, null);
                });
                futures.add(future);
            }
        }

        checkFinish(futures);

        runnerThread.start();
        wait(WAIT_DML_SECOND);
    }
}
