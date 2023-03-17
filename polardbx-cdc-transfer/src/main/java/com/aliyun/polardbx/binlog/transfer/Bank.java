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
package com.aliyun.polardbx.binlog.transfer;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Bank {

    private static final Logger logger = LoggerFactory.getLogger(Bank.class);
    private final boolean useTSO;
    private int accountCount;
    private AtomicLong nextSequence = new AtomicLong(0);
    private int initialBalance;
    private boolean start = false;
    private AtomicLong counter = new AtomicLong(0);
    private long interval = 15;
    private long lastTimestamp = System.currentTimeMillis();
    private Random random = new Random(System.currentTimeMillis());
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });

    private Executor executor;
    private boolean isPolarx = true;

    public Bank(int accountCount, int initialBalance, boolean useTSO) {
        this.accountCount = accountCount;
        this.initialBalance = initialBalance;
        this.useTSO = useTSO;
    }

    public void setPolarx(boolean isPolarx) {
        this.isPolarx = isPolarx;
    }

    private void switchPolicy(Connection connection) throws SQLException {
        if (!isPolarx) {
            return;
        }

        PreparedStatement ps = connection.prepareStatement("set drds_transaction_policy=?");
        if (useTSO) {
            ps.setString(1, "TSO");
        } else {
            ps.setString(1, "XA");
        }
        ps.executeUpdate();
        ps.close();
    }

    public void startWork(List<Connection> connectionList) {
        List<Connection> cpList = Lists.newArrayList(connectionList);
        Connection peekOne = cpList.remove(0);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                check(peekOne);
            } catch (Exception e) {
                logger.error("check summary failed!", e);
            }
        }, interval, interval, TimeUnit.SECONDS);
        start = true;

        executor = Executors.newFixedThreadPool(connectionList.size(), r -> {
            Thread t = new Thread(r);
            return t;
        });

        for (Connection connection : connectionList) {
            executor.execute(() -> {
                while (start) {
                    try {
                        Transfer transfer = roundTransfer();
                        doTransfer(transfer, connection);
                    } catch (Exception e) {
                        logger.error("do transfer failed! ", e);
                    }
                }
            });
        }
    }

    public void stop() {
        this.start = false;
    }

    private void updateBalance(int src, int amount, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE accounts SET balance = balance + ? where id = ?");
        ps.setInt(1, amount);
        ps.setInt(2, src);
        ps.executeUpdate();
        ps.close();
    }

    private int round(int accountCount) {
        return (int) nextSequence.addAndGet(random.nextInt(9) + 1) % accountCount;
    }

    private Transfer roundTransfer() {
        int src, dst;
        src = round(accountCount);
        do {
            dst = round(accountCount);
        } while (src == dst);
        return new Transfer(src, dst, 1);
    }

    private void doTransfer(Transfer transfer, Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            switchPolicy(connection);
            int srcBalance = queryBalance(transfer.getSrc(), connection);
            if (srcBalance < transfer.getAmount()) {
                String errorMsg = "insufficient balance, balance " + srcBalance + ", required " + transfer.getAmount();
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            updateBalance(transfer.getSrc(), -transfer.getAmount(), connection);
            updateBalance(transfer.getDst(), transfer.getAmount(), connection);
            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
            connection.rollback();
        } finally {
            counter.incrementAndGet();
        }
    }

    private int queryBalance(int id, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT balance FROM accounts WHERE id = ? FOR UPDATE");
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        int srcBalance = 0;
        while (rs.next()) {
            srcBalance = rs.getInt("balance");
        }
        rs.close();
        ps.close();
        return srcBalance;
    }

    private void check(Connection connection) throws SQLException {
        //        PreparedStatement ps = connection.prepareStatement("SELECT sum(balance) FROM accounts");
        //        ResultSet rs = ps.executeQuery();
        //        int sum = 0;
        //        while (rs.next()) {
        //            int balance = rs.getInt(1);
        //            sum += balance;
        //        }
        //
        //        rs.close();
        //        ps.close();
        //        int expected = accountCount * initialBalance;

        //        if (sum != expected) {
        //            long counter = Bank.this.counter.getAndSet(0);
        //            throw new RuntimeException("unexpected total balance : " + sum + " tps : (" + counter / interval + ")/s");
        //        } else {
        long now = System.currentTimeMillis();
        long counter = this.counter.getAndSet(0);
        logger.info("tps : (" + counter / TimeUnit.MILLISECONDS.toSeconds(now - lastTimestamp) + ")/s");
        //        }

    }
}
