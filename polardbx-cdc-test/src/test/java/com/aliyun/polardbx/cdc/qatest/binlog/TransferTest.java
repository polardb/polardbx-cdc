/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.StreamHashUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author yudong
 * @since 2022/9/18
 **/
@Slf4j
public class TransferTest extends RplBaseTestCase {
    private final String DB_NAME = "cdc_transfer_test";
    private final String BINLOGX_TEST_TABLE = "binlogx_accounts";
    private final String BINLOG_TEST_TABLE = "binlog_accounts";
    private final String BINLOGX_TEST_TABLE_FULL_NAME = "`" + DB_NAME + "`.`" + BINLOGX_TEST_TABLE + "`";
    private final String BINLOG_TEST_TABLE_FULL_NAME = "`" + DB_NAME + "`.`" + BINLOG_TEST_TABLE + "`";

    private final int ACCOUNT_COUNT = 10000;
    private final int INITIAL_BALANCE = 1000;
    private final int ERROR_COUNT = 100;
    private final int TRANSFER_COUNT = 1000;
    private final AtomicLong nextSequence = new AtomicLong(0);
    private final Random random = new Random(System.currentTimeMillis());

    @Before
    public void prepareTable() throws SQLException {
        prepareTestDatabase(DB_NAME);
        String tb;
        if (usingBinlogX) {
            tb = BINLOGX_TEST_TABLE_FULL_NAME;
        } else {
            tb = BINLOG_TEST_TABLE_FULL_NAME;
        }
        createTable(tb);
        cleanTable(tb);
        initTable(tb);
    }

    private void createTable(String tb) throws SQLException {
        String CREATE_TABLE_SQL_FORMAT =
            "CREATE TABLE IF NOT EXISTS %s"
                + "(id INT PRIMARY KEY, "
                + "balance INT NOT NULL) "
                + "ENGINE=InnoDB  DBPARTITION BY HASH(id)";
        String sql = String.format(CREATE_TABLE_SQL_FORMAT, tb);
        try (Connection polardbxConnection = getPolardbxConnection(DB_NAME)) {
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }

    private void cleanTable(String tb) throws SQLException {
        String TRUNCATE_TABLE_SQL_FORMAT = "TRUNCATE TABLE %s";
        String sql = String.format(TRUNCATE_TABLE_SQL_FORMAT, tb);
        try (Connection polardbxConnection = getPolardbxConnection(DB_NAME)) {
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }

    private void initTable(String tb) throws SQLException {
        String INSERT_TABLE_SQL_FORMAT = "INSERT INTO %s VALUES %s";
        List<String> valueList = Lists.newArrayListWithCapacity(ACCOUNT_COUNT);
        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            valueList.add(String.format("(%d, %d)", i, INITIAL_BALANCE));
        }
        String insertSQL = String.format(INSERT_TABLE_SQL_FORMAT, tb,
            StringUtils.join(valueList, ","));
        try (Connection polardbxConnection = getPolardbxConnection(DB_NAME)) {
            JdbcUtil.executeSuccess(polardbxConnection, insertSQL);
        }
    }

    @Test
    public void transferTest() throws SQLException {
        startTransfer();
        sendTokenAndWait(CheckParameter.builder().build());
        checkBalance();
    }

    public void startTransfer() {
        log.info("start to do transfer.");
        int PARALLELISM = 10;
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM, Thread::new);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < PARALLELISM; i++) {
            Future<?> future = executor.submit(() -> {
                int transferCount = 0;
                int errorCount = 0;
                while (transferCount < TRANSFER_COUNT) {
                    Transfer transfer = createTransfer();
                    try {
                        doTransfer(transfer);
                        transferCount++;
                    } catch (SQLException e) {
                        errorCount++;
                        if (errorCount > ERROR_COUNT) {
                            throw new RuntimeException("transfer error.", e);
                        }
                    }
                }
            });
            futures.add(future);
        }
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Throwable e) {
                throw new RuntimeException("transfer error");
            }
        });
        log.info("finished to do transfer");
    }

    public void checkBalance() throws SQLException {
        log.info("start to check balance.");
        int totalBalance = ACCOUNT_COUNT * INITIAL_BALANCE;
        int srcTotalBalance = 0;
        int dstTotalBalance = 0;
        if (usingBinlogX) {
            HashLevel hashLevel = StreamHashUtil.getHashLevel(DB_NAME, BINLOGX_TEST_TABLE);
            if (hashLevel != HashLevel.RECORD) {
                int streamSeq = StreamHashUtil.getHashStreamSeq(DB_NAME, BINLOGX_TEST_TABLE);

                try (Connection srcConn = getPolardbxConnection(DB_NAME)) {
                    srcTotalBalance += calculateBalance(srcConn, BINLOGX_TEST_TABLE_FULL_NAME);
                }
                if (streamSeq == 0) {
                    try (Connection dstConn1 = getCdcSyncDbConnectionFirst()) {
                        dstTotalBalance += calculateBalance(dstConn1, BINLOGX_TEST_TABLE_FULL_NAME);
                    }
                } else if (streamSeq == 1) {
                    try (Connection dstConn2 = getCdcSyncDbConnectionSecond()) {
                        dstTotalBalance += calculateBalance(dstConn2, BINLOGX_TEST_TABLE_FULL_NAME);
                    }
                } else if (streamSeq == 2) {
                    try (Connection dstConn3 = getCdcSyncDbConnectionThird()) {
                        dstTotalBalance += calculateBalance(dstConn3, BINLOGX_TEST_TABLE_FULL_NAME);
                    }
                } else {
                    throw new PolardbxException("invalid stream seq " + streamSeq);
                }
            } else {
                try (Connection srcConn = getPolardbxConnection(DB_NAME);
                    Connection dstConn1 = getCdcSyncDbConnectionFirst();
                    Connection dstConn2 = getCdcSyncDbConnectionSecond();
                    Connection dstConn3 = getCdcSyncDbConnectionThird()) {
                    srcTotalBalance += calculateBalance(srcConn, BINLOGX_TEST_TABLE_FULL_NAME);
                    dstTotalBalance += calculateBalance(dstConn1, BINLOGX_TEST_TABLE_FULL_NAME);
                    dstTotalBalance += calculateBalance(dstConn2, BINLOGX_TEST_TABLE_FULL_NAME);
                    dstTotalBalance += calculateBalance(dstConn3, BINLOGX_TEST_TABLE_FULL_NAME);
                }
            }
        } else {
            try (Connection srcConn = getPolardbxConnection(DB_NAME);
                Connection dstConn = getCdcSyncDbConnection()) {
                srcTotalBalance += calculateBalance(srcConn, BINLOG_TEST_TABLE_FULL_NAME);
                dstTotalBalance += calculateBalance(dstConn, BINLOG_TEST_TABLE_FULL_NAME);
            }
        }
        Assert.assertEquals("polarx transfer test failed!", totalBalance, srcTotalBalance);
        Assert.assertEquals("mysql transfer test failed!", totalBalance, dstTotalBalance);
        log.info("transfer test success!");
    }

    private int calculateBalance(Connection conn, String tb) throws SQLException {
        int totalBalance = 0;
        String CHECK_ACCOUNTS_SQL_FORMAT = "SELECT SUM(balance) FROM %s";
        String sql = String.format(CHECK_ACCOUNTS_SQL_FORMAT, tb);
        try (ResultSet rs = JdbcUtil.executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                totalBalance = rs.getInt(1);
            }
        }
        return totalBalance;
    }

    private Transfer createTransfer() {
        int from = randomAccount();
        int to;
        do {
            to = randomAccount();
        } while (from == to);
        int amount = new Random(System.currentTimeMillis()).nextInt(100);
        return new Transfer(from, to, amount);
    }

    private void doTransfer(Transfer transfer) throws SQLException {
        Connection conn = getPolardbxConnection(DB_NAME);
        try {
            conn.setAutoCommit(false);
            updateBalance(conn, transfer.to, -transfer.amount);
            updateBalance(conn, transfer.from, transfer.amount);
            conn.commit();
            Thread.sleep(5);
        } catch (Exception e) {
            conn.rollback();
        } finally {
            JdbcUtil.closeConnection(conn);
        }
    }

    private void updateBalance(Connection conn, int id, int amount) throws SQLException {
        String TRANSFER_SQL_FORMAT = "UPDATE %s SET balance = balance + %s WHERE id = %s";
        String tb;
        if (usingBinlogX) {
            tb = BINLOGX_TEST_TABLE_FULL_NAME;
        } else {
            tb = BINLOG_TEST_TABLE_FULL_NAME;
        }
        String sql = String.format(TRANSFER_SQL_FORMAT, tb, amount, id);
        JdbcUtil.executeUpdate(conn, sql);
    }

    private int randomAccount() {
        return (int) nextSequence.addAndGet(random.nextInt(9) + 1) % ACCOUNT_COUNT;
    }

    private static class Transfer {
        private final int from;
        private final int to;
        private final int amount;

        Transfer(int f, int t, int a) {
            this.from = f;
            this.to = t;
            this.amount = a;
        }

        @Override
        public String toString() {
            return "Transfer{" + "from=" + from
                + ", to=" + to + ", amount=" + amount + '}';
        }
    }
}
