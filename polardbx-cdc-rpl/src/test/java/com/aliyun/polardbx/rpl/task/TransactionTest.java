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

import com.aliyun.polardbx.rpl.RplTaskRunner;
import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.applier.DmlApplyHelper;
import com.aliyun.polardbx.rpl.applier.SqlContext;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author shicai.xsc 2021/3/18 11:12
 * @since 5.0.0.0
 */
@Ignore
public class TransactionTest extends TestBase {

    @Before
    public void before() throws Exception {
        channel = "transactionTest";
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void startTask_Test() throws Exception {
        RplTaskRunner runner = new RplTaskRunner(288);
        runner.start();
    }

    @Test
    public void insertData_Test() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(20, new NamedThreadFactory("test"));
        for (int i = 0; i < 20; i++) {
            final int j = i;
            executorService.submit(() -> {
                int start = 30000 + j * 2000000;
                Connection conn = srcDs.getConnection();
                conn.setAutoCommit(false);
                while (true) {
                    List<SqlContext> contexts = getSqlContexts1(start);
                    contexts.addAll(getSqlContexts2(start));
                    contexts.addAll(getSqlContexts3(start));
                    contexts.addAll(getSqlContexts4(start));
                    for (SqlContext context : contexts) {
                        DmlApplyHelper.execSqlContext(conn, context);
                    }
                    conn.commit();
                    start += 10;
                }
            });
        }
        Thread.sleep(10000000);
    }

    private List<SqlContext> getSqlContexts1(int start) {
        List<SqlContext> contexts = new ArrayList<>();

        for (int i = start; i < start + 10; i++) {
            SqlContext context1 = new SqlContext(
                "INSERT INTO `rpl`.`t1`(id,value) VALUES (?,?) ON DUPLICATE KEY UPDATE id=?,value=?",
                "rpl",
                "t1",
                Arrays.asList(i, i, i, i));
            SqlContext context2 = new SqlContext(
                "INSERT INTO `rpl`.`t2`(id,value) VALUES (?,?) ON DUPLICATE KEY UPDATE id=?,value=?",
                "rpl",
                "t2",
                Arrays.asList(i, i, i, i));
            SqlContext context3 = new SqlContext("UPDATE `rpl`.`t1` SET value= ? WHERE id=?",
                "rpl",
                "t1",
                Arrays.asList(i + 1, i));
            SqlContext context4 = new SqlContext("UPDATE `rpl`.`t2` SET value= ? WHERE id=?",
                "rpl",
                "t2",
                Arrays.asList(i + 1, i));
            contexts.add(context1);
            contexts.add(context2);
            contexts.add(context3);
            contexts.add(context4);
        }

        return contexts;
    }

    private List<SqlContext> getSqlContexts2(int start) {
        List<SqlContext> contexts = new ArrayList<>();

        for (int i = start; i < start + 10; i++) {
            SqlContext context1 = new SqlContext(
                "INSERT INTO `rpl`.`t3`(id,value) VALUES (?,?) ON DUPLICATE KEY UPDATE id=?,value=?",
                "rpl",
                "t3",
                Arrays.asList(i, i, i, i));
            SqlContext context2 = new SqlContext("UPDATE `rpl`.`t3` SET value= ? WHERE id=?",
                "rpl",
                "t3",
                Arrays.asList(i + 2, i));
            contexts.add(context1);
            contexts.add(context2);
        }

        return contexts;
    }

    private List<SqlContext> getSqlContexts3(int start) {
        List<SqlContext> contexts = new ArrayList<>();

        for (int i = start; i < start + 10; i++) {
            SqlContext context1 = new SqlContext(
                "INSERT INTO `rpl`.`t4`(id,value) VALUES (?,?) ON DUPLICATE KEY UPDATE id=?,value=?",
                "rpl",
                "t4",
                Arrays.asList(i, i, i, i));
            SqlContext context2 = new SqlContext("UPDATE `rpl`.`t4` SET value= ? WHERE id=?",
                "rpl",
                "t4",
                Arrays.asList(i + 3, i));
            SqlContext context3 = new SqlContext("UPDATE `rpl`.`t4` SET value= ? WHERE id=?",
                "rpl",
                "t4",
                Arrays.asList(i + 4, i));
            contexts.add(context1);
            contexts.add(context2);
            contexts.add(context3);
        }

        return contexts;
    }

    private List<SqlContext> getSqlContexts4(int start) {
        List<SqlContext> contexts = new ArrayList<>();

        for (int i = start; i < start + 5; i++) {
            SqlContext context1 = new SqlContext("DELETE FROM `rpl`.`t3` WHERE id=?", "rpl", "t3", Arrays.asList(i));
            SqlContext context2 = new SqlContext("DELETE FROM `rpl`.`t4` WHERE id=?", "rpl", "t4", Arrays.asList(i));
            contexts.add(context1);
            contexts.add(context2);
        }
        return contexts;
    }
}
