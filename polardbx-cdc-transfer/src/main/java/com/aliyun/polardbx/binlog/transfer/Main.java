/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.transfer;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static String getValue(Map<String, String> paramMap, String key, String defaultValue) {
        String value = paramMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static Integer getValue(Map<String, String> paramMap, String key, Integer defaultValue) {
        String value = paramMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Integer.valueOf(value);
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException {

        Map<String, String> paramHashMap = new HashMap<>();
        for (String arg : args) {
            String kv[] = arg.split("=");
            paramHashMap.put(kv[0], kv[1]);
        }

        int accountCount = 100;
        int initialAmount = 100000;
        int poolAccount = getValue(paramHashMap, "threadNum", 10);

        boolean drds = false;

        if (drds) {
            paramHashMap.put("ip", "127.0.0.1");
            paramHashMap.put("db", "");
            paramHashMap.put("user", "");
            paramHashMap.put("pwd", "");
        } else {
            paramHashMap.put("ip", "127.0.0.1");
            paramHashMap.put("db", "transfer");
            paramHashMap.put("user", "polardbx_root");
            paramHashMap.put("pwd", "123456");
        }
        String dbHost = getValue(paramHashMap, "ip", "127.0.0.1");
        String dbName = getValue(paramHashMap, "db", "");
        String username = getValue(paramHashMap, "user", "polardbx_root");
        String password = getValue(paramHashMap, "pwd", "123456");
        int port = getValue(paramHashMap, "port", 8527);

        boolean usetso = Boolean.parseBoolean(getValue(paramHashMap, "useTSO", "true"));

        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", poolAccount + "");
        Class.forName("com.mysql.jdbc.Driver");

        logger.info("use TSO " + usetso);
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?autoReconnect=true&useUnicode=true&createDatabaseIfNotExist=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
            dbHost,
            port,
            dbName);
        logger.info("init with url : " + url);
        List<Connection> connectionList = Lists.newArrayListWithCapacity(poolAccount);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                for (Connection connection : connectionList) {
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException throwables) {

                        }
                    }
                }
            }
        });
        logger.info("prepare connection!");
        try {
            for (int i = 0; i < poolAccount; i++) {
                Connection connection = DriverManager.getConnection(url, username, password);
                logger.info("init connection " + i);
                connectionList.add(connection);
            }
//            PrepareData.init(connectionList.get(0), accountCount, initialAmount);
            logger.info("data prepared success!");
            Bank bank = new Bank(accountCount, initialAmount, usetso);
            logger.info("start transfer!");
            bank.setPolarx(!drds);
            bank.startWork(connectionList);
            LockSupport.park();
        } finally {
            for (Connection connection : connectionList) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {

                    }
                }
            }
        }

    }
}
