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
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/4/22 16:43
 * @since 5.0.0.0
 */
@Ignore
public class TransferTest extends TestBase {

    private String dbName = "rpl_transfer_test";
    private boolean isPolarx = false;
    private int runSeconds = 60;
    private int connectionCount = 20;

    @Before
    public void before() throws Exception {
        channel = "transferTest";
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void transferTest() throws Exception {
        execUpdate(srcConn, "drop database if exists rpl_transfer_test", null);
        execUpdate(srcConn, "create database rpl_transfer_test", null);

        runnerThread.start();
        wait(WAIT_TASK_SECOND);

        int accountCount = 100;
        int initialAmount = 1000;

        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", connectionCount + "");
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?autoReconnect=true&useUnicode=true&createDatabaseIfNotExist=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
            srcHostInfo.getHost(),
            srcHostInfo.getPort(),
            dbName);

        // 创建连接
        List<Connection> connectionList = Lists.newArrayListWithCapacity(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            Connection connection = DriverManager
                .getConnection(url, srcHostInfo.getUserName(), srcHostInfo.getPassword());
            connectionList.add(connection);
        }

        // 准备数据
        //PrepareData.setPolarx(isPolarx);
        //PrepareData.init(connectionList.get(0), accountCount, initialAmount);

        // 启动转账
        //Bank bank = new Bank(accountCount, initialAmount, false);
        //bank.setPolarx(isPolarx);
        //new Thread(() -> bank.startWork(connectionList)).start();
        //wait(runSeconds);
        //bank.stop();

        // 校验结果
        wait(1);
        List<String> fields = Arrays.asList("id", "balance");
        String sql = String.format("select * from %s.accounts order by id", dbName);

        List<Map<String, String>> srcRes = execQuery(srcConn, sql, fields);
        List<Map<String, String>> dstRes = execQuery(dstConn, sql, fields);
        Assert.assertEquals(srcRes.size(), dstRes.size());
        for (int i = 0; i < srcRes.size(); i++) {
            Map<String, String> srcRecord = srcRes.get(i);
            Map<String, String> dstRecord = dstRes.get(i);
            Assert.assertEquals(srcRecord.get("id"), dstRecord.get("id"));
            Assert.assertEquals(srcRecord.get("balance"), dstRecord.get("balance"));
        }
    }
}
