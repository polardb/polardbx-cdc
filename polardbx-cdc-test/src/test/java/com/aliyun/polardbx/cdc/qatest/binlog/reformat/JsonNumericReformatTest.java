/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.BitSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回归场景：reformat链路对JSON列重编码时，含小数的数值（fastjson解析为BigDecimal）
 * 与超long整数（BigInteger）曾被静默丢弃，生成内部offset错乱的损坏JSONB。
 * <p>
 * 触发方式：逻辑表data列为json，将node(0)物理表的data列modify为text制造列类型不匹配
 * （columnTypeMatch=false），使该列走RowEventReformator#resolveDataTypeNotMatch
 * -> JsonField重编码路径；随后直插物理表一条含小数/大整数的JSON数据，
 * dump逻辑binlog校验JSON可正常解析且数值无损。
 * <p>
 * 由于指定分表插入，数据无法被逻辑查询正确路由（与RowsEventReformatTest相同）；且物理列
 * 被modify为text后，源端SELECT返回原始文本、下游Replica端为JSONB归一化文本，
 * CHECK REPLICA TABLE的checksum必然不一致（实验室qatest.properties由平台生成，仓库内
 * 黑名单条目不生效），因此用例结束时drop表并等待下游消费，避免进入任何全量校验。
 */
@Slf4j
public class JsonNumericReformatTest extends RplBaseTestCase {
    private static final String DB_NAME = "zm_test_db";
    private static final String TABLE_NAME = "json_decimal_tb";
    private static final String CREATE_DATABASE_SQL = "create database if not exists %s mode = 'auto'";
    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE if not exists `%s`.`%s` ( \n"
            + "  `id` int NOT NULL AUTO_INCREMENT,\n"
            + "  `data` json,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") PARTITION BY HASH(`id`) PARTITIONS 4";
    private static final String SHOW_MASTER_STATUS_SQL = "show master status";
    private static final String SHOW_TABLES_SQL = "/*+TDDL:node(0)*/show tables like '%s'";
    // 将物理表的json列改为text，制造逻辑列(json)与物理列(text)类型不匹配，强制该列走JsonField重编码
    private static final String MODIFY_COLUMN_SQL = "/*+TDDL:node(0)*/alter table `%s` modify column `data` text";
    private static final String INSERT_SQL = "/*+TDDL:node(0)*/insert into `%s` (`data`) values ('%s')";
    private static final String USE_DATABASE = "use `%s`";
    private static final String DROP_TABLE_SQL = "drop table if exists `%s`.`%s`";
    private static final String UPDATE_NULL_BITMAP_TABLE = "json_update_null_bitmap_tb";
    private static final String CONCURRENT_DDL_DB = "json_char_ddl_repro_db";
    private static final String CONCURRENT_DDL_TABLE = "json_char_ddl_repro_tb";
    private static final long BINLOG_DUMP_TIMEOUT_MINUTES = 5;

    /**
     * 覆盖修复的三类数值编码：小数(decimal，数值无损)、UINT64区间大整数、普通整数；
     * 数值字段与字符串字段交错，模拟线上损坏场景的字段布局
     */
    private static final String JSON_VALUE = "{\"businessOrderNumber\":\"H36451314A\",\"brokenCount\":33.04,"
        + "\"receiverName\":\"abc\",\"orderTotal\":10000.00,\"transportPrice\":-128.75,"
        + "\"uintVal\":18446744073709551615,\"missingCount\":0}";

    @Test
    @SneakyThrows
    public void testJsonNumericReformat() {
        String file = "";
        long pos = 0;
        String phyTableName = "";

        try (Connection c = getPolardbxConnection()) {
            ResultSet rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            c.createStatement().execute(String.format(CREATE_TABLE_SQL, DB_NAME, TABLE_NAME));
            c.createStatement().execute(String.format(USE_DATABASE, DB_NAME));
            rs = c.createStatement().executeQuery(String.format(SHOW_TABLES_SQL, TABLE_NAME + "_%"));
            if (rs.next()) {
                phyTableName = rs.getString(1);
            }
            c.createStatement().execute(String.format(MODIFY_COLUMN_SQL, phyTableName));
            c.createStatement().execute(String.format(INSERT_SQL, phyTableName, JSON_VALUE));
        }

        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();
        log.info("start dump from {}:{}", file, pos);
        try {
            mysqlConn.dump(file, pos, null, (event, logPosition) -> {
                if (event instanceof WriteRowsLogEvent) {
                    WriteRowsLogEvent writeRowsLogEvent = (WriteRowsLogEvent) event;
                    if (writeRowsLogEvent.getTable().getTableName().equalsIgnoreCase(TABLE_NAME)) {
                        checkJsonColumn(writeRowsLogEvent);
                        return false;
                    }
                }
                return true;
            });
        } finally {
            // 清理：drop表并等待下游消费完毕，避免该表进入Replica/一致性全量校验
            // （源物理列为text原文，下游为JSONB归一化文本，checksum必然不一致）
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, TABLE_NAME));
            }
            sendTokenAndWait(CheckParameter.builder().build());
        }
    }

    /**
     * Production-chain regression for an ordinary (non-EXTERNALIZE) incompatible UPDATE.
     *
     * <p>The physical CHAR(1) value is intentionally invalid JSON. The type conversion therefore produces a
     * logical NULL. The UPDATE after-image must set the logical JSON null bit and keep every following field aligned;
     * otherwise downstream SQL apply stops with errno 1610 and the token barrier times out.</p>
     */
    @Test
    @SneakyThrows
    public void testUpdateConversionToNullKeepsReleaseBitmapSemantics() {
        Assume.assumeFalse("this regression targets the single-stream Replica path", PropertiesUtil.usingBinlogX);
        String file = "";
        long pos = 0;
        String phyTableName = "";

        try {
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
                c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, UPDATE_NULL_BITMAP_TABLE));

                ResultSet rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
                if (rs.next()) {
                    file = rs.getString("FILE");
                    pos = Long.parseLong(rs.getString("POSITION"));
                }

                String createTable = "CREATE TABLE `%s`.`%s` ("
                    + "`id` BIGINT NOT NULL, `data` JSON NULL, `c_geo` GEOMETRY NULL, "
                    + "`c_idx` BIGINT NOT NULL, PRIMARY KEY (`id`)) "
                    + "PARTITION BY HASH(`id`) PARTITIONS 4";
                c.createStatement().execute(String.format(createTable, DB_NAME, UPDATE_NULL_BITMAP_TABLE));
                c.createStatement().execute(String.format(USE_DATABASE, DB_NAME));
                rs = c.createStatement().executeQuery(
                    String.format(SHOW_TABLES_SQL, UPDATE_NULL_BITMAP_TABLE + "_%"));
                Assert.assertTrue("physical table must exist", rs.next());
                phyTableName = rs.getString(1);
                c.createStatement().execute("set sql_mode = ''");
                c.createStatement().execute(String.format(
                    "/*+TDDL:node(0)*/alter table `%s` modify column `data` char(1)", phyTableName));
                c.createStatement().execute(String.format(
                    "/*+TDDL:node(0)*/insert into `%s` (`id`,`data`,`c_geo`,`c_idx`) "
                        + "values (1,'{',ST_GeomFromText('POINT(1 2)'),7)", phyTableName));
                c.createStatement().execute(String.format(
                    "/*+TDDL:node(0)*/update `%s` set `data`='x',"
                        + "`c_geo`=ST_GeomFromText('POINT(3 4)'),`c_idx`=9 where `id`=1", phyTableName));
            }

            MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
            mysqlConn.connect();
            final String dumpFile = file;
            final long dumpPosition = pos;
            log.info("start ordinary incompatible UPDATE dump from {}:{}", dumpFile, dumpPosition);
            ExecutorService dumpExecutor = Executors.newSingleThreadExecutor();
            Future<?> dumpFuture = dumpExecutor.submit(() -> {
                try {
                    mysqlConn.dump(dumpFile, dumpPosition, null, (event, logPosition) -> {
                        if (event instanceof UpdateRowsLogEvent) {
                            UpdateRowsLogEvent updateRows = (UpdateRowsLogEvent) event;
                            if (updateRows.getTable().getTableName().equalsIgnoreCase(UPDATE_NULL_BITMAP_TABLE)) {
                                checkUpdateConversionToNull(updateRows);
                                return false;
                            }
                        }
                        return true;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            try {
                dumpFuture.get(BINLOG_DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                Assert.fail("can not find ordinary incompatible UPDATE in " + BINLOG_DUMP_TIMEOUT_MINUTES
                    + " minutes from " + dumpFile + ":" + dumpPosition);
            } finally {
                try {
                    mysqlConn.disconnect();
                } finally {
                    dumpFuture.cancel(true);
                    dumpExecutor.shutdownNow();
                }
            }
        } finally {
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, UPDATE_NULL_BITMAP_TABLE));
            }
        }
    }

    /**
     * Reproduce the TestModeTwo failure window through the normal CN DDL path. Multiple old-schema prepared UPDATEs
     * keep writing valid JSON while CN changes the logical column to CHAR(1). At least one DML must commit while the
     * DDL is executing; the downstream token and schema checks then prove that Replica SQL apply crossed the window.
     */
    @Test
    @SneakyThrows
    public void testConcurrentDmlWithJsonToCharDdlKeepsReplicaProgress() {
        Assume.assumeFalse("this regression targets the single-stream Replica path", PropertiesUtil.usingBinlogX);
        final int rowCount = 1024;
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicBoolean ddlExecuting = new AtomicBoolean(false);
        final AtomicInteger sequence = new AtomicInteger();
        final AtomicInteger dmlSuccess = new AtomicInteger();
        final AtomicInteger dmlSuccessDuringDdl = new AtomicInteger();
        final AtomicInteger dmlErrors = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        boolean validationPassed = false;

        try {
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format("create database if not exists `%s` mode = 'drds'",
                    CONCURRENT_DDL_DB));
                c.createStatement().execute(String.format(DROP_TABLE_SQL, CONCURRENT_DDL_DB, CONCURRENT_DDL_TABLE));
                String createTable = "CREATE TABLE `%s`.`%s` ("
                    + "`id` BIGINT NOT NULL, `data` JSON NULL, `c_geo` GEOMETRY NULL, "
                    + "`c_idx` BIGINT NOT NULL, PRIMARY KEY (`id`)) "
                    + "dbpartition by hash(`id`) "
                    + "tbpartition by hash(`id`) tbpartitions 3";
                c.createStatement().execute(String.format(createTable, CONCURRENT_DDL_DB, CONCURRENT_DDL_TABLE));
            }

            try (Connection c = getPolardbxConnection(CONCURRENT_DDL_DB);
                PreparedStatement ps = c.prepareStatement(String.format(
                    "insert into `%s` (`id`,`data`,`c_geo`,`c_idx`) values (?,?,ST_GeomFromText(?),?)",
                    CONCURRENT_DDL_TABLE))) {
                for (int id = 1; id <= rowCount; id++) {
                    ps.setLong(1, id);
                    ps.setString(2, "{\"seed\":" + id + ",\"payload\":\"json-before-char-ddl\"}");
                    ps.setString(3, "POINT(" + (id % 100) + " " + ((id + 1) % 100) + ")");
                    ps.setLong(4, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            for (int worker = 0; worker < 4; worker++) {
                workers.submit(() -> runJsonDmlTraffic(running, ddlExecuting, sequence, dmlSuccess,
                    dmlSuccessDuringDdl, dmlErrors, rowCount));
            }

            long warmupDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
            while (dmlSuccess.get() < 100 && System.currentTimeMillis() < warmupDeadline) {
                Thread.sleep(100);
            }
            Assert.assertTrue("DML traffic did not warm up before DDL, success=" + dmlSuccess.get(),
                dmlSuccess.get() >= 100);

            ddlExecuting.set(true);
            try (Connection c = getPolardbxConnection(CONCURRENT_DDL_DB);
                Statement stmt = c.createStatement()) {
                stmt.execute("set sql_mode = ''");
                stmt.execute(String.format(
                    "alter table `%s` modify column `data` char(1) character set gbk default 'x'",
                    CONCURRENT_DDL_TABLE));
            } finally {
                ddlExecuting.set(false);
                running.set(false);
            }

            workers.shutdown();
            Assert.assertTrue("DML workers did not stop", workers.awaitTermination(30, TimeUnit.SECONDS));
            Assert.assertTrue("no DML committed during the JSON-to-CHAR DDL window; success=" + dmlSuccess.get()
                    + ", errors=" + dmlErrors.get(),
                dmlSuccessDuringDdl.get() > 0);
            log.info("JSON-to-CHAR concurrent window completed, dmlSuccess={}, duringDdl={}, errors={}",
                dmlSuccess.get(), dmlSuccessDuringDdl.get(), dmlErrors.get());

            sendTokenAndWait(CheckParameter.builder().loopWaitTimeoutMs(TimeUnit.MINUTES.toMillis(5)).build());
            try (Connection target = getCdcSyncDbConnection(CONCURRENT_DDL_DB)) {
                ResultSet rs = target.createStatement().executeQuery(
                    String.format("select count(*) from `%s`", CONCURRENT_DDL_TABLE));
                Assert.assertTrue(rs.next());
                Assert.assertEquals(rowCount, rs.getInt(1));

                rs = target.createStatement().executeQuery(
                    String.format("show columns from `%s` like 'data'", CONCURRENT_DDL_TABLE));
                Assert.assertTrue(rs.next());
                Assert.assertEquals("char(1)", rs.getString("Type").toLowerCase());
            }
            validationPassed = true;
        } finally {
            running.set(false);
            workers.shutdownNow();
            workers.awaitTermination(30, TimeUnit.SECONDS);
            if (validationPassed) {
                try (Connection c = getPolardbxConnection()) {
                    c.createStatement().execute(
                        String.format(DROP_TABLE_SQL, CONCURRENT_DDL_DB, CONCURRENT_DDL_TABLE));
                }
                sendTokenAndWait(CheckParameter.builder().loopWaitTimeoutMs(TimeUnit.MINUTES.toMillis(5)).build());
            }
        }
    }

    @SneakyThrows
    private void runJsonDmlTraffic(AtomicBoolean running, AtomicBoolean ddlExecuting, AtomicInteger sequence,
                                   AtomicInteger dmlSuccess, AtomicInteger dmlSuccessDuringDdl,
                                   AtomicInteger dmlErrors, int rowCount) {
        try (Connection c = getPolardbxConnection(CONCURRENT_DDL_DB);
            PreparedStatement ps = c.prepareStatement(String.format(
                "update `%s` set `data`=?,`c_geo`=ST_GeomFromText(?),`c_idx`=? where `id`=?",
                CONCURRENT_DDL_TABLE))) {
            c.createStatement().execute("set sql_mode = ''");
            while (running.get()) {
                int seq = sequence.incrementAndGet();
                long id = ThreadLocalRandom.current().nextInt(1, rowCount + 1);
                ps.setString(1, "{\"seq\":" + seq + ",\"payload\":\"json-during-char-ddl\"}");
                ps.setString(2, "POINT(" + (seq % 100) + " " + ((seq + 1) % 100) + ")");
                ps.setLong(3, seq);
                ps.setLong(4, id);
                try {
                    if (ps.executeUpdate() == 1) {
                        dmlSuccess.incrementAndGet();
                        if (ddlExecuting.get()) {
                            dmlSuccessDuringDdl.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    dmlErrors.incrementAndGet();
                }
            }
        }
    }

    private void checkUpdateConversionToNull(UpdateRowsLogEvent event) {
        int columnCount = event.getTable().getColumnCnt();
        Assert.assertEquals(4, columnCount);
        Assert.assertEquals(LogEvent.MYSQL_TYPE_JSON, event.getTable().getColumnInfo()[1].type);
        Assert.assertEquals(LogEvent.MYSQL_TYPE_GEOMETRY, event.getTable().getColumnInfo()[2].type);
        Assert.assertEquals(LogEvent.MYSQL_TYPE_LONGLONG, event.getTable().getColumnInfo()[3].type);
        Assert.assertEquals(columnCount, event.getColumns().cardinality());
        Assert.assertEquals(columnCount, event.getChangeColumns().cardinality());

        RowsLogBuffer buffer = event.getRowsBuf("utf-8");
        Assert.assertTrue(buffer.nextOneRow(event.getColumns()));
        Serializable[] before = readIncludedRow(buffer, event.getColumns(), event.getTable().getColumnInfo());
        Assert.assertNull(before[1]);
        Assert.assertNotNull(before[2]);
        Assert.assertEquals(7L, ((Number) before[3]).longValue());

        Assert.assertTrue(buffer.nextOneRow(event.getChangeColumns()));
        Serializable[] after = readIncludedRow(buffer, event.getChangeColumns(), event.getTable().getColumnInfo());
        Assert.assertNull("converted JSON must be represented by the after-image NULL bit", after[1]);
        Assert.assertNotNull("GEOMETRY payload must remain in its own slot", after[2]);
        Assert.assertEquals("trailing BIGINT proves that the whole after-image stayed aligned", 9L,
            ((Number) after[3]).longValue());
        Assert.assertFalse("the rebuilt row event must be consumed exactly to its end",
            buffer.nextOneRow(event.getColumns()));
    }

    private Serializable[] readIncludedRow(RowsLogBuffer buffer, BitSet included,
                                           TableMapLogEvent.ColumnInfo[] columnInfos) {
        Serializable[] values = new Serializable[columnInfos.length];
        for (int i = 0; i < columnInfos.length; i++) {
            if (!included.get(i)) {
                continue;
            }
            TableMapLogEvent.ColumnInfo columnInfo = columnInfos[i];
            values[i] = buffer.nextValue(columnInfo.type, columnInfo.meta, i == 2);
        }
        return values;
    }

    private void checkJsonColumn(WriteRowsLogEvent writeRowsLogEvent) {
        int columnCnt = writeRowsLogEvent.getTable().getColumnCnt();
        TableMapLogEvent.ColumnInfo[] columnInfos = writeRowsLogEvent.getTable().getColumnInfo();
        RowsLogBuffer rowsLogBuffer = writeRowsLogEvent.getRowsBuf("utf-8");
        BitSet columns = writeRowsLogEvent.getColumns();
        while (rowsLogBuffer.nextOneRow(columns)) {
            BitSet nullBits = rowsLogBuffer.getNullBits();
            for (int i = 0; i < columnCnt; i++) {
                TableMapLogEvent.ColumnInfo info = columnInfos[i];
                if (nullBits.get(i)) {
                    log.info("Column {}: NULL", i);
                    continue;
                }
                // 修复前：JSON列payload内部offset错乱，此处decode会抛异常或解出错乱值
                Serializable value = rowsLogBuffer.nextValue(info.type, info.meta);
                log.info("Column {}: {}", i, value);
                if (i == 1) {
                    JSONObject actual = JSON.parseObject(value.toString());
                    Assert.assertEquals("H36451314A", actual.getString("businessOrderNumber"));
                    Assert.assertEquals("abc", actual.getString("receiverName"));
                    // decimal编码数值无损（文本尾零不保证：解码在小数末段全零时丢尾零，如10000.00->10000.0）
                    Assert.assertEquals(0, new BigDecimal("33.04").compareTo(actual.getBigDecimal("brokenCount")));
                    Assert.assertEquals(0, new BigDecimal("10000.00").compareTo(actual.getBigDecimal("orderTotal")));
                    Assert.assertEquals(0,
                        new BigDecimal("-128.75").compareTo(actual.getBigDecimal("transportPrice")));
                    // (Long.MAX_VALUE, UINT64_MAX]区间整数按UINT64编码，数值无损
                    Assert.assertEquals(0,
                        new BigDecimal("18446744073709551615").compareTo(actual.getBigDecimal("uintVal")));
                    Assert.assertEquals(0, actual.getIntValue("missingCount"));
                }
            }
        }
    }
}
