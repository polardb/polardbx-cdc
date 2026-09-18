/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * End-to-end verification from CN writes to logical row events in the global binlog.
 *
 * <p>This class validates the producer side of external-column replication: physical address
 * columns and transaction-local staging rows must be rebuilt as logical TEXT/BLOB events, while
 * physical GSI/replica rows stay hidden. Target-side GDN application is covered separately by
 * {@code ExternalColumnReplicaTest}.</p>
 */
@Slf4j
public class ExternalColumnBinlogTest extends RplBaseTestCase {

    private static final String DB_NAME = "cdc_external_column_test";
    private static final String TABLE_NAME = "ext_row";
    private static final String DML_TABLE = "ext_dml";
    private static final String TYPES_TABLE = "ext_types";
    private static final String MARKER_TABLE = "dump_marker";
    private static final long DML_MARKER_START_ID = 804100;
    private static final int DML_MARKER_ROW_COUNT = 32;
    private static final long CDC_HA_RETRY_TIMEOUT_SECONDS = 300;
    private static final long CDC_HA_RETRY_INTERVAL_SECONDS = 5;
    private static final String BODY_INSERT = "CDC外列-raw-🙂";
    private static final String BODY_UPDATE = "CDC外列-new-🙂";
    private static final byte[] PAYLOAD = new byte[] {0, 1, 2, 0, (byte) 0xFF, (byte) 0xE4, (byte) 0xB8, (byte) 0xAD};

    @BeforeClass
    public static void prepareDatabase() throws Exception {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB_NAME + "`");
            statement.execute("CREATE DATABASE `" + DB_NAME + "` MODE = 'auto'");
            statement.execute("USE `" + DB_NAME + "`");
            statement.execute("CREATE TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` ("
                + "`id` BIGINT NOT NULL, `sk` BIGINT NOT NULL, "
                + "`body` LONGTEXT EXTERNALIZE, `payload` LONGBLOB EXTERNALIZE, "
                + "`note` VARCHAR(64), PRIMARY KEY (`id`), "
                + "GLOBAL INDEX `g_sk` (`sk`) COVERING (`body`, `payload`) "
                + "PARTITION BY KEY(`sk`) PARTITIONS 4) "
                + "PARTITION BY KEY(`id`) PARTITIONS 4");
            statement.execute("CREATE TABLE `" + DB_NAME + "`.`" + DML_TABLE + "` ("
                + "`id` BIGINT NOT NULL, `sk` BIGINT NOT NULL, `gk` BIGINT NOT NULL, "
                + "`body` LONGTEXT EXTERNALIZE, `payload` LONGBLOB EXTERNALIZE, "
                + "`note` VARCHAR(64), PRIMARY KEY (`id`), "
                + "GLOBAL INDEX `g_gk` (`gk`) COVERING (`body`, `payload`) "
                + "PARTITION BY KEY(`gk`) PARTITIONS 4) "
                + "PARTITION BY KEY(`sk`) PARTITIONS 4");
            statement.execute("CREATE TABLE `" + DB_NAME + "`.`" + TYPES_TABLE + "` ("
                + "`id` BIGINT NOT NULL PRIMARY KEY, "
                + "`c_text` TEXT EXTERNALIZE, `c_tinytext` TINYTEXT EXTERNALIZE, "
                + "`c_mediumtext` MEDIUMTEXT EXTERNALIZE, `c_longtext` LONGTEXT EXTERNALIZE, "
                + "`c_blob` BLOB EXTERNALIZE, `c_tinyblob` TINYBLOB EXTERNALIZE, "
                + "`c_mediumblob` MEDIUMBLOB EXTERNALIZE, `c_longblob` LONGBLOB EXTERNALIZE) "
                + "DEFAULT CHARSET=utf8mb4 PARTITION BY KEY(`id`) PARTITIONS 4");
            statement.execute("CREATE TABLE `" + DB_NAME + "`.`" + MARKER_TABLE
                + "` (`id` BIGINT NOT NULL PRIMARY KEY) PARTITION BY KEY(`id`) PARTITIONS 4");
        }
    }

    /**
     * Covers logical INSERT/UPDATE/DELETE row images, unchanged-address reuse, NULL transitions,
     * and filtering of physical GSI/replica row events from the global binlog.
     */
    @Test
    public void testLogicalImagesAndGsiFiltering() throws Exception {
        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + TABLE_NAME
                + "` VALUES (804001, 1, '" + BODY_INSERT + "', X'00010200FFE4B8AD', 'n0')");
            statement.executeUpdate("UPDATE `" + TABLE_NAME + "` SET `note` = 'n1' WHERE `id` = 804001");
            statement.executeUpdate("UPDATE `" + TABLE_NAME
                + "` SET `body` = `body`, `note` = 'n2' WHERE `id` = 804001");
            statement.executeUpdate("UPDATE `" + TABLE_NAME + "` SET `body` = '" + BODY_UPDATE
                + "' WHERE `id` = 804001");
            statement.executeUpdate("DELETE FROM `" + TABLE_NAME + "` WHERE `id` = 804001");
            statement.executeUpdate("INSERT INTO `" + TABLE_NAME
                + "` VALUES (804002, 2, NULL, NULL, 'null0')");
            statement.executeUpdate("UPDATE `" + TABLE_NAME + "` SET `note` = 'null1' WHERE `id` = 804002");
            statement.executeUpdate("DELETE FROM `" + TABLE_NAME + "` WHERE `id` = 804002");
            insertMarker(statement, DML_MARKER_START_ID);
        }

        List<RowImages> rows = dumpUntilMarker(start, TABLE_NAME, DML_MARKER_START_ID);
        Assert.assertEquals(8, rows.size());

        RowImages insert = rows.get(0);
        Assert.assertTrue(insert.isWrite());
        Assert.assertEquals(BODY_INSERT, asUtf8(insert.after[2]));
        Assert.assertArrayEquals(PAYLOAD, (byte[]) insert.after[3]);

        assertAddressReuse(rows.get(1));
        assertAddressReuse(rows.get(2));

        RowImages externalUpdate = rows.get(3);
        Assert.assertTrue(externalUpdate.isUpdate());
        assertBlobRef(asUtf8(externalUpdate.before[2]));
        assertAfterColumnIncluded(externalUpdate, 2);
        Assert.assertEquals(BODY_UPDATE, asUtf8(externalUpdate.after[2]));
        assertBlobRef(asUtf8(externalUpdate.before[3]));
        assertAfterColumnOmitted(externalUpdate, 3);

        RowImages delete = rows.get(4);
        Assert.assertTrue(delete.isDelete());
        assertBlobRef(asUtf8(delete.before[2]));
        assertBlobRef(asUtf8(delete.before[3]));

        RowImages nullInsert = rows.get(5);
        Assert.assertTrue(nullInsert.isWrite());
        Assert.assertNull(nullInsert.after[2]);
        Assert.assertNull(nullInsert.after[3]);

        RowImages nullUpdate = rows.get(6);
        Assert.assertTrue(nullUpdate.isUpdate());
        Assert.assertNull(nullUpdate.before[2]);
        Assert.assertNull(nullUpdate.before[3]);
        assertAfterColumnOmitted(nullUpdate, 2);
        assertAfterColumnOmitted(nullUpdate, 3);

        RowImages nullDelete = rows.get(7);
        Assert.assertTrue(nullDelete.isDelete());
        Assert.assertNull(nullDelete.before[2]);
        Assert.assertNull(nullDelete.before[3]);
    }

    /**
     * Covers all supported TEXT and BLOB logical types, including Unicode text, binary zero bytes,
     * non-UTF8 bytes, NULL values, and UPDATE reconstruction from physical BlobRef carriers.
     */
    @Test
    public void testAllLogicalTextAndBlobTypes() throws Exception {
        String[] insertedText = {"text-中文-🙂", "tiny-ß", "medium-Καλημέρα", "long-日本語-🚀"};
        String[] updatedText = {"text-new-🌍", "tiny-new-é", "medium-new-한글", "long-new-🧪"};
        byte[][] insertedBlob = {
            bytes(0x00, 0x01, 0xFF), bytes(0x7F, 0x00), bytes(0xE4, 0xB8, 0xAD, 0x00),
            bytes(0xFF, 0xFE, 0x00, 0x41)
        };
        byte[][] updatedBlob = {
            bytes(0x10, 0x00, 0xFE), bytes(0x11, 0xFF), bytes(0x12, 0x00, 0x13),
            bytes(0x14, 0xFF, 0x00, 0x15)
        };

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + TYPES_TABLE + "` VALUES (840001, "
                + quote(insertedText[0]) + "," + quote(insertedText[1]) + ","
                + quote(insertedText[2]) + "," + quote(insertedText[3]) + ","
                + hex(insertedBlob[0]) + "," + hex(insertedBlob[1]) + ","
                + hex(insertedBlob[2]) + "," + hex(insertedBlob[3]) + ")");
            statement.executeUpdate("INSERT INTO `" + TYPES_TABLE
                + "` VALUES (840002,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)");
            statement.executeUpdate("UPDATE `" + TYPES_TABLE + "` SET "
                + "c_text=" + quote(updatedText[0]) + ",c_tinytext=" + quote(updatedText[1])
                + ",c_mediumtext=" + quote(updatedText[2]) + ",c_longtext=" + quote(updatedText[3])
                + ",c_blob=" + hex(updatedBlob[0]) + ",c_tinyblob=" + hex(updatedBlob[1])
                + ",c_mediumblob=" + hex(updatedBlob[2]) + ",c_longblob=" + hex(updatedBlob[3])
                + " WHERE id=840001");
            insertMarker(statement, 844100);
        }

        List<RowImages> rows = dumpUntilMarker(start, TYPES_TABLE, 844100);
        RowImages insert = onlyRow(writeRowsForId(rows, 840001), "typed insert");
        Assert.assertTrue(insert.isWrite());
        assertTypedValues(insert.after, insertedText, insertedBlob);

        RowImages nullInsert = onlyRow(writeRowsForId(rows, 840002), "typed NULL insert");
        Assert.assertTrue(nullInsert.isWrite());
        for (int i = 1; i < nullInsert.after.length; i++) {
            Assert.assertNull("typed NULL column " + i, nullInsert.after[i]);
        }

        RowImages update = onlyRow(updateRowsForId(rows, 840001), "typed update");
        Assert.assertTrue(update.isUpdate());
        for (int i = 1; i < update.before.length; i++) {
            assertBlobRef(asUtf8(update.before[i]));
        }
        assertTypedValues(update.after, updatedText, updatedBlob);
    }

    /**
     * Covers the principal INSERT-family execution paths: ordinary/ignored INSERT, UPSERT insert
     * and conflict paths, REPLACE, multi-row batch INSERT, and INSERT SELECT with NULL/empty values.
     */
    @Test
    public void testInsertUpsertReplaceIgnoreBatchAndInsertSelect() throws Exception {
        byte[] p1 = bytes(0x01, 0x00, 0xFF);
        byte[] p2 = bytes(0x02, 0x00, 0xFE);
        byte[] p3 = bytes(0x03, 0x00, 0xFD);
        byte[] p4 = bytes(0x04, 0x00, 0xFC);
        byte[] p5 = bytes(0x05, 0x00, 0xFB);

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate(dmlInsertSql(810001, 1, 11, "insert-one", p1, "insert"));
            statement.executeUpdate("INSERT IGNORE INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810001, 1, 11, "ignore-should-not-appear", p2, "ignored"));
            statement.executeUpdate("INSERT IGNORE INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810002, 2, 12, "ignore-new", p2, "ignore-new"));

            statement.executeUpdate("INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810003, 3, 13, "upsert-insert", p3, "upsert-insert")
                + " ON DUPLICATE KEY UPDATE body=VALUES(body),payload=VALUES(payload),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810003, 3, 13, "upsert-update", p4, "upsert-update")
                + " ON DUPLICATE KEY UPDATE body=VALUES(body),payload=VALUES(payload),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810003, 3, 13, "upsert-body-only", p5, "upsert-body-only")
                + " ON DUPLICATE KEY UPDATE body=VALUES(body),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810003, 3, 13, "upsert-unused", p5, "upsert-note-only")
                + " ON DUPLICATE KEY UPDATE note=VALUES(note)");

            statement.executeUpdate("REPLACE INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810004, 4, 14, "replace-insert", p4, "replace-insert"));
            statement.executeUpdate("REPLACE INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810004, 4, 14, "replace-update", p5, "replace-update"));

            statement.executeUpdate("INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(810005, 5, 15, "batch-a", p1, "batch-a") + ","
                + dmlValues(810006, 6, 16, null, null, "batch-null") + ","
                + dmlValues(810007, 7, 17, "", new byte[0], "batch-empty"));
            statement.executeUpdate("INSERT INTO `" + DML_TABLE
                + "` (id,sk,gk,body,payload,note) SELECT id+100,sk+100,gk+100,body,payload,"
                + "CONCAT(note,'-copy') FROM `" + DML_TABLE + "` WHERE id IN (810005,810006)");
            insertMarker(statement, 814100);
        }

        List<RowImages> rows = dumpUntilMarker(start, DML_TABLE, 814100);
        Assert.assertEquals("duplicate INSERT IGNORE must not emit a primary row event", 1,
            rowsForId(rows, 810001).size());
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810001), "ordinary insert"), "insert-one", p1);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810002), "INSERT IGNORE insert path"), "ignore-new", p2);

        assertDmlWrite(onlyRow(writeRowsForId(rows, 810003), "UPSERT insert path"), "upsert-insert", p3);
        List<RowImages> upsertUpdates = updateRowsForId(rows, 810003);
        Assert.assertTrue("UPSERT conflict path must emit update events: " + upsertUpdates,
            upsertUpdates.size() >= 2);
        RowImages materializedUpsert = findByAfterNote(upsertUpdates, "upsert-update");
        assertExternalUpdate(materializedUpsert, "upsert-update", p4);
        RowImages mixedExternalUpsert = findByAfterNote(rowsForId(rows, 810003), "upsert-body-only");
        if (mixedExternalUpsert.isUpdate()) {
            assertAfterColumnIncluded(mixedExternalUpsert, 3);
            Assert.assertEquals("upsert-body-only", asUtf8(mixedExternalUpsert.after[3]));
            assertBlobRef(asUtf8(mixedExternalUpsert.before[4]));
            assertAfterColumnOmitted(mixedExternalUpsert, 4);
        } else {
            assertDmlWrite(mixedExternalUpsert, "upsert-body-only", p4);
        }
        RowImages reuseUpsert = findByAfterNote(upsertUpdates, "upsert-note-only");
        assertDmlAddressReuse(reuseUpsert);

        List<RowImages> replaceRows = rowsForId(rows, 810004);
        Assert.assertTrue("REPLACE conflict must emit at least two primary mutations: " + replaceRows,
            replaceRows.size() >= 2);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810004).subList(0, 1), "REPLACE insert path"),
            "replace-insert", p4);
        RowImages replaceTerminal = findByAfterBody(replaceRows, "replace-update");
        assertAfterLogicalData(replaceTerminal, "replace-update", p5);
        for (RowImages row : replaceRows) {
            if (row.isDelete()) {
                assertDmlDeleteAddresses(row);
            }
        }

        assertDmlWrite(onlyRow(writeRowsForId(rows, 810005), "batch row A"), "batch-a", p1);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810006), "batch NULL row"), null, null);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810007), "batch empty row"), "", new byte[0]);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810105), "INSERT SELECT copy"), "batch-a", p1);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 810106), "INSERT SELECT NULL copy"), null, null);

        assertLogicalValueAbsent(rows, "ignore-should-not-appear");
        assertLogicalValueAbsent(rows, "upsert-unused");
    }

    /**
     * Covers transaction-local staging across different DNs, including commit, full rollback,
     * savepoint rollback, and an unchanged external value reused by a committed UPDATE.
     */
    @Test
    public void testTransactionCommitRollbackSavepointAndMultiDn() throws Exception {
        long[] routeValues;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routeValues = findDifferentRouteValues(connection, DML_TABLE, "sk");
        }

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate(dmlInsertSql(830001, routeValues[0], 31, "txn-first", bytes(0x31), "t0"));
            statement.executeUpdate("UPDATE `" + DML_TABLE
                + "` SET body='txn-second',payload=X'3200FF',note='t1' WHERE id=830001");
            statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET note='t2' WHERE id=830001");
            statement.executeUpdate(dmlInsertSql(830002, routeValues[1], 32, "txn-other-dn",
                bytes(0x33, 0x00, 0xFF), "other"));
            connection.commit();

            statement.executeUpdate(dmlInsertSql(830010, routeValues[0], 40, "rollback-insert",
                bytes(0x40), "rollback"));
            statement.executeUpdate("UPDATE `" + DML_TABLE
                + "` SET body='rollback-update',payload=X'41' WHERE id=830001");
            connection.rollback();

            statement.executeUpdate(dmlInsertSql(830003, routeValues[0], 33, "savepoint-keep",
                bytes(0x34), "sp0"));
            statement.execute("SAVEPOINT ext_sp");
            statement.executeUpdate(dmlInsertSql(830004, routeValues[1], 34, "savepoint-drop",
                bytes(0x35), "sp-drop"));
            statement.executeUpdate("UPDATE `" + DML_TABLE
                + "` SET body='savepoint-drop-update',payload=X'36' WHERE id=830003");
            statement.execute("ROLLBACK TO SAVEPOINT ext_sp");
            statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET note='sp-committed' WHERE id=830003");
            connection.commit();
            connection.setAutoCommit(true);
            insertMarker(statement, 834100);
        }

        List<RowImages> rows = dumpUntilMarker(start, DML_TABLE, 834100);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 830001), "transaction insert"), "txn-first", bytes(0x31));
        List<RowImages> firstUpdates = updateRowsForId(rows, 830001);
        Assert.assertEquals("rolled-back UPDATE must not enter global binlog", 2, firstUpdates.size());
        assertExternalUpdate(findByAfterNote(firstUpdates, "t1"), "txn-second", bytes(0x32, 0x00, 0xFF));
        assertDmlAddressReuse(findByAfterNote(firstUpdates, "t2"));
        assertDmlWrite(onlyRow(writeRowsForId(rows, 830002), "other-DN transaction insert"),
            "txn-other-dn", bytes(0x33, 0x00, 0xFF));

        Assert.assertTrue("fully rolled-back INSERT must not enter global binlog", rowsForId(rows, 830010).isEmpty());
        assertLogicalValueAbsent(rows, "rollback-update");
        assertDmlWrite(onlyRow(writeRowsForId(rows, 830003), "savepoint surviving insert"),
            "savepoint-keep", bytes(0x34));
        RowImages savepointUpdate = onlyRow(updateRowsForId(rows, 830003), "savepoint surviving update");
        assertDmlAddressReuse(savepointUpdate);
        Assert.assertEquals("sp-committed", savepointUpdate.after[5]);
        Assert.assertTrue("ROLLBACK TO SAVEPOINT row must not enter global binlog",
            rowsForId(rows, 830004).isEmpty());
        assertLogicalValueAbsent(rows, "savepoint-drop-update");
    }

    /**
     * Runs the same committed and rolled-back multi-DN external-column mutations under TSO, XA,
     * and XA_TSO so every supported distributed transaction policy exercises row reconstruction.
     */
    @Test
    public void testTsoXaAndXaTsoTransactionLifecycle() throws Exception {
        long[] routeValues;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routeValues = findDifferentRouteValues(connection, DML_TABLE, "sk");
        }

        for (int i = 0; i < ExternalTransactionPolicy.values().length; i++) {
            ExternalTransactionPolicy policy = ExternalTransactionPolicy.values()[i];
            long rowId = 900001L + i * 10_000L;
            long otherDnRowId = rowId + 1;
            long rollbackRowId = rowId + 9;
            long markerId = 904100L + i * 10_000L;
            String tag = policy.expectedName.toLowerCase();
            byte[] seedPayload = bytes(0x61 + i, 0x00, 0xF1 + i);
            byte[] updatedPayload = bytes(0x71 + i, 0x00, 0xE1 + i);
            byte[] otherDnPayload = bytes(0x51 + i, 0x00, 0xD1 + i);

            BinlogPosition start = currentBinlogPosition();
            try (Connection connection = getPolardbxConnection(DB_NAME);
                Statement statement = connection.createStatement()) {
                policy.applyAndAssert(statement);
                connection.setAutoCommit(false);
                statement.executeUpdate(dmlInsertSql(rowId, routeValues[0], 101 + i,
                    tag + "-seed", seedPayload, tag + "-seed"));
                statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET body=" + quote(tag + "-updated")
                    + ",payload=" + hex(updatedPayload) + ",note=" + quote(tag + "-updated")
                    + " WHERE id=" + rowId);
                statement.executeUpdate(dmlInsertSql(otherDnRowId, routeValues[1], 201 + i,
                    tag + "-other-dn", otherDnPayload, tag + "-other-dn"));
                connection.commit();

                statement.executeUpdate(dmlInsertSql(rollbackRowId, routeValues[1], 301 + i,
                    tag + "-rollback", bytes(0x41 + i), tag + "-rollback"));
                statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET body=" + quote(tag + "-rollback-update")
                    + ",payload=" + hex(bytes(0x31 + i)) + ",note=" + quote(tag + "-rollback-update")
                    + " WHERE id=" + rowId);
                connection.rollback();

                connection.setAutoCommit(true);
                insertMarker(statement, markerId);
            }

            List<RowImages> rows = dumpUntilMarker(start, DML_TABLE, markerId);
            assertDmlWrite(onlyRow(writeRowsForId(rows, rowId), policy.expectedName + " transaction insert"),
                tag + "-seed", seedPayload);
            assertExternalUpdate(onlyRow(updateRowsForId(rows, rowId), policy.expectedName + " transaction update"),
                tag + "-updated", updatedPayload);
            assertDmlWrite(
                onlyRow(writeRowsForId(rows, otherDnRowId), policy.expectedName + " other-DN transaction insert"),
                tag + "-other-dn", otherDnPayload);
            Assert.assertTrue(policy.expectedName + " rolled-back INSERT must not enter global binlog",
                rowsForId(rows, rollbackRowId).isEmpty());
            assertLogicalValueAbsent(rows, tag + "-rollback-update");
        }
    }

    /**
     * Covers real primary-table/GSI relocates where zero, one, or both external columns change,
     * plus UPSERT relocate with reused and newly materialized values.
     */
    @Test
    public void testPartitionKeyAndCoveringGsiRelocate() throws Exception {
        long[] routeValues;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routeValues = findDifferentRouteValues(connection, DML_TABLE, "sk");
        }
        byte[] originalPayload = bytes(0x51, 0x00, 0xFF);
        byte[] changedPayload = bytes(0x52, 0x00, 0xFE);
        String logicalHint = "/*+TDDL:cmd_extra(DML_EXECUTION_STRATEGY=LOGICAL,"
            + "MODIFY_SELECT_MULTI=false,ENABLE_MODIFY_SHARDING_COLUMN=true)*/ ";
        String fullPkDuplicateCheckHint =
            "/*+TDDL:cmd_extra(DML_PARTITION_LOCAL_PK_DUP_CHECK=false)*/ ";

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate(dmlInsertSql(820011, routeValues[0], 21, "relocate-original",
                originalPayload, "seed"));
            statement.executeUpdate(logicalHint + "UPDATE `" + DML_TABLE
                + "` SET id=id,sk=sk,note='identity-keys' WHERE id=820011");
            statement.executeUpdate(logicalHint + "UPDATE `" + DML_TABLE
                + "` SET gk=121,note='gsi-key' WHERE id=820011");
            statement.executeUpdate(logicalHint + "UPDATE `" + DML_TABLE + "` SET sk=" + routeValues[1]
                + ",gk=221,note='primary-gsi-relocate' WHERE id=820011");
            statement.executeUpdate(logicalHint + "UPDATE `" + DML_TABLE + "` SET sk=" + routeValues[0]
                + ",gk=321,body='relocate-mixed',note='primary-gsi-mixed' WHERE id=820011");
            statement.executeUpdate(logicalHint + "UPDATE `" + DML_TABLE + "` SET sk=" + routeValues[1]
                + ",gk=421,body='relocate-changed',payload=" + hex(changedPayload)
                + ",note='primary-gsi-changed' WHERE id=820011");
            statement.executeUpdate(dmlInsertSql(820020, routeValues[0], 22, "upsert-relocate-original",
                originalPayload, "upsert-relocate-seed"));
            statement.executeUpdate(fullPkDuplicateCheckHint + "INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(820020, routeValues[1], 222, "upsert-relocate-unused", changedPayload,
                "upsert-relocate-reuse")
                + " ON DUPLICATE KEY UPDATE sk=VALUES(sk),gk=VALUES(gk),note=VALUES(note)");
            statement.executeUpdate(fullPkDuplicateCheckHint + "INSERT INTO `" + DML_TABLE + "` VALUES "
                + dmlValues(820020, routeValues[0], 322, "upsert-relocate-changed", changedPayload,
                "upsert-relocate-change")
                + " ON DUPLICATE KEY UPDATE sk=VALUES(sk),gk=VALUES(gk),body=VALUES(body),"
                + "payload=VALUES(payload),note=VALUES(note)");
            insertMarker(statement, 824100);
        }

        List<RowImages> rows = dumpUntilMarker(start, DML_TABLE, 824100);
        List<RowImages> targetRows = rowsForId(rows, 820011);
        assertDmlWrite(findWriteByAfterNote(targetRows, "seed"), "relocate-original", originalPayload);

        RowImages identityUpdate = findByAfterNote(updateRowsForId(rows, 820011), "identity-keys");
        assertDmlAddressReuse(identityUpdate);
        RowImages gsiKeyUpdate = findByAfterNote(updateRowsForId(rows, 820011), "gsi-key");
        assertDmlAddressReuse(gsiKeyUpdate);

        RowImages unchangedRelocate = findWriteByAfterNote(targetRows, "primary-gsi-relocate");
        assertDmlWrite(unchangedRelocate, "relocate-original", originalPayload);
        RowImages mixedRelocate = findWriteByAfterNote(targetRows, "primary-gsi-mixed");
        assertDmlWrite(mixedRelocate, "relocate-mixed", originalPayload);
        RowImages changedRelocate = findWriteByAfterNote(targetRows, "primary-gsi-changed");
        assertDmlWrite(changedRelocate, "relocate-changed", changedPayload);

        List<RowImages> targetDeletes = deleteRowsForId(rows, 820011);
        Assert.assertEquals("three partition-key relocates must emit three primary deletes", 3, targetDeletes.size());
        for (RowImages row : targetDeletes) {
            assertDmlDeleteAddresses(row);
        }

        List<RowImages> upsertRelocateRows = rowsForId(rows, 820020);
        assertDmlWrite(findWriteByAfterNote(upsertRelocateRows, "upsert-relocate-seed"),
            "upsert-relocate-original", originalPayload);
        assertDmlWrite(findWriteByAfterNote(upsertRelocateRows, "upsert-relocate-reuse"),
            "upsert-relocate-original", originalPayload);
        assertDmlWrite(findWriteByAfterNote(upsertRelocateRows, "upsert-relocate-change"),
            "upsert-relocate-changed", changedPayload);
        Assert.assertEquals(2, deleteRowsForId(rows, 820020).size());
        assertLogicalValueAbsent(rows, "upsert-relocate-unused");
    }

    /**
     * Covers expression and UPDATE JOIN assignment, non-NULL-to-NULL and NULL-to-non-NULL changes,
     * unchanged external fields, and DELETE before images containing physical addresses.
     */
    @Test
    public void testUpdateExpressionJoinNullTransitionsAndDelete() throws Exception {
        byte[] sourcePayload = bytes(0x61, 0x00, 0xFF);
        byte[] targetPayload = bytes(0x62, 0x00, 0xFE);
        byte[] expressionPayload = bytes(0x63, 0x00, 0xFD);
        byte[] restoredPayload = bytes(0x64, 0x00, 0xFC);

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate(dmlInsertSql(850001, 51, 61, "join-source", sourcePayload, "source-note"));
            statement.executeUpdate(dmlInsertSql(850002, 52, 62, "join-target", targetPayload, "target-note"));
            statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET body=CONCAT('expr-',note),payload="
                + hex(expressionPayload) + ",note='expression' WHERE id=850002");
            statement.executeUpdate("/*TDDL:FORBID_EXECUTE_DML_ALL=FALSE*/ UPDATE `" + DML_TABLE
                + "` a JOIN `" + DML_TABLE
                + "` b ON b.id=850001 SET a.body=b.body,a.payload=b.payload,a.note='join-copy' "
                + "WHERE a.id=850002");
            statement.executeUpdate("UPDATE `" + DML_TABLE
                + "` SET body=NULL,payload=NULL,note='to-null' WHERE id=850002");
            statement.executeUpdate("UPDATE `" + DML_TABLE + "` SET body='from-null',payload="
                + hex(restoredPayload) + ",note='from-null' WHERE id=850002");
            statement.executeUpdate("DELETE FROM `" + DML_TABLE + "` WHERE id=850002");
            insertMarker(statement, 854100);
        }

        List<RowImages> rows = dumpUntilMarker(start, DML_TABLE, 854100);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 850001), "UPDATE JOIN source"),
            "join-source", sourcePayload);
        assertDmlWrite(onlyRow(writeRowsForId(rows, 850002), "UPDATE JOIN target"),
            "join-target", targetPayload);
        List<RowImages> updates = updateRowsForId(rows, 850002);
        Assert.assertEquals(4, updates.size());
        assertExternalUpdate(findByAfterNote(updates, "expression"), "expr-target-note", expressionPayload);
        assertExternalUpdate(findByAfterNote(updates, "join-copy"), "join-source", sourcePayload);
        RowImages toNull = findByAfterNote(updates, "to-null");
        assertBlobRef(asUtf8(toNull.before[3]));
        assertBlobRef(asUtf8(toNull.before[4]));
        assertAfterColumnIncluded(toNull, 3);
        assertAfterColumnIncluded(toNull, 4);
        Assert.assertNull(toNull.after[3]);
        Assert.assertNull(toNull.after[4]);
        RowImages fromNull = findByAfterNote(updates, "from-null");
        Assert.assertNull(fromNull.before[3]);
        Assert.assertNull(fromNull.before[4]);
        assertAfterColumnIncluded(fromNull, 3);
        assertAfterColumnIncluded(fromNull, 4);
        Assert.assertEquals("from-null", asUtf8(fromNull.after[3]));
        Assert.assertArrayEquals(restoredPayload, (byte[]) fromNull.after[4]);
        assertDmlDeleteAddresses(onlyRow(deleteRowsForId(rows, 850002), "DELETE before image"));
    }

    /**
     * Covers logical DDL emitted for directly externalized columns, ADD/DROP external columns, and
     * MCE externalization while ensuring physical address-column names never leak downstream.
     */
    @Test
    public void testLogicalDdlForExternalColumns() throws Exception {
        String createTable = "ext_ddl_create";
        String addDropTable = "ext_ddl_add_drop";
        String mceTable = "ext_ddl_mce";
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `" + createTable + "`");
            statement.execute("DROP TABLE IF EXISTS `" + addDropTable + "`");
            statement.execute("DROP TABLE IF EXISTS `" + mceTable + "`");
        }

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `" + createTable + "` ("
                + "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, "
                + "`payload` LONGBLOB EXTERNALIZE) PARTITION BY KEY(`id`) PARTITIONS 2");
            statement.execute("CREATE TABLE `" + addDropTable + "` ("
                + "`id` BIGINT NOT NULL PRIMARY KEY) PARTITION BY KEY(`id`) PARTITIONS 2");
            statement.execute("ALTER TABLE `" + addDropTable + "` ADD COLUMN `body` LONGTEXT EXTERNALIZE");
            statement.execute("ALTER TABLE `" + addDropTable + "` DROP COLUMN `body`");
            statement.execute("CREATE TABLE `" + mceTable + "` ("
                + "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT) "
                + "PARTITION BY KEY(`id`) PARTITIONS 2");
            statement.execute("ALTER TABLE `" + mceTable + "` MODIFY COLUMN `body` LONGTEXT EXTERNALIZE");
            statement.executeUpdate("INSERT INTO `" + MARKER_TABLE + "` VALUES (804200)");
        }

        List<DdlImages> ddlEvents = dumpDdlUntilMarker(start,
            Arrays.asList(createTable, addDropTable, mceTable), 804200);

        assertLogicalDdl(findDdl(ddlEvents, createTable, "CREATE TABLE"),
            "BODY", "LONGTEXT", "PAYLOAD", "LONGBLOB");
        assertLogicalDdl(findDdl(ddlEvents, addDropTable, "ADD COLUMN"), "BODY", "LONGTEXT");
        assertLogicalDdl(findDdl(ddlEvents, addDropTable, "DROP COLUMN"), "BODY");
        assertLogicalDdl(findDdl(ddlEvents, mceTable, "MODIFY COLUMN"), "BODY", "LONGTEXT");
    }

    /**
     * A terminal MCE marker must leave CDC able to process later DDLs that carry either a fresh
     * physical CREATE TABLE or only an incremental physical DDL. Run the matrix in both
     * directions so the departing content/address column can never leak into restored meta.
     */
    @Test
    public void testDdlMatrixAfterExternalizeAndInternalize() throws Exception {
        String table = "ext_mce_ddl_matrix";
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `" + table + "`");
        }

        BinlogPosition start = currentBinlogPosition();
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `" + table + "` ("
                + "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT, `note` VARCHAR(64)) "
                + "PARTITION BY KEY(`id`) PARTITIONS 2");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (1, 'matrix-body', 'n0')");

            statement.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `body` LONGTEXT EXTERNALIZE");
            assertLatestMceCreateSql(table, "body_addr_", "body");

            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `extra_c` INT DEFAULT 7");
            statement.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `note` VARCHAR(128)");
            statement.execute("CREATE INDEX `idx_note` ON `" + table + "` (`note`)");
            statement.execute("ALTER TABLE `" + table + "` RENAME INDEX `idx_note` TO `idx_note_renamed`");
            statement.execute("ALTER TABLE `" + table + "` DROP INDEX `idx_note_renamed`");
            statement.execute("ALTER TABLE `" + table + "` CHANGE COLUMN `extra_c` `extra_c2` BIGINT");
            statement.execute("ALTER TABLE `" + table + "` DROP COLUMN `extra_c2`");
            statement.execute("ALTER TABLE `" + table + "` COMMENT='externalized-terminal'");
            statement.executeUpdate("UPDATE `" + table + "` SET body='matrix-external', note='n1' WHERE id=1");

            statement.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `body` LONGTEXT");
            assertLatestMceCreateSql(table, "body", "body_addr_");

            statement.execute("CREATE INDEX `idx_body` ON `" + table + "` (`body`(16))");
            statement.execute("ALTER TABLE `" + table + "` DROP INDEX `idx_body`");
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `plain_c` VARCHAR(32)");
            statement.execute("ALTER TABLE `" + table + "` DROP COLUMN `plain_c`");
            statement.executeUpdate("UPDATE `" + table + "` SET body='matrix-plain', note='n2' WHERE id=1");
            insertMarker(statement, 874100);
        }

        List<DdlImages> ddlEvents = dumpDdlUntilMarker(start, Arrays.asList(table), 874100);
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "ADD COLUMN", "EXTRA_C"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "MODIFY COLUMN", "NOTE"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "CREATE INDEX", "IDX_NOTE"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "RENAME INDEX", "IDX_NOTE_RENAMED"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "DROP INDEX", "IDX_NOTE_RENAMED"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "CHANGE COLUMN", "EXTRA_C2"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "DROP COLUMN", "EXTRA_C2"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "COMMENT", "EXTERNALIZED-TERMINAL"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "CREATE INDEX", "IDX_BODY"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "DROP INDEX", "IDX_BODY"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "ADD COLUMN", "PLAIN_C"));
        assertNoPhysicalAddress(findDdlContaining(ddlEvents, table, "DROP COLUMN", "PLAIN_C"));

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT body,note FROM `" + table + "` WHERE id=1")) {
            Assert.assertTrue(resultSet.next());
            Assert.assertEquals("matrix-plain", resultSet.getString(1));
            Assert.assertEquals("n2", resultSet.getString(2));
            Assert.assertFalse(resultSet.next());
        }
    }

    private static DdlImages findDdl(List<DdlImages> ddlEvents, String tableName, String operation) {
        return ddlEvents.stream()
            .filter(ddl -> ddl.privateDdl.toUpperCase().contains(tableName.toUpperCase()))
            .filter(ddl -> ddl.privateDdl.toUpperCase().contains(operation))
            .findFirst()
            .orElseThrow(() -> new AssertionError("cannot find " + operation + " for " + tableName
                + " in global binlog ddl events " + ddlEvents));
    }

    private static DdlImages findDdlContaining(List<DdlImages> ddlEvents, String tableName, String... tokens) {
        return ddlEvents.stream().filter(ddl -> {
            String privateDdl = ddl.privateDdl.toUpperCase();
            if (!privateDdl.contains(tableName.toUpperCase())) {
                return false;
            }
            for (String token : tokens) {
                if (!privateDdl.contains(token.toUpperCase())) {
                    return false;
                }
            }
            return true;
        }).findFirst().orElseThrow(() -> new AssertionError("cannot find DDL containing "
            + Arrays.toString(tokens) + " for " + tableName + " in global binlog ddl events " + ddlEvents));
    }

    private static void assertNoPhysicalAddress(DdlImages ddl) {
        Assert.assertFalse("private DDL must not expose the physical address column: " + ddl,
            ddl.privateDdl.toUpperCase().contains("_ADDR_"));
        Assert.assertFalse("MySQL DDL must not expose the physical address column: " + ddl,
            ddl.mysqlDdl.toUpperCase().contains("_ADDR_"));
    }

    private static void assertLogicalDdl(DdlImages ddl, String... logicalTokens) {
        String privateDdl = ddl.privateDdl.toUpperCase();
        String mysqlDdl = ddl.mysqlDdl.toUpperCase();
        Assert.assertFalse("private DDL must not expose the physical address column: " + ddl,
            privateDdl.contains("_ADDR_"));
        Assert.assertFalse("MySQL DDL must not expose the physical address column: " + ddl,
            mysqlDdl.contains("_ADDR_"));
        for (String token : logicalTokens) {
            Assert.assertTrue("private DDL misses logical token " + token + ": " + ddl,
                privateDdl.contains(token));
            Assert.assertTrue("MySQL DDL misses logical token " + token + ": " + ddl,
                mysqlDdl.contains(token));
        }
        if (!privateDdl.contains("DROP COLUMN")) {
            Assert.assertTrue("PolarDB-X private DDL must retain EXTERNALIZE: " + ddl,
                privateDdl.contains("EXTERNALIZE"));
        }
        Assert.assertFalse("MySQL-compatible DDL must remove EXTERNALIZE: " + ddl,
            mysqlDdl.contains("EXTERNALIZE"));
    }

    private void assertLatestMceCreateSql(String tableName, String includedColumn, String excludedColumn)
        throws Exception {
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT ext FROM __cdc__.__cdc_ddl_record__ WHERE schema_name='" + DB_NAME
                    + "' AND table_name='" + tableName + "' ORDER BY id DESC LIMIT 1")) {
            Assert.assertTrue("missing MCE CDC marker for " + tableName, resultSet.next());
            JSONObject ext = JSONObject.parseObject(resultSet.getString(1));
            Assert.assertNotNull("missing CDC ext payload for " + tableName, ext);
            String createSql = ext.getString("createSql4PhyTable");
            Assert.assertNotNull("missing createSql4PhyTable for " + tableName, createSql);
            Assert.assertTrue("createSql4PhyTable must contain terminal column " + includedColumn + ": " + createSql,
                containsColumnDefinition(createSql, includedColumn));
            Assert.assertFalse("createSql4PhyTable must exclude departing column " + excludedColumn + ": " + createSql,
                containsColumnDefinition(createSql, excludedColumn));
        }
    }

    private static boolean containsColumnDefinition(String createSql, String columnName) {
        return Pattern.compile("(?i)(?:\\(|,)\\s*`?" + Pattern.quote(columnName) + "`?\\s+")
            .matcher(createSql).find();
    }

    private static void assertAddressReuse(RowImages update) {
        Assert.assertTrue(update.isUpdate());
        assertBlobRef(asUtf8(update.before[2]));
        assertBlobRef(asUtf8(update.before[3]));
        assertAfterColumnOmitted(update, 2);
        assertAfterColumnOmitted(update, 3);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static String quote(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private static String hex(byte[] value) {
        if (value == null) {
            return "NULL";
        }
        StringBuilder result = new StringBuilder("X'");
        for (byte b : value) {
            result.append(String.format("%02X", b & 0xFF));
        }
        return result.append('\'').toString();
    }

    private static String dmlInsertSql(long id, long sk, long gk, String body, byte[] payload, String note) {
        return "INSERT INTO `" + DML_TABLE + "` VALUES " + dmlValues(id, sk, gk, body, payload, note);
    }

    private static String dmlValues(long id, long sk, long gk, String body, byte[] payload, String note) {
        return "(" + id + "," + sk + "," + gk + "," + quote(body) + "," + hex(payload) + ","
            + quote(note) + ")";
    }

    private enum ExternalTransactionPolicy {
        TSO("TSO", true, "TSO"),
        XA("XA", false, "XA"),
        XA_TSO("XA", true, "XA_TSO");

        private final String configuredName;
        private final boolean enableXaTso;
        private final String expectedName;

        ExternalTransactionPolicy(String configuredName, boolean enableXaTso, String expectedName) {
            this.configuredName = configuredName;
            this.enableXaTso = enableXaTso;
            this.expectedName = expectedName;
        }

        private void applyAndAssert(Statement statement) throws SQLException {
            statement.execute("SET enable_xa_tso = " + enableXaTso);
            statement.execute("SET transaction_policy = " + configuredName);
            try (ResultSet resultSet = statement.executeQuery("SHOW VARIABLES LIKE 'transaction_policy'")) {
                Assert.assertTrue("transaction_policy is not visible after setting " + expectedName, resultSet.next());
                Assert.assertEquals("effective transaction policy", expectedName,
                    resultSet.getString("Value").toUpperCase());
            }
        }
    }

    private static void assertTypedValues(Object[] image, String[] text, byte[][] blob) {
        for (int i = 0; i < text.length; i++) {
            Assert.assertEquals("typed text column " + i, text[i], asUtf8(image[i + 1]));
        }
        for (int i = 0; i < blob.length; i++) {
            Assert.assertArrayEquals("typed blob column " + i, blob[i], (byte[]) image[i + 5]);
        }
    }

    private static void assertDmlWrite(RowImages row, String body, byte[] payload) {
        Assert.assertTrue("expected WRITE_ROWS: " + row, row.isWrite());
        assertAfterLogicalData(row, body, payload);
    }

    private static void assertAfterLogicalData(RowImages row, String body, byte[] payload) {
        Assert.assertNotNull("row must have an after image: " + row, row.after);
        if (body == null) {
            Assert.assertNull(row.after[3]);
        } else {
            Assert.assertEquals(body, asUtf8(row.after[3]));
        }
        if (payload == null) {
            Assert.assertNull(row.after[4]);
        } else {
            Assert.assertArrayEquals(payload, (byte[]) row.after[4]);
        }
    }

    private static void assertExternalUpdate(RowImages update, String body, byte[] payload) {
        Assert.assertTrue("expected UPDATE_ROWS: " + update, update.isUpdate());
        assertBlobRef(asUtf8(update.before[3]));
        assertBlobRef(asUtf8(update.before[4]));
        assertAfterColumnIncluded(update, 3);
        assertAfterColumnIncluded(update, 4);
        assertAfterLogicalData(update, body, payload);
    }

    private static void assertDmlAddressReuse(RowImages update) {
        Assert.assertTrue("expected UPDATE_ROWS: " + update, update.isUpdate());
        assertBlobRef(asUtf8(update.before[3]));
        assertBlobRef(asUtf8(update.before[4]));
        assertAfterColumnOmitted(update, 3);
        assertAfterColumnOmitted(update, 4);
    }

    private static void assertAfterColumnIncluded(RowImages row, int columnIndex) {
        Assert.assertNotNull("row must have an after-column bitmap: " + row, row.afterColumns);
        Assert.assertTrue("after image must include column " + columnIndex + ": " + row,
            row.afterColumns.get(columnIndex));
    }

    private static void assertAfterColumnOmitted(RowImages row, int columnIndex) {
        Assert.assertNotNull("row must have an after image: " + row, row.after);
        Assert.assertNotNull("row must have an after-column bitmap: " + row, row.afterColumns);
        Assert.assertFalse("after image must omit unchanged external column " + columnIndex + ": " + row,
            row.afterColumns.get(columnIndex));
        Assert.assertNull("omitted after-image column must have no decoded value: " + row, row.after[columnIndex]);
    }

    private static void assertDmlDeleteAddresses(RowImages row) {
        Assert.assertTrue("expected DELETE_ROWS: " + row, row.isDelete());
        assertBlobRef(asUtf8(row.before[3]));
        assertBlobRef(asUtf8(row.before[4]));
    }

    private static List<RowImages> rowsForId(List<RowImages> rows, long id) {
        List<RowImages> result = new ArrayList<>();
        for (RowImages row : rows) {
            if (imageHasId(row.before, id) || imageHasId(row.after, id)) {
                result.add(row);
            }
        }
        return result;
    }

    private static List<RowImages> writeRowsForId(List<RowImages> rows, long id) {
        return filterRows(rowsForId(rows, id), true, false, false);
    }

    private static List<RowImages> updateRowsForId(List<RowImages> rows, long id) {
        return filterRows(rowsForId(rows, id), false, true, false);
    }

    private static List<RowImages> deleteRowsForId(List<RowImages> rows, long id) {
        return filterRows(rowsForId(rows, id), false, false, true);
    }

    private static List<RowImages> filterRows(List<RowImages> rows, boolean write, boolean update, boolean delete) {
        List<RowImages> result = new ArrayList<>();
        for (RowImages row : rows) {
            if ((write && row.isWrite()) || (update && row.isUpdate()) || (delete && row.isDelete())) {
                result.add(row);
            }
        }
        return result;
    }

    private static boolean imageHasId(Object[] image, long id) {
        return image != null && image[0] instanceof Number && ((Number) image[0]).longValue() == id;
    }

    private static RowImages onlyRow(List<RowImages> rows, String description) {
        Assert.assertEquals(description + ": " + rows, 1, rows.size());
        return rows.get(0);
    }

    private static RowImages findByAfterNote(List<RowImages> rows, String note) {
        List<RowImages> matches = new ArrayList<>();
        for (RowImages row : rows) {
            if (row.after != null && note.equals(row.after[5])) {
                matches.add(row);
            }
        }
        return onlyRow(matches, "row with after note " + note);
    }

    private static RowImages findByAfterBody(List<RowImages> rows, String body) {
        List<RowImages> matches = new ArrayList<>();
        for (RowImages row : rows) {
            if (row.after != null && row.after[3] != null && body.equals(asUtf8(row.after[3]))) {
                matches.add(row);
            }
        }
        return onlyRow(matches, "row with after body " + body);
    }

    private static RowImages findWriteByAfterNote(List<RowImages> rows, String note) {
        return findByAfterNote(filterRows(rows, true, false, false), note);
    }

    private static void assertLogicalValueAbsent(List<RowImages> rows, String value) {
        for (RowImages row : rows) {
            if (row.before != null && row.before[3] != null) {
                Assert.assertNotEquals(value, asUtf8(row.before[3]));
            }
            if (row.after != null && row.after[3] != null) {
                Assert.assertNotEquals(value, asUtf8(row.after[3]));
            }
        }
    }

    private static long[] findDifferentRouteValues(Connection connection, String table, String partitionColumn)
        throws Exception {
        String firstNode = null;
        long firstValue = 0;
        try (Statement statement = connection.createStatement()) {
            for (long candidate = 1; candidate <= 256; candidate++) {
                boolean hasResult = statement.execute("TRACE SELECT id FROM `" + table + "` WHERE `"
                    + partitionColumn + "`=" + candidate);
                if (hasResult) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        while (ignored.next()) {
                            // Drain the traced query before reading SHOW TRACE on the same statement.
                        }
                    }
                }
                String group = null;
                try (ResultSet trace = statement.executeQuery("SHOW TRACE")) {
                    while (trace.next()) {
                        if (trace.getString("GROUP_NAME") != null) {
                            group = trace.getString("GROUP_NAME");
                            break;
                        }
                    }
                }
                Assert.assertNotNull("TRACE did not expose a route for " + table + "." + partitionColumn, group);
                if (firstNode == null) {
                    firstNode = group;
                    firstValue = candidate;
                } else if (!firstNode.equals(group)) {
                    return new long[] {firstValue, candidate};
                }
            }
        }
        throw new AssertionError("cannot find values routed to different DNs for " + table + "." + partitionColumn);
    }

    private static void assertBlobRef(String value) {
        Assert.assertNotNull(value);
        Assert.assertTrue(value.matches("02[0-9a-f]{64}"));
    }

    private BinlogPosition currentBinlogPosition() throws Exception {
        return retryOnCdcHa("SHOW MASTER STATUS", () -> {
            try (Connection connection = getPolardbxConnection();
                ResultSet resultSet = connection.createStatement().executeQuery("SHOW MASTER STATUS")) {
                if (!resultSet.next()) {
                    throw new IOException("SHOW MASTER STATUS returned no rows");
                }
                String file = resultSet.getString("FILE");
                long position = resultSet.getLong("POSITION");
                if (file == null || file.isEmpty() || position < 4) {
                    throw new IOException("SHOW MASTER STATUS returned an invalid position: " + file + ":"
                        + position);
                }
                return new BinlogPosition(file, position);
            }
        });
    }

    private List<RowImages> dumpUntilMarker(BinlogPosition start, String expectedTable, long markerStartId)
        throws Exception {
        return retryOnCdcHa("dump row events from " + start.file + ":" + start.position,
            () -> dumpUntilMarkerOnce(start, expectedTable, markerStartId));
    }

    private List<RowImages> dumpUntilMarkerOnce(BinlogPosition start, String expectedTable, long markerStartId)
        throws Exception {
        List<RowImages> result = new ArrayList<>();
        int[] markerRows = {0};
        MysqlConnection connection = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        try {
            connection.connect();
            connection.dump(start.file, start.position, null, (event, position) -> {
                if (!(event instanceof RowsLogEvent)) {
                    return true;
                }
                RowsLogEvent rowsEvent = (RowsLogEvent) event;
                TableMapLogEvent table = rowsEvent.getTable();
                if (!DB_NAME.equalsIgnoreCase(table.getDbName())) {
                    return true;
                }
                if (MARKER_TABLE.equalsIgnoreCase(table.getTableName())) {
                    markerRows[0] += countWriteRowsInRange(rowsEvent, markerStartId,
                        markerStartId + DML_MARKER_ROW_COUNT);
                    return markerRows[0] < DML_MARKER_ROW_COUNT;
                }
                if (!expectedTable.equalsIgnoreCase(table.getTableName())) {
                    if (isLogicalTestTable(table.getTableName())) {
                        return true;
                    }
                    Assert.fail("unexpected GSI/replica row event in global binlog: " + table.getTableName());
                }
                assertLogicalExternalTypes(table, expectedTable);
                result.addAll(decodeRows(rowsEvent));
                return true;
            });
            if (markerRows[0] < DML_MARKER_ROW_COUNT) {
                throw new IOException("dump ended before all marker rows arrived: " + markerRows[0] + "/"
                    + DML_MARKER_ROW_COUNT);
            }
            return result;
        } finally {
            disconnectQuietly(connection);
        }
    }

    private static void insertMarker(Statement statement, long startId) throws Exception {
        StringBuilder markerValues = new StringBuilder();
        for (long id = startId; id < startId + DML_MARKER_ROW_COUNT; id++) {
            if (markerValues.length() > 0) {
                markerValues.append(',');
            }
            markerValues.append('(').append(id).append(')');
        }
        statement.executeUpdate("INSERT INTO `" + MARKER_TABLE + "` VALUES " + markerValues);
    }

    private static int countWriteRowsInRange(RowsLogEvent event, long startId, long endId) {
        Assert.assertTrue("marker must be a WRITE_ROWS event", isWrite(event));
        int count = 0;
        for (RowImages row : decodeRows(event)) {
            if (row.after != null && row.after[0] instanceof Number) {
                long id = ((Number) row.after[0]).longValue();
                if (id >= startId && id < endId) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isLogicalTestTable(String tableName) {
        return TABLE_NAME.equalsIgnoreCase(tableName) || DML_TABLE.equalsIgnoreCase(tableName)
            || TYPES_TABLE.equalsIgnoreCase(tableName);
    }

    private List<DdlImages> dumpDdlUntilMarker(BinlogPosition start, List<String> tableNames, long markerId)
        throws Exception {
        return retryOnCdcHa("dump DDL events from " + start.file + ":" + start.position,
            () -> dumpDdlUntilMarkerOnce(start, tableNames, markerId));
    }

    private List<DdlImages> dumpDdlUntilMarkerOnce(BinlogPosition start, List<String> tableNames, long markerId)
        throws Exception {
        List<DdlImages> result = new ArrayList<>();
        boolean[] markerSeen = {false};
        MysqlConnection connection = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        try {
            connection.connect();
            connection.dump(start.file, start.position, null, (event, position) -> {
                if (event instanceof RowsLogEvent) {
                    RowsLogEvent rowsEvent = (RowsLogEvent) event;
                    TableMapLogEvent table = rowsEvent.getTable();
                    if (DB_NAME.equalsIgnoreCase(table.getDbName())
                        && MARKER_TABLE.equalsIgnoreCase(table.getTableName())) {
                        markerSeen[0] = countWriteRowsInRange(rowsEvent, markerId, markerId + 1) > 0;
                        return !markerSeen[0];
                    }
                    return true;
                }
                if (!(event instanceof QueryLogEvent)) {
                    return true;
                }
                QueryLogEvent queryEvent = (QueryLogEvent) event;
                if (!DB_NAME.equalsIgnoreCase(queryEvent.getDbName())) {
                    return true;
                }
                String privateDdl = CommonUtils.extractPolarxOriginSql(queryEvent.getQuery());
                if (tableNames.stream().noneMatch(name -> privateDdl.toUpperCase().contains(name.toUpperCase()))) {
                    return true;
                }
                result.add(new DdlImages(privateDdl, extractMysqlDdl(queryEvent.getQuery())));
                return true;
            });
            if (!markerSeen[0]) {
                throw new IOException("dump ended before DDL marker arrived: " + markerId);
            }
            return result;
        } finally {
            disconnectQuietly(connection);
        }
    }

    private <T> T retryOnCdcHa(String operation, Callable<T> callable) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(CDC_HA_RETRY_TIMEOUT_SECONDS);
        for (int attempt = 1; ; attempt++) {
            try {
                return callable.call();
            } catch (Exception e) {
                if (!isTransientCdcHaFailure(e)) {
                    throw e;
                }

                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    log.warn("{} still failed after {} attempts and {} seconds: {}", operation, attempt,
                        CDC_HA_RETRY_TIMEOUT_SECONDS, e.toString());
                    throw e;
                }

                long sleepNanos = Math.min(TimeUnit.SECONDS.toNanos(CDC_HA_RETRY_INTERVAL_SECONDS), remainingNanos);
                log.warn("{} failed on attempt {}, retrying after up to {} seconds ({} seconds remaining): {}",
                    operation, attempt, CDC_HA_RETRY_INTERVAL_SECONDS,
                    TimeUnit.NANOSECONDS.toSeconds(remainingNanos), e.toString());
                try {
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw interruptedException;
                }
            }
        }
    }

    private static boolean isTransientCdcHaFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException || current instanceof SQLException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    private static void disconnectQuietly(MysqlConnection connection) {
        try {
            connection.disconnect();
        } catch (IOException e) {
            log.warn("failed to close CDC dump connection", e);
        }
    }

    private static String extractMysqlDdl(String query) {
        StringBuilder result = new StringBuilder();
        boolean foundMysqlDdl = false;
        try (Scanner scanner = new Scanner(query)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!foundMysqlDdl && line.startsWith("#")) {
                    continue;
                }
                foundMysqlDdl = true;
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(line);
            }
        }
        return result.toString().trim();
    }

    private static void assertLogicalExternalTypes(TableMapLogEvent table, String tableName) {
        TableMapLogEvent.ColumnInfo[] columns = table.getColumnInfo();
        if (TABLE_NAME.equalsIgnoreCase(tableName)) {
            Assert.assertEquals(5, table.getColumnCnt());
            assertLogicalBlobColumn(columns[2], 4);
            assertLogicalBlobColumn(columns[3], 4);
        } else if (DML_TABLE.equalsIgnoreCase(tableName)) {
            Assert.assertEquals(6, table.getColumnCnt());
            assertLogicalBlobColumn(columns[3], 4);
            assertLogicalBlobColumn(columns[4], 4);
        } else if (TYPES_TABLE.equalsIgnoreCase(tableName)) {
            Assert.assertEquals(9, table.getColumnCnt());
            int[] expectedMeta = {2, 1, 3, 4, 2, 1, 3, 4};
            for (int i = 0; i < expectedMeta.length; i++) {
                assertLogicalBlobColumn(columns[i + 1], expectedMeta[i]);
            }
        } else {
            Assert.fail("missing logical external type expectation for " + tableName);
        }
    }

    private static void assertLogicalBlobColumn(TableMapLogEvent.ColumnInfo column, int expectedMeta) {
        Assert.assertEquals(com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_BLOB, column.type);
        Assert.assertEquals(expectedMeta, column.meta);
    }

    private static List<RowImages> decodeRows(RowsLogEvent event) {
        List<RowImages> result = new ArrayList<>();
        RowsLogBuffer buffer = event.getRowsBuf("utf8");
        BitSet beforeColumns = event.getColumns();
        while (buffer.nextOneRow(beforeColumns)) {
            Object[] before = decodeImage(buffer, beforeColumns, event.getTable());
            Object[] after = null;
            BitSet rowBeforeColumns = beforeColumns;
            BitSet afterColumns = null;
            if (event instanceof UpdateRowsLogEvent) {
                afterColumns = event.getChangeColumns();
                Assert.assertTrue(buffer.nextOneRow(afterColumns));
                after = decodeImage(buffer, afterColumns, event.getTable());
            }
            if (isWrite(event)) {
                after = before;
                before = null;
                afterColumns = beforeColumns;
                rowBeforeColumns = null;
            }
            result.add(new RowImages(event.getHeader().getType(), before, after, rowBeforeColumns, afterColumns));
        }
        return result;
    }

    private static Object[] decodeImage(RowsLogBuffer buffer, BitSet columns, TableMapLogEvent table) {
        Object[] values = new Object[table.getColumnCnt()];
        for (int i = 0; i < table.getColumnCnt(); i++) {
            if (!columns.get(i)) {
                continue;
            }
            TableMapLogEvent.ColumnInfo info = table.getColumnInfo()[i];
            Serializable value = buffer.nextValue(info.type, info.meta, false);
            values[i] = value;
        }
        return values;
    }

    private static boolean isWrite(RowsLogEvent event) {
        int type = event.getHeader().getType();
        return type == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT
            || type == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT_V1;
    }

    private static String asUtf8(Object value) {
        return new String((byte[]) value, StandardCharsets.UTF_8);
    }

    private static class BinlogPosition {
        private final String file;
        private final long position;

        private BinlogPosition(String file, long position) {
            this.file = file;
            this.position = position;
        }
    }

    private static class RowImages {
        private final int eventType;
        private final Object[] before;
        private final Object[] after;
        private final BitSet beforeColumns;
        private final BitSet afterColumns;

        private RowImages(int eventType, Object[] before, Object[] after, BitSet beforeColumns, BitSet afterColumns) {
            this.eventType = eventType;
            this.before = before;
            this.after = after;
            this.beforeColumns = beforeColumns == null ? null : (BitSet) beforeColumns.clone();
            this.afterColumns = afterColumns == null ? null : (BitSet) afterColumns.clone();
        }

        private boolean isWrite() {
            return eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT
                || eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT_V1;
        }

        private boolean isUpdate() {
            return eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT
                || eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT_V1;
        }

        private boolean isDelete() {
            return eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT
                || eventType == com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT_V1;
        }

        @Override
        public String toString() {
            return "RowImages{" + "eventType=" + eventType + ", before=" + Arrays.toString(before)
                + ", after=" + Arrays.toString(after) + ", beforeColumns=" + beforeColumns
                + ", afterColumns=" + afterColumns + '}';
        }
    }

    private static class DdlImages {
        private final String privateDdl;
        private final String mysqlDdl;

        private DdlImages(String privateDdl, String mysqlDdl) {
            this.privateDdl = privateDdl;
            this.mysqlDdl = mysqlDdl;
        }

        @Override
        public String toString() {
            return "DdlImages{" + "privateDdl='" + privateDdl + '\''
                + ", mysqlDdl='" + mysqlDdl + '\'' + '}';
        }
    }
}
