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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.RplConstants;

/**
 * @author shicai.xsc 2021/4/16 00:06
 * @since 5.0.0.0
 */
public class DdlE2ETest extends TestBase {

    private final static String DB_1 = "db1";
    private final static String DB_2 = "db2";
    private final static String DB_3 = "db3";

    @Before
    public void before() throws Exception {
        channel = "ddlTest";
        super.before();
        List<String> dbs = Arrays.asList(DB_1, DB_2, DB_3);
        for (String db : dbs) {
            execUpdate(srcConn, "drop database if exists " + db, null);
            execUpdate(dstConn, "drop database if exists " + db, null);
            execUpdate(mysqlDstConn, "drop database if exists " + db, null);
        }
        wait(WAIT_DDL_SECOND);
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    /**
     * 不跨库，没有过滤条件
     */
    @Test
    public void basic() throws Exception {
        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));

        // use db_1
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
    }

    /**
     * 不跨库，有 doDb 过滤
     */
    @Test
    public void doDb() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, String.format("(%s)", DB_1));
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // use db_1
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(dstConn, "create database " + DB_2, null);
        execUpdate(mysqlDstConn, "create database " + DB_2, null);

        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
    }

    /**
     * 不跨库，有 ignoreDb 过滤
     */
    @Test
    public void ignoreDb() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, String.format("(%s)", DB_2));
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // drop database
        execUpdate(dstConn, "create database " + DB_2, null);
        execUpdate(mysqlDstConn, "create database " + DB_2, null);

        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
    }

    /**
     * 不跨库，有 wildDoTables 过滤，不过滤 create database DB_1
     */
    @Test
    public void wildDoTables_1() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, String.format("('%s.%%', '%s.%%')", DB_3, DB_1));
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // drop database
        execUpdate(dstConn, "create database " + DB_2, null);
        execUpdate(mysqlDstConn, "create database " + DB_2, null);

        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
    }

    /**
     * 不跨库，有 wildDoTables 过滤，过滤掉 create database DB_1
     */
    @Test
    public void wildDoTables_2() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, String.format("('%s.t1%%', '%s.t1%%')", DB_3, DB_1));
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // create database in dst manually
        execUpdate(dstConn, "create database " + DB_1, null);
        execUpdate(mysqlDstConn, "create database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        // use db
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        execUpdate(srcConn, "create table t2_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t2_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t2_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        execUpdate(srcConn, "rename table t2_1 to t2;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        Assert.assertFalse(res.contains("t2"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        Assert.assertFalse(res.contains("t2"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        execUpdate(srcConn, "alter table t2 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        execUpdate(srcConn, "alter table t2 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        execUpdate(srcConn, "create index idx_f1 on t2(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        execUpdate(srcConn, "alter table t2 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        execUpdate(srcConn, "drop index idx_f1 on t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        execUpdate(srcConn, "alter table t2 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        execUpdate(srcConn, "insert into t2 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        execUpdate(srcConn, "truncate table t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        execUpdate(srcConn, "drop table t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
    }

    /**
     * 不跨库，有 wildIgnoreTables 过滤，不过滤 create database DB_1
     */
    @Test
    public void wildIgnoreTables_1() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, String.format("('%s.%%', '%s.%%')", DB_2, DB_3));
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // drop database
        execUpdate(dstConn, "create database " + DB_2, null);
        execUpdate(mysqlDstConn, "create database " + DB_2, null);

        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
    }

    /**
     * 不跨库，有 wildIgnoreTable 过滤，过滤掉 create database DB_1
     */
    @Test
    public void wildIgnoreTables_2() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, String.format("('%s.t2%%', '%s.t2%%')", DB_1, DB_2));
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, "");
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));

        // use db
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        execUpdate(srcConn, "create table t2_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t2_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t2_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        execUpdate(srcConn, "rename table t2_1 to t2;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        Assert.assertFalse(res.contains("t2"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        Assert.assertFalse(res.contains("t2"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        execUpdate(srcConn, "alter table t2 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        execUpdate(srcConn, "alter table t2 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        execUpdate(srcConn, "create index idx_f1 on t2(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        execUpdate(srcConn, "alter table t2 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        execUpdate(srcConn, "drop index idx_f1 on t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        execUpdate(srcConn, "alter table t2 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        execUpdate(srcConn, "insert into t2 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        execUpdate(srcConn, "truncate table t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        execUpdate(srcConn, "drop table t2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
    }

    /**
     * 不跨库，有 rewriteDbs，除 create database 和 drop database 外，在源库 DB_1 上执行的 DDL，会在目标库
     * DB_2 上执行。而 create database 和 drop database 会原样执行。
     */
    @Test
    public void rewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, String.format("((%s, %s))", DB_1, DB_2));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        // rewriteDbs won't affect create database
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // create DB_2 manually
        execUpdate(dstConn, "create database " + DB_2, null);
        execUpdate(mysqlDstConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        // use db_1
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_2, null);
        execUpdate(mysqlDstConn, "use " + DB_2, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
    }

    /**
     * 不跨库，有 rewriteDbs，在源库 DB_1 上执行的 DDL，在目标库 DB_2 上发生。 有 filter。rewriteDbs 对
     * filter 的影响仅在 ReplicaFilter 的 ignoreEvent(String schema, String tbName,
     * DBMSAction action) 入口处，故只写这一个测试用例即可。 rewriteDbs 对 create database 和 drop
     * database 不生效。
     */
    @Test
    public void rewriteDbs_DoDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, String.format("(%s)", DB_2));
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, String.format("((%s, %s))", DB_1, DB_2));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));

        // use db_1
        execUpdate(srcConn, "use " + DB_1, null);
        execUpdate(dstConn, "use " + DB_2, null);
        execUpdate(mysqlDstConn, "use " + DB_2, null);

        // create table
        execUpdate(srcConn, "create table t1_1(id int, f1 int, f2 int)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, "rename table t1_1 to t1;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, "alter table t1 add column f3 int;", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, "alter table t1 drop column f3", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, "create index idx_f1 on t1(f1)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, "alter table t1 add index idx_f2(f2)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, "drop index idx_f1 on t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, "alter table t1 drop index idx_f2", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, "truncate table t1", null);
        execUpdate(srcConn, "insert into t1 values(1,2,3)", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, "truncate table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, "drop table t1", null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
    }

    /**
     * 跨库，没有 tbFilter，没有 dbFilter，没有 rewriteDbs，在源库 DB_1 上执行的 DDL，在目标库 DB_1 上发生
     */
    @Test
    public void crossDb_Basic() throws Exception {
        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));

        // use db_1, 跨库执行 ddl
        execUpdate(srcConn, "use information_schema", null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // create table
        execUpdate(srcConn, String.format("create table %s.t1_1(id int, f1 int, f2 int)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, String.format("rename table %s.t1_1 to %s.t1;", DB_1, DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, String.format("alter table %s.t1 add column f3 int;", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, String.format("alter table %s.t1 drop column f3", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, String.format("create index idx_f1 on %s.t1(f1)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, String.format("alter table %s.t1 add index idx_f2(f2)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, String.format("drop index idx_f1 on %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, String.format("alter table %s.t1 drop index idx_f2", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(srcConn, String.format("insert into %s.t1 values(1,2,3)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, String.format("truncate table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, String.format("drop table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
    }

    /**
     * 跨库，有 rewriteDbs，在源库 DB_1 上执行的 DDL，在目标库 DB_1 上发生
     */
    @Test
    public void crossDb_RewriteDbs() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, String.format("((%s, %s))", DB_1, DB_2));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        // rewriteDbs won't affect create database
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));

        // use db_1
        execUpdate(srcConn, "use information_schema", null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // DDL sql 应该发生在 DB_1
        // create table
        execUpdate(srcConn, String.format("create table %s.t1_1(id int, f1 int, f2 int)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, String.format("rename table %s.t1_1 to %s.t1;", DB_1, DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // alter table add column
        execUpdate(srcConn, String.format("alter table %s.t1 add column f3 int;", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));
        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertTrue(res.contains("f3"));

        // alter table drop column
        execUpdate(srcConn, String.format("alter table %s.t1 drop column f3", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));
        res = execQuery(dstConn, "desc t1", 1);
        Assert.assertFalse(res.contains("f3"));

        // create index
        execUpdate(srcConn, String.format("create index idx_f1 on %s.t1(f1)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f1`"));

        // alter add index
        execUpdate(srcConn, String.format("alter table %s.t1 add index idx_f2(f2)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertTrue(res.get(0).contains("KEY `idx_f2`"));

        // drop index
        execUpdate(srcConn, String.format("drop index idx_f1 on %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));
        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f1`"));

        // alter drop index
        execUpdate(srcConn, String.format("alter table %s.t1 drop index idx_f2", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));
        res = execQuery(dstConn, "show create table t1", 2);
        Assert.assertFalse(res.get(0).contains("KEY `idx_f2`"));

        // truncate table
        execUpdate(dstConn, String.format("insert into %s.t1 values(1,2,3)", DB_1), null);
        execUpdate(mysqlDstConn, String.format("insert into %s.t1 values(1,2,3)", DB_1), null);
        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);
        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() > 0);

        execUpdate(srcConn, String.format("truncate table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);
        res = execQuery(dstConn, "select * from t1", 1);
        Assert.assertTrue(res.size() == 0);

        // drop table
        execUpdate(srcConn, String.format("drop table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertFalse(res.contains(DB_2));
    }

    /**
     * 跨库，有 rewriteDbs，在源库 DB_1 上执行的 DDL，在目标库 DB_1 上发生。 有 filter。rewriteDbs 对 filter
     * 的影响仅在 ReplicaFilter 的 ignoreEvent(String schema, String tbName, DBMSAction
     * action) 入口处。 rewriteDbs 对 create database 和 drop database 不生效。
     */
    @Test
    public void crossDb_RewriteDbs_DoDbs_1() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, String.format("(%s)", DB_2));
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, String.format("((%s, %s))", DB_1, DB_2));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));

        // create DB_1 in dst manually
        execUpdate(dstConn, "create database " + DB_1, null);
        execUpdate(mysqlDstConn, "create database " + DB_1, null);

        // use information_schema
        execUpdate(srcConn, "use information_schema", null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // DDL sql 应该发生在 DB_1，但因为 srcConn 执行了 use information_schema，mysql 认为当前库是在
        // information_schema, 则过滤掉了 ddl。
        // create table
        execUpdate(srcConn, String.format("create table %s.t1_1(id int, f1 int, f2 int)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, String.format("rename table %s.t1_1 to %s.t1;", DB_1, DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertFalse(res.contains("t1"));

        // drop table
        execUpdate(srcConn, String.format("drop table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
    }

    /**
     * 跨库，有 rewriteDbs，在源库 DB_1 上执行的 DDL，在目标库 DB_1 上发生。 有 filter。rewriteDbs 对 filter
     * 的影响仅在 ReplicaFilter 的 ignoreEvent(String schema, String tbName, DBMSAction
     * action) 入口处，故只写这一个测试用例即可。 rewriteDbs 对 create database 和 drop database 不生效。
     */
    @Test
    public void crossDb_RewriteDbs_DoDbs_2() throws Exception {
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put(RplConstants.REPLICATE_DO_DB, String.format("(%s)", DB_2));
        filterParams.put(RplConstants.REPLICATE_IGNORE_DB, "");
        filterParams.put(RplConstants.REPLICATE_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_DO_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "");
        filterParams.put(RplConstants.REPLICATE_REWRITE_DB, String.format("((%s, %s))", DB_1, DB_2));
        setupService(channel, initBinlogPosition, filterParams);

        // setup compare replica
        setupMysqlReplica(mysqlDstConn, initBinlogPosition);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        List<String> res = null;

        // create database
        execUpdate(srcConn, "create database " + DB_1, null);
        execUpdate(srcConn, "create database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));
        res = execQuery(dstConn, "show databases", 1);
        Assert.assertFalse(res.contains(DB_1));
        Assert.assertTrue(res.contains(DB_2));

        // create DB_1 in dst manually
        execUpdate(dstConn, "create database " + DB_1, null);
        execUpdate(mysqlDstConn, "create database " + DB_1, null);

        // use db_2
        execUpdate(srcConn, "use " + DB_2, null);
        execUpdate(dstConn, "use " + DB_1, null);
        execUpdate(mysqlDstConn, "use " + DB_1, null);

        // DDL sql 应该发生在 DB_1，本来 DB_1 应该被过滤的，但 mysql 却没有过滤，因为 srcConn 执行时用了 use
        // DB_2，mysql 便认为当前 DB 是 DB_2，则该 DDL 没有被过滤。
        // create table
        execUpdate(srcConn, String.format("create table %s.t1_1(id int, f1 int, f2 int)", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertTrue(res.contains("t1_1"));

        // rename table
        execUpdate(srcConn, String.format("rename table %s.t1_1 to %s.t1;", DB_1, DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1_1"));
        Assert.assertTrue(res.contains("t1"));

        // drop table
        execUpdate(srcConn, String.format("drop table %s.t1", DB_1), null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(mysqlDstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));
        res = execQuery(dstConn, "show tables", 1);
        Assert.assertFalse(res.contains("t1"));

        // drop database
        execUpdate(srcConn, "drop database " + DB_1, null);
        execUpdate(srcConn, "drop database " + DB_2, null);
        wait(WAIT_DDL_SECOND);

        res = execQuery(dstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
        res = execQuery(mysqlDstConn, "show databases", 1);
        Assert.assertTrue(res.contains(DB_1));
    }
}
