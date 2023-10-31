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
import com.aliyun.polardbx.rpl.common.RplConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/4/22 17:01
 * @since 5.0.0.0
 */
@Ignore
public class DmlE2ETest extends TestBase {

    private static final String DB_1 = "filter_db1";
    private static final String DB_1_R = "filter_db1_rewrite";
    private static final String DB_2 = "filter_db2";
    private static final String DB_3 = "filter_db3";
    private static final String DB_3_R = "filter_db3_rewrite";
    private static final String DB_4 = "filter_db4";

    private static final String TB_1 = "t1_1";
    private static final String TB_2 = "t2_1";

    private static final String ID = "id";
    private static final String VALUE = "value";
    private static final List<String> FIELDS = Arrays.asList(ID, VALUE);
    private static final List<String> ALL_DBS = Arrays.asList(DB_1, DB_1_R, DB_2, DB_3, DB_3_R, DB_4);
    private static final List<String> ALL_TBS = Arrays.asList(TB_1, TB_2);

    private static final int INTERVAL = 3;
    private static final int WAIT_DML_SECOND = 10;

    @Before
    public void before() throws Exception {
        channel = "rpl_replicate_filter_test";
        super.before();
        initDbsAndTables();
        initBinlogPosition = getLastBinlogPosition();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void doDb_rewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, String.format("(%s,%s)", DB_1_R, DB_2));
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void ignoreDb_rewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, String.format("(%s,%s)", DB_3_R, DB_4));
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_DML_SECOND);

        doTest();
        wait(5);

        checkTwoDstsSame();
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void doTables_rewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, String.format("(%s.%s, %s.%s)", DB_1_R, TB_1, DB_2, TB_2));
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_DDL_SECOND * 3);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsInit(DB_1_R, TB_2);
        checkDstEqualsInit(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void ignoreTables_rewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE,
            String.format("(%s.%s, %s.%s)", DB_3_R, TB_1, DB_4, TB_2));
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsChanged(DB_3_R, TB_2);
        checkDstEqualsChanged(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void wildDoTables_rewriteDbs_1() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE,
            String.format("('%s.%%', '%s.%%')", escape(DB_1_R), escape(DB_2)));
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void wildDoTables_rewriteDbs_2() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE,
            String.format("('%s.%s%%', '%s.%s%%')", escape(DB_1_R), escape(TB_1), escape(DB_2), escape(TB_2)));
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsInit(DB_1_R, TB_2);
        checkDstEqualsInit(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void wildIgnoreTables_rewriteDbs_1() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE,
            String.format("('%s.%%', '%s.%%')", escape(DB_3_R), escape(DB_4)));
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsInit(DB_3_R, TB_2);
        checkDstEqualsInit(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    @Test
    public void wildIgnoreTables_rewriteDbs_2() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE,
            String.format("('%s.%s%%', '%s.%s%%')", escape(DB_3_R), escape(TB_1), escape(DB_4), escape(TB_2)));
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB,
            String.format("((%s, %s),(%s, %s))", DB_1, DB_1_R, DB_3, DB_3_R));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        doTest();
        wait(WAIT_DML_SECOND);

        checkTwoDstsSame();
        checkDstEqualsInit(DB_1, TB_1);
        checkDstEqualsInit(DB_1, TB_2);
        checkDstEqualsChanged(DB_1_R, TB_1);
        checkDstEqualsChanged(DB_1_R, TB_2);
        checkDstEqualsChanged(DB_2, TB_1);
        checkDstEqualsChanged(DB_2, TB_2);
        checkDstEqualsInit(DB_3, TB_1);
        checkDstEqualsInit(DB_3, TB_2);
        checkDstEqualsInit(DB_3_R, TB_1);
        checkDstEqualsChanged(DB_3_R, TB_2);
        checkDstEqualsChanged(DB_4, TB_1);
        checkDstEqualsInit(DB_4, TB_2);
    }

    private void initDbsAndTables() {
        List<Connection> connections = Arrays.asList(srcConn, dstConn, mysqlDstConn);

        for (String db : ALL_DBS) {
            String dropSql = "drop database if exists " + db;
            String createSql = "create database " + db;

            for (Connection conn : connections) {
                execUpdate(conn, dropSql, null);
                execUpdate(conn, createSql, null);

                for (String tb : ALL_TBS) {
                    String createTable = String
                        .format("create table %s.%s(id int, value int, primary key(id))", db, tb);
                    execUpdate(conn, createTable, null);
                    for (int i = 0; i < INTERVAL * 2; i++) {
                        String insert = String.format("insert into %s.%s values(%d,%d)", db, tb, i, i);
                        execUpdate(conn, insert, null);
                    }
                }
            }
        }
    }

    private void doTest() {
        List<String> dbs = Arrays.asList(DB_1, DB_2, DB_3, DB_4);
        for (String db : dbs) {
            for (String tb : ALL_TBS) {
                for (int i = INTERVAL * 2; i < INTERVAL * 3; i++) {
                    String insert = String.format("insert into %s.%s values(%d,%d)", db, tb, i, i);
                    execUpdate(srcConn, insert, null);
                }
                for (int i = 0; i < INTERVAL; i++) {
                    String delete = String.format("delete from %s.%s where id = %d", db, tb, i);
                    execUpdate(srcConn, delete, null);
                }
                for (int i = INTERVAL; i < INTERVAL * 2; i++) {
                    String update = String.format("update %s.%s set value=%d where id = %d", db, tb, i + INTERVAL, i);
                    execUpdate(srcConn, update, null);
                }
            }
        }
    }

    private void checkTwoDstsSame() {
        for (String db : ALL_DBS) {
            for (String tb : ALL_TBS) {
                checkTwoDstsSame(db, tb, FIELDS, ID);
            }
        }
    }

    private void checkDstEqualsInit(String db, String tb) {
        String dstSql = String.format("select * from %s.%s order by id", db, tb);
        List<Map<String, String>> dstRes = execQuery(dstConn, dstSql, FIELDS);
        Assert.assertEquals(dstRes.size(), INTERVAL * 2);
        for (int i = 0; i < INTERVAL * 2; i++) {
            Map<String, String> dstRecord = dstRes.get(i);
            Assert.assertEquals(dstRecord.get(ID), String.valueOf(i));
            Assert.assertEquals(dstRecord.get(VALUE), String.valueOf(i));
        }
    }

    private void checkDstEqualsChanged(String db, String tb) {
        String dstSql = String.format("select * from %s.%s order by id", db, tb);
        List<Map<String, String>> dstRes = execQuery(dstConn, dstSql, FIELDS);
        Assert.assertEquals(dstRes.size(), INTERVAL * 2);

        // 被 delete 的记录，d: INTERVAL 到 2 * INTERVAL

        // 被 update 的记录, id: INTERVAL 到 2 * INTERVAL, value == id + INTERVAL
        for (int i = 0; i < INTERVAL; i++) {
            Map<String, String> dstRecord = dstRes.get(i);
            Assert.assertEquals(dstRecord.get(ID), String.valueOf(i + INTERVAL));
            Assert.assertEquals(dstRecord.get(VALUE), String.valueOf(i + INTERVAL + INTERVAL));
        }
        // 被 insert 的记录, id: 2 * INTERVAL 到 3 * INTERVAL, value == id
        for (int i = INTERVAL; i < INTERVAL * 2; i++) {
            Map<String, String> dstRecord = dstRes.get(i);
            Assert.assertEquals(dstRecord.get(ID), String.valueOf(i + INTERVAL));
            Assert.assertEquals(dstRecord.get(VALUE), String.valueOf(i + INTERVAL));
        }
    }

    private String escape(String name) {
        return name.replace("_", "\\_");
    }
}
