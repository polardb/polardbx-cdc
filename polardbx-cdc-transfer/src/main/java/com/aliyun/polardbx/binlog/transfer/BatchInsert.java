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
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BatchInsert {

    private static final Logger logger = LoggerFactory.getLogger(BatchInsert.class);

    private static String tableDDL =
        "CREATE TABLE batch_insert(id bigint primary key auto_increment, gmt_create datetime(3), name varchar(20)) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 10";

    private static String insertSqlFormat = "insert into batch_insert(gmt_create, name) values";
    private static String valuesFormat = "(NOW(), '%s')";

    private static int batchSize = 256;

    private static AtomicLong counter = new AtomicLong(0);

    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        String dbHost = "";
        String dbName = "remove_full_progress123";
        String username = "remove_full_progress123";
        String password = "remove_full_progress123";
        int port = 3306;
        int poolCount = 10;

        boolean useTSO = false;

        Class.forName("com.mysql.jdbc.Driver");

        logger.info("use TSO " + useTSO);
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?autoReconnect=true&useUnicode=true&createDatabaseIfNotExist=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
            dbHost,
            port,
            dbName);
        logger.info("init with url : " + url);

        List<Connection> connectionList = Lists.newArrayList();
        for (int i = 0; i < poolCount; i++) {
            Connection connection = DriverManager.getConnection(url, username, password);
            connectionList.add(connection);
            logger.info("init connection " + i);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                for (Connection conn : connectionList) {
                    try {
                        conn.close();
                    } catch (SQLException throwables) {

                    }
                }
            }
        });

        Executor executor = Executors.newFixedThreadPool(poolCount, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });

        for (Connection connection : connectionList) {
            executor.execute(() -> {
                logger.info("start thread : " + Thread.currentThread().getName());
                while (true) {
                    try {
                        batchInsert(connection);
                        counter.addAndGet(batchSize);
                    } catch (SQLException e) {
                        logger.error("batch insert failed!", e);
                    }
                }
            });
        }

        long last = System.currentTimeMillis();
        while (true) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {

            }
            long now = System.currentTimeMillis();
            long mc = counter.getAndSet(0);
            logger.info("pool size : " + poolCount + " , batch size " + batchSize + ", tps : "
                + mc / Math.max(TimeUnit.MILLISECONDS.toSeconds(now - last), 1));
            last = now;
        }
    }

    private static void batchInsert(Connection connection) throws SQLException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(insertSqlFormat);
        for (int i = 0; i < batchSize; i++) {
            stringBuilder.append(String.format(valuesFormat, RandomStringUtils.randomAlphanumeric(4)));
            if (i < batchSize - 1) {
                stringBuilder.append(",");
            }
        }
        PreparedStatement ps = connection.prepareStatement(stringBuilder.toString());
        ps.executeUpdate();
        ps.close();
    }
}
