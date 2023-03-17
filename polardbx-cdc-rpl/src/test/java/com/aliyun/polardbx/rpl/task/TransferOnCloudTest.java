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

import com.aliyun.polardbx.binlog.transfer.Bank;
import com.aliyun.polardbx.binlog.transfer.PrepareData;
import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * @author shicai.xsc 2021/6/17 16:32
 * @since 5.0.0.0
 */
public class TransferOnCloudTest extends TestBase {

    private int connectionCount = 10;
    private int accountCount = 100;
    private int initialAmount = 1000;
    private int runSeconds = 10;
    private String dbName = "rpl_transfer_test";
    private boolean isPolarx = true;

    @Before
    public void before() {
    }

    @Test
    public void test() throws Exception {
        srcHostInfo = new HostInfo();
        srcHostInfo.setHost("192.168.1.1");
        srcHostInfo.setPort(3306);
        srcHostInfo.setUserName("root");
        srcHostInfo.setPassword("Drds123456");

        DataSource srcDs = createDataSource(srcHostInfo, "");
        Connection srcConn = srcDs.getConnection();

        execUpdate(srcConn, "drop database if exists " + dbName, null);
        execUpdate(srcConn, "create database " + dbName, null);

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
        PrepareData.setPolarx(isPolarx);
        PrepareData.init(connectionList.get(0), accountCount, initialAmount);

        // 启动转账
        Bank bank = new Bank(accountCount, initialAmount, true);
        bank.setPolarx(isPolarx);
        new Thread(() -> bank.startWork(connectionList)).start();
        wait(runSeconds);
        bank.stop();
    }
}
