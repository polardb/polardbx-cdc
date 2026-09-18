/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.ConfigConstant;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end external-column verification across a PolarDB-X replica topology.
 *
 * <p>The {@code polardbx*} properties identify the source instance and the
 * {@code cdcSyncDb*} properties identify one aggregate PolarDB-X GDN target. The test deliberately
 * uses two independent JDBC endpoints and validates the final target schema and logical values;
 * physical BlobRef and staging representations must never be visible at the target SQL layer.</p>
 *
 * <p>This class covers supported P0/P1 correctness scenarios only. CDC process restarts, injected
 * failures, long-running stress, compatibility-off mode, and route-stable primary-key changes in a
 * multi-stream topology are outside its scope.</p>
 */
public class ExternalColumnReplicaTest extends RplBaseTestCase {

    private static final String DB_NAME = "cdc_external_column_replica_test";
    private static final String LEGACY_DB_NAME = "cdc_external_column_replica_drds_test";
    private static final long REPLICA_TIMEOUT_MS = 180_000L;
    private static final long MCE_REPLICA_TIMEOUT_MS = 300_000L;
    private static final int ONE_MIB = 1024 * 1024;
    private static final int DEFAULT_STAGING_THRESHOLD_BYTES = 100 * 1024;
    private static final int MAX_TESTABLE_STAGING_THRESHOLD_BYTES = 4 * ONE_MIB;
    private static final int MIN_TRAFFIC_TRANSACTIONS_PER_PAUSE = 20;
    private static final String BLOB_REF_LIKE_VALUE = "02"
        + "0000000000000000000000000000000000000000000000000000000000000000";
    private static boolean replicaDatabasePrepared;

    @BeforeClass
    public static void prepareReplicaDatabase() throws Exception {
        Assert.assertFalse("ExternalColumnReplicaTest expects one aggregate GDN target: set usingBinlogX=false",
            PropertiesUtil.usingBinlogX);

        String sourceEndpoint = endpoint(ConfigConstant.POLARDBX_ADDRESS, ConfigConstant.POLARDBX_PORT);
        String targetEndpoint = endpoint(ConfigConstant.CDC_SYNC_DB_ADDRESS, ConfigConstant.CDC_SYNC_DB_PORT);
        Assert.assertNotEquals("source and replica JDBC endpoints must be different", sourceEndpoint, targetEndpoint);

        try (Connection connection = ConnectionManager.getInstance().getDruidMetaConnection();
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT param_val FROM inst_config "
                    + "WHERE param_key='ENABLE_EXTERNALIZED_BINLOG_COMPATIBILITY' "
                    + "ORDER BY gmt_modified DESC LIMIT 1")) {
            if (resultSet.next()) {
                String value = resultSet.getString(1);
                Assert.assertTrue(
                    "GDN external-column tests require ENABLE_EXTERNALIZED_BINLOG_COMPATIBILITY=true, but was "
                        + value,
                    "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value));
            }
            // No explicit inst_config row means the CN default (true) is in effect.
        }

        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB_NAME + "`");
            statement.execute("CREATE DATABASE `" + DB_NAME + "` MODE = 'auto'");
            statement.execute("DROP DATABASE IF EXISTS `" + LEGACY_DB_NAME + "`");
            statement.execute("CREATE DATABASE `" + LEGACY_DB_NAME + "` MODE = 'drds'");
            replicaDatabasePrepared = true;
        }
    }

    /**
     * Removes the isolated source database after all replica scenarios finish. The DROP itself is
     * intentionally left to the configured replication link, just like every other logical DDL.
     */
    @AfterClass
    public static void cleanupReplicaDatabase() throws Exception {
        if (!replicaDatabasePrepared) {
            return;
        }
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB_NAME + "`");
            statement.execute("DROP DATABASE IF EXISTS `" + LEGACY_DB_NAME + "`");
        }
    }

    /**
     * Reproduces the legacy DRDS SHOW CREATE suffix emitted for a shard key containing a backtick.
     * Loading metadata for this ordinary table must not stop the same schema's stream before the
     * following externalized table is created and populated.
     */
    @Test
    public void testMalformedLegacyShowCreateSuffixDoesNotBlockExternalReplica() throws Exception {
        String specialTable = "gxw_test-minus";
        String externalTable = "ext_after_special_show_create";
        try (Statement statement = polardbxConnection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + qualified(LEGACY_DB_NAME, specialTable));
            statement.execute("CREATE TABLE " + qualified(LEGACY_DB_NAME, specialTable)
                + " (`col``backtick` INT, `c2` INT) "
                + "dbpartition by hash(`col``backtick`)");
            statement.execute("INSERT INTO " + qualified(LEGACY_DB_NAME, specialTable)
                + " (`col``backtick`,`c2`) VALUES (1,2)");

            statement.execute("DROP TABLE IF EXISTS " + qualified(LEGACY_DB_NAME, externalTable));
            statement.execute("CREATE TABLE " + qualified(LEGACY_DB_NAME, externalTable)
                + " (`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, `note` VARCHAR(64)) "
                + "dbpartition by hash(`id`)");
            statement.execute("INSERT INTO " + qualified(LEGACY_DB_NAME, externalTable)
                + " (`id`,`body`,`note`) VALUES (1,'after-special','replica-must-advance')");
        }

        assertReplicaQueryEventually(
            "SELECT `col``backtick`,`c2` FROM " + qualified(LEGACY_DB_NAME, specialTable),
            REPLICA_TIMEOUT_MS);
        assertReplicaQueryEventually(
            "SELECT `id`,`body`,`note` FROM " + qualified(LEGACY_DB_NAME, externalTable),
            REPLICA_TIMEOUT_MS);
    }

    /**
     * Covers direct external-table CRUD with NULL, empty, Unicode, arbitrary binary bytes, a
     * 66-character lowercase-hex user value that resembles a BlobRef, values at threshold-1,
     * threshold, and threshold+1, and 1 MiB TEXT/BLOB values. The size-boundary rows prove
     * compatibility mode takes precedence over the legacy staging threshold and that no logical
     * value is replaced by a physical address.
     */
    @Test
    public void testDirectCrudAndValueShapes() throws Exception {
        String table = "ext_replica_values";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, "
                + "`payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 4");

        int stagingThreshold = sourceIntConfigOrDefault(
            "EXT_STAGING_THRESHOLD_BYTES", DEFAULT_STAGING_THRESHOLD_BYTES);
        Assert.assertTrue("EXT_STAGING_THRESHOLD_BYTES must be in the bounded integration-test range, but was "
                + stagingThreshold,
            stagingThreshold > 1 && stagingThreshold <= MAX_TESTABLE_STAGING_THRESHOLD_BYTES);
        String largeText = repeat('L', ONE_MIB);
        byte[] largePayload = patternedBytes(ONE_MIB);
        try (Connection connection = getPolardbxConnection(DB_NAME);
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` (`id`,`body`,`payload`,`note`) VALUES (?,?,?,?)")) {
            addExternalBatch(statement, 1, null, null, "null-seed");
            addExternalBatch(statement, 2, "", new byte[0], "empty");
            addExternalBatch(statement, 3, BLOB_REF_LIKE_VALUE,
                BLOB_REF_LIKE_VALUE.getBytes("UTF-8"), "blobref-like");
            addExternalBatch(statement, 4, "small-中文-🙂", bytes(0x00, 0xFF, 0x41), "small");
            addExternalBatch(statement, 5, repeat('B', stagingThreshold - 1),
                patternedBytes(stagingThreshold - 1), "threshold-below");
            addExternalBatch(statement, 6, repeat('E', stagingThreshold),
                patternedBytes(stagingThreshold), "threshold-exact");
            addExternalBatch(statement, 7, repeat('A', stagingThreshold + 1),
                patternedBytes(stagingThreshold + 1), "threshold-above");
            addExternalBatch(statement, 8, largeText, largePayload, "large");
            addExternalBatch(statement, 9, "delete-me", bytes(0x66), "delete");
            statement.executeBatch();
        }
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE `" + table
                + "` SET body='from-null',payload=X'0001FF',note='null-restored' WHERE id=1");
            statement.executeUpdate("UPDATE `" + table + "` SET note='small-note-only' WHERE id=4");
            statement.executeUpdate("DELETE FROM `" + table + "` WHERE id=9");
        }

        assertReplicaQueryEventually(externalDigestQuery(table, "id", "note"), REPLICA_TIMEOUT_MS);
        Assert.assertEquals("BlobRef-like user text must survive GDN without a second decode",
            BLOB_REF_LIKE_VALUE,
            awaitTargetScalar("SELECT body FROM " + qualified(table) + " WHERE id=3", REPLICA_TIMEOUT_MS));
        Assert.assertEquals(String.valueOf(ONE_MIB),
            awaitTargetScalar("SELECT OCTET_LENGTH(body) FROM " + qualified(table) + " WHERE id=8",
                REPLICA_TIMEOUT_MS));
        Assert.assertEquals(String.valueOf(ONE_MIB),
            awaitTargetScalar("SELECT OCTET_LENGTH(payload) FROM " + qualified(table) + " WHERE id=8",
                REPLICA_TIMEOUT_MS));
        assertTargetCreateSql(table, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE", "`PAYLOAD` LONGBLOB EXTERNALIZE"},
            new String[] {"_ADDR_"});
    }

    /**
     * Covers the six supported two-external-column mutations: ordinary UPDATE and real partition
     * relocate with zero, one, or both external values changed. It also executes one multi-row
     * UPDATE in which different rows change different external columns, exercising per-row after
     * bitmaps and preventing an unchanged BlobRef from being written as target data.
     */
    @Test
    public void testPartialUpdateRelocateAndPerRowBitmaps() throws Exception {
        String table = "ext_replica_update";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `sk` BIGINT NOT NULL, "
                + "`body` LONGTEXT EXTERNALIZE, `payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`sk`) PARTITIONS 4");

        long[] routes;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routes = findDifferentRouteValues(connection, table, "sk");
        }
        try (Connection connection = getPolardbxConnection(DB_NAME);
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` VALUES (?,?,?,?,?)")) {
            for (int id = 1; id <= 8; id++) {
                statement.setInt(1, id);
                statement.setLong(2, routes[0]);
                statement.setString(3, "body-" + id);
                statement.setBytes(4, bytes(0x10 + id, 0x00, 0xF0 - id));
                statement.setString(5, "seed-" + id);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE `" + table + "` SET note='update-none' WHERE id=1");
            statement.executeUpdate("UPDATE `" + table
                + "` SET body='update-body',note='update-one' WHERE id=2");
            statement.executeUpdate("UPDATE `" + table
                + "` SET body='update-both',payload=X'2200FE',note='update-both' WHERE id=3");

            statement.executeUpdate("UPDATE `" + table + "` SET sk=" + routes[1]
                + ",note='relocate-none' WHERE id=4");
            statement.executeUpdate("UPDATE `" + table + "` SET sk=" + routes[1]
                + ",body='relocate-body',note='relocate-one' WHERE id=5");
            statement.executeUpdate("UPDATE `" + table + "` SET sk=" + routes[1]
                + ",body='relocate-both',payload=X'6600FC',note='relocate-both' WHERE id=6");

            statement.executeUpdate("UPDATE `" + table + "` SET "
                + "body=IF(id=7,'mixed-body',body),"
                + "payload=IF(id=8,X'8800FA',payload),note='mixed' WHERE id IN (7,8)");
        }

        assertReplicaQueryEventually(externalDigestQuery(table, "id", "sk", "note"), REPLICA_TIMEOUT_MS);
    }

    /**
     * Covers transaction-local external staging across different DNs for committed mutations,
     * full rollback, and ROLLBACK TO SAVEPOINT. The final source/target comparison proves rolled
     * back raw values do not leak and surviving values remain associated with the right row.
     */
    @Test
    public void testCommitRollbackAndSavepointAcrossDns() throws Exception {
        String table = "ext_replica_txn";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `sk` BIGINT NOT NULL, "
                + "`body` LONGTEXT EXTERNALIZE, `payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`sk`) PARTITIONS 4");

        long[] routes;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routes = findDifferentRouteValues(connection, table, "sk");
        }
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate(insertSql(table, 1001, routes[0], "commit-a", bytes(0x01), "commit"));
            statement.executeUpdate(insertSql(table, 1002, routes[1], "commit-b", bytes(0x02), "commit"));
            statement.executeUpdate("UPDATE `" + table
                + "` SET body='commit-a-new',payload=X'0100FF',note='updated' WHERE id=1001");
            connection.commit();

            statement.executeUpdate(insertSql(table, 1010, routes[1], "rollback", bytes(0x10), "rollback"));
            statement.executeUpdate("UPDATE `" + table + "` SET body='rollback-update' WHERE id=1001");
            connection.rollback();

            statement.executeUpdate(insertSql(table, 1003, routes[0], "savepoint-keep", bytes(0x03), "keep"));
            statement.execute("SAVEPOINT ext_replica_sp");
            statement.executeUpdate(insertSql(table, 1004, routes[1], "savepoint-drop", bytes(0x04), "drop"));
            statement.executeUpdate("UPDATE `" + table + "` SET body='savepoint-drop-update' WHERE id=1003");
            statement.execute("ROLLBACK TO SAVEPOINT ext_replica_sp");
            statement.executeUpdate("UPDATE `" + table + "` SET note='savepoint-committed' WHERE id=1003");
            connection.commit();
        }

        assertReplicaQueryEventually(externalDigestQuery(table, "id", "sk", "note"), REPLICA_TIMEOUT_MS);
    }

    /**
     * Runs committed INSERT/UPDATE mutations and a rolled-back row across two DNs under TSO, XA,
     * and XA_TSO. This validates that external staging and GDN application retain the same atomic
     * transaction outcome for every supported distributed transaction policy.
     */
    @Test
    public void testTsoXaAndXaTsoAcrossDns() throws Exception {
        String table = "ext_replica_policy";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `sk` BIGINT NOT NULL, "
                + "`body` LONGTEXT EXTERNALIZE, `payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`sk`) PARTITIONS 4");

        long[] routes;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routes = findDifferentRouteValues(connection, table, "sk");
        }
        for (int i = 0; i < TransactionPolicy.values().length; i++) {
            TransactionPolicy policy = TransactionPolicy.values()[i];
            long rowId = 2001L + i * 100L;
            String tag = policy.expectedName.toLowerCase();
            try (Connection connection = getPolardbxConnection(DB_NAME);
                Statement statement = connection.createStatement()) {
                policy.apply(statement);
                connection.setAutoCommit(false);
                statement.executeUpdate(insertSql(table, rowId, routes[0], tag + "-seed",
                    bytes(0x20 + i), tag + "-seed"));
                statement.executeUpdate(insertSql(table, rowId + 1, routes[1], tag + "-other-dn",
                    bytes(0x30 + i), tag + "-other-dn"));
                statement.executeUpdate("UPDATE `" + table + "` SET body='" + tag
                    + "-updated',payload=X'" + String.format("%02X00FE", 0x40 + i)
                    + "',note='" + tag + "-updated' WHERE id=" + rowId);
                connection.commit();

                statement.executeUpdate(insertSql(table, rowId + 9, routes[1], tag + "-rollback",
                    bytes(0x50 + i), tag + "-rollback"));
                connection.rollback();
            }
        }

        assertReplicaQueryEventually(externalDigestQuery(table, "id", "sk", "note"), REPLICA_TIMEOUT_MS);
    }

    /**
     * Covers ordinary and ignored INSERT, UPSERT insert/conflict paths with all/one/no external
     * value changed, REPLACE, multi-row batch INSERT, and INSERT SELECT. Final target equality
     * catches both missing writes and accidental application of unused VALUES() BlobRefs.
     */
    @Test
    public void testInsertUpsertReplaceBatchAndInsertSelect() throws Exception {
        String table = "ext_replica_insert_forms";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, "
                + "`payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 4");

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (1,'insert',X'0100FF','insert')");
            statement.executeUpdate("INSERT IGNORE INTO `" + table
                + "` VALUES (1,'ignored',X'02','ignored')");
            statement.executeUpdate("INSERT IGNORE INTO `" + table
                + "` VALUES (2,'ignore-new',X'0200FE','ignore-new')");

            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (3,'upsert-seed',X'03','seed') "
                + "ON DUPLICATE KEY UPDATE body=VALUES(body),payload=VALUES(payload),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (3,'upsert-all',X'0300FD','all') "
                + "ON DUPLICATE KEY UPDATE body=VALUES(body),payload=VALUES(payload),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (3,'upsert-body',X'04','body-only') "
                + "ON DUPLICATE KEY UPDATE body=VALUES(body),note=VALUES(note)");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (3,'unused',X'05','note-only') "
                + "ON DUPLICATE KEY UPDATE note=VALUES(note)");

            statement.executeUpdate("REPLACE INTO `" + table + "` VALUES (4,'replace-seed',X'04','seed')");
            statement.executeUpdate("REPLACE INTO `" + table + "` VALUES (4,'replace-final',X'0400FC','final')");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES "
                + "(5,'batch',X'0500FB','batch'),(6,NULL,NULL,'null'),(7,'',X'','empty')");
            statement.executeUpdate("INSERT INTO `" + table
                + "` SELECT id+100,body,payload,CONCAT(note,'-copy') FROM `" + table + "` WHERE id IN (5,6)");
        }

        assertReplicaQueryEventually(externalDigestQuery(table, "id", "note"), REPLICA_TIMEOUT_MS);
    }

    /**
     * Seeds one conflicting row only on the target, then commits a mixed source transaction whose external-table
     * UPDATE is applied before a two-row INSERT batch reaches that conflict. Replica must roll back the complete
     * first target transaction and replay the source transaction in a new connection: the final UPDATE, both
     * external INSERTs on different groups, and the ordinary-table INSERT must converge atomically.
     */
    @Test
    public void testExternalizedInsertDuplicateRollsBackBeforeOverwriteReplay() throws Exception {
        String externalTable = "ext_replica_insert_duplicate";
        String ordinaryTable = "ordinary_replica_insert_duplicate";
        recreateSourceTable(externalTable,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, "
                + "`payload` LONGBLOB EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 4");
        recreateSourceTable(ordinaryTable,
            "`id` BIGINT NOT NULL PRIMARY KEY, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 4");

        executeSource("INSERT INTO `" + externalTable
            + "` VALUES (10000,'baseline',X'10','baseline')");
        assertReplicaQueryEventually(externalDigestQuery(externalTable, "id", "note"), REPLICA_TIMEOUT_MS);
        assertReplicaQueryEventually(
            "SELECT `id`,`note` FROM " + qualified(ordinaryTable) + " ORDER BY `id`", REPLICA_TIMEOUT_MS);

        long[] routes;
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            routes = findDifferentRouteValues(connection, externalTable, "id");
        }

        long targetOnlyServerId = resolveTargetOnlyServerId();
        executeTargetOnly(targetOnlyServerId, "INSERT INTO `" + externalTable + "` VALUES (" + routes[0]
            + ",'target-stale',X'7F','target-stale')");

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("UPDATE `" + externalTable
                + "` SET note='updated-before-conflict' WHERE id=10000");
            statement.executeUpdate("INSERT INTO `" + ordinaryTable + "` VALUES (1,'same-source-transaction')");
            statement.executeUpdate("INSERT INTO `" + externalTable + "` VALUES (" + routes[0]
                + ",'source-overwrite',X'0100FF','source-overwrite')");
            statement.executeUpdate("INSERT INTO `" + externalTable + "` VALUES (" + routes[1]
                + ",'source-new',X'0200FE','source-new')");
            connection.commit();
        }

        assertReplicaQueryEventually(externalDigestQuery(externalTable, "id", "note"), REPLICA_TIMEOUT_MS);
        assertReplicaQueryEventually(
            "SELECT `id`,`note` FROM " + qualified(ordinaryTable) + " ORDER BY `id`", REPLICA_TIMEOUT_MS);

        executeTargetOnly(targetOnlyServerId, "INSERT INTO `" + externalTable
            + "` VALUES (20000,'target-single-stale',X'20','target-single-stale')");
        executeSource("INSERT INTO `" + externalTable
            + "` VALUES (20000,'source-single-overwrite',X'2000DF','source-single-overwrite')");
        assertReplicaQueryEventually(externalDigestQuery(externalTable, "id", "note"), REPLICA_TIMEOUT_MS);
    }

    /**
     * Covers direct DDL and DML for every supported logical TEXT/BLOB family member, including
     * Unicode strings, embedded zero bytes, non-UTF8 binary bytes, NULLs, and subsequent updates.
     * The target schema must retain every EXTERNALIZE attribute without exposing address columns.
     */
    @Test
    public void testAllTextAndBlobTypes() throws Exception {
        String table = "ext_replica_types";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, "
                + "`c_text` TEXT EXTERNALIZE, `c_tinytext` TINYTEXT EXTERNALIZE, "
                + "`c_mediumtext` MEDIUMTEXT EXTERNALIZE, `c_longtext` LONGTEXT EXTERNALIZE, "
                + "`c_blob` BLOB EXTERNALIZE, `c_tinyblob` TINYBLOB EXTERNALIZE, "
                + "`c_mediumblob` MEDIUMBLOB EXTERNALIZE, `c_longblob` LONGBLOB EXTERNALIZE"
                + ") DEFAULT CHARSET=utf8mb4 PARTITION BY KEY(`id`) PARTITIONS 4");

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (1,"
                + "'text-中文-🙂','tiny-ß','medium-Καλημέρα','long-日本語-🚀',"
                + "X'0001FF',X'7F00',X'E4B8AD00',X'FFFE0041')");
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES (2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)");
            statement.executeUpdate("UPDATE `" + table + "` SET "
                + "c_text='text-new-🌍',c_tinytext='tiny-new-é',c_mediumtext='medium-new-한글',"
                + "c_longtext='long-new-🧪',c_blob=X'1000FE',c_tinyblob=X'11FF',"
                + "c_mediumblob=X'120013',c_longblob=X'14FF0015' WHERE id=1");
        }

        String query = "SELECT id"
            + digestExpression("c_text") + digestExpression("c_tinytext")
            + digestExpression("c_mediumtext") + digestExpression("c_longtext")
            + digestExpression("c_blob") + digestExpression("c_tinyblob")
            + digestExpression("c_mediumblob") + digestExpression("c_longblob")
            + " FROM " + qualified(table) + " ORDER BY id";
        assertReplicaQueryEventually(query, REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(table, MCE_REPLICA_TIMEOUT_MS,
            new String[] {
                "`C_TEXT` TEXT EXTERNALIZE", "`C_TINYTEXT` TINYTEXT EXTERNALIZE",
                "`C_MEDIUMTEXT` MEDIUMTEXT EXTERNALIZE", "`C_LONGTEXT` LONGTEXT EXTERNALIZE",
                "`C_BLOB` BLOB EXTERNALIZE", "`C_TINYBLOB` TINYBLOB EXTERNALIZE",
                "`C_MEDIUMBLOB` MEDIUMBLOB EXTERNALIZE", "`C_LONGBLOB` LONGBLOB EXTERNALIZE"},
            new String[] {"_ADDR_"});
    }

    /**
     * Pauses source MCE immediately before READ_ADDR while foreground INSERT/UPDATE/DELETE and
     * source/target logical reads keep running. Before source CONTINUE, no CDC marker may have been
     * emitted: both logical schemas must still expose an ordinary LONGTEXT column and a sentinel
     * transaction must already reach the target. After source CONTINUE emits the marker, target MCE
     * must pause at the same gate while a second sentinel and foreground traffic keep replicating.
     * The test then continues target DDL explicitly and requires the externalized target schema and
     * all final row digests to converge. The known concurrent-DDL flashback schema-change error is
     * retried just like CN MCE stress tests; every other reader/writer error is fatal.
     */
    @Test
    public void testMcePauseWithContinuousReadWriteTraffic() throws Exception {
        String table = "ext_replica_mce_traffic";
        recreateSourceTable(table,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT, `body_len` BIGINT NOT NULL, "
                + "`body_md5` CHAR(32) NOT NULL, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 4");
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + table + "` VALUES "
                + "(1,'seed',OCTET_LENGTH('seed'),MD5('seed'),'hot'),"
                + "(2,'baseline',OCTET_LENGTH('baseline'),MD5('baseline'),'baseline')");
        }
        assertReplicaQueryEventually(mceTrafficDigestQuery(table), REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(table, REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT"}, new String[] {"EXTERNALIZE", "_ADDR_"});

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong committedTransactions = new AtomicLong();
        AtomicLong successfulReadRounds = new AtomicLong();
        AtomicReference<Throwable> trafficFailure = new AtomicReference<>();
        ExecutorService trafficExecutor = Executors.newFixedThreadPool(2);
        Future<?> writer = trafficExecutor.submit(
            () -> runMceWriter(table, running, committedTransactions, trafficFailure));
        Future<?> reader = trafficExecutor.submit(
            () -> runMceReader(table, running, successfulReadRounds, trafficFailure));

        long sourceJobId = -1L;
        try {
            awaitTrafficAdvance(committedTransactions, 0L, MIN_TRAFFIC_TRANSACTIONS_PER_PAUSE,
                trafficFailure, 30_000L);

            executeSource("/*+TDDL:cmd_extra(ENABLE_ASYNC_DDL=true,PURE_ASYNC_DDL_MODE=true,"
                + "MCE_PAUSE_BEFORE_READ_CUTOVER=true)*/ ALTER TABLE `" + table
                + "` MODIFY COLUMN `body` LONGTEXT EXTERNALIZE");
            try (Connection sourceControl = getPolardbxConnection(DB_NAME)) {
                sourceJobId = awaitDdlJob(sourceControl, table, MCE_REPLICA_TIMEOUT_MS);
                awaitDdlState(sourceControl, sourceJobId, "PAUSED", true, MCE_REPLICA_TIMEOUT_MS);
                long sourcePausedAt = committedTransactions.get();
                awaitTrafficAdvance(committedTransactions, sourcePausedAt,
                    MIN_TRAFFIC_TRANSACTIONS_PER_PAUSE, trafficFailure, 30_000L);

                // READ_ADDR has not happened, so TableSync has not emitted the external-column CDC marker.
                assertCreateSql(sourceControl, "source", table,
                    new String[] {"`BODY` LONGTEXT"}, new String[] {"EXTERNALIZE", "_ADDR_"});
                assertCreateSql(cdcSyncDbConnection, "target", table,
                    new String[] {"`BODY` LONGTEXT"}, new String[] {"EXTERNALIZE", "_ADDR_"});
                insertMceTrafficRow(table, 9_000_001L, "pre-marker-sentinel", "source-paused");
                Assert.assertEquals("pre-marker-sentinel",
                    awaitTargetScalar("SELECT body FROM " + qualified(table)
                        + " WHERE id=9000001", REPLICA_TIMEOUT_MS));
                assertDdlState(sourceControl, sourceJobId, "PAUSED", true);

                continueDdl(sourceControl, sourceJobId);
                awaitDdlDone(sourceControl, sourceJobId, MCE_REPLICA_TIMEOUT_MS);
            }

            try (Connection targetControl = getCdcSyncDbConnection(DB_NAME)) {
                long targetJobId = awaitDdlJob(targetControl, table, MCE_REPLICA_TIMEOUT_MS);
                awaitDdlState(targetControl, targetJobId, "PAUSED", true, MCE_REPLICA_TIMEOUT_MS);
                assertCreateSql(targetControl, "target", table,
                    new String[] {"`BODY` LONGTEXT"}, new String[] {"EXTERNALIZE", "_ADDR_"});

                // This transaction is generated after the source marker and must pass the paused target MCE.
                long targetPausedAt = committedTransactions.get();
                insertMceTrafficRow(table, 9_000_002L, "post-marker-sentinel", "target-paused");
                Assert.assertEquals("post-marker-sentinel",
                    awaitTargetScalar("SELECT body FROM " + qualified(table)
                        + " WHERE id=9000002", MCE_REPLICA_TIMEOUT_MS));
                awaitTrafficAdvance(committedTransactions, targetPausedAt,
                    MIN_TRAFFIC_TRANSACTIONS_PER_PAUSE, trafficFailure, 30_000L);
                assertDdlState(targetControl, targetJobId, "PAUSED", true);

                continueDdl(targetControl, targetJobId);
                awaitDdlDone(targetControl, targetJobId, MCE_REPLICA_TIMEOUT_MS);
            }
        } finally {
            running.set(false);
            stopTrafficFuture(writer);
            stopTrafficFuture(reader);
            trafficExecutor.shutdownNow();
            trafficExecutor.awaitTermination(30L, TimeUnit.SECONDS);
            finishPausedDdlBestEffort(false, table);
            finishPausedDdlBestEffort(true, table);
        }

        assertNoTrafficFailure(trafficFailure);
        Assert.assertTrue("continuous MCE reader must complete at least one source/target round",
            successfulReadRounds.get() > 0L);
        assertNoMceTrafficCorruption(polardbxConnection, table);
        assertNoMceTrafficCorruption(cdcSyncDbConnection, table);
        assertReplicaQueryEventually(mceTrafficDigestQuery(table), MCE_REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(table, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE"}, new String[] {"_ADDR_"});
    }

    /**
     * Covers replica DDL application for directly externalized columns, ADD/DROP EXTERNALIZE, and
     * a complete MCE externalize-to-internalize round trip with DML before, between, and after the
     * transitions. The target must track the same logical schema and final data at every terminal
     * state rather than replaying source physical address-column DDL.
     */
    @Test
    public void testLogicalDdlAndMceRoundTrip() throws Exception {
        String directTable = "ext_replica_ddl";
        recreateSourceTable(directTable,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT EXTERNALIZE, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 2");
        assertTargetCreateSql(directTable, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE"}, new String[] {"_ADDR_"});

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + directTable + "` ADD COLUMN `payload` LONGBLOB EXTERNALIZE");
            statement.executeUpdate("INSERT INTO `" + directTable
                + "` VALUES (1,'direct-body','direct',X'0100FF')");
        }
        assertReplicaQueryEventually(externalDigestQuery(directTable, "id", "note"), MCE_REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(directTable, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE", "`PAYLOAD` LONGBLOB EXTERNALIZE"},
            new String[] {"_ADDR_"});
        executeSource("ALTER TABLE `" + directTable + "` DROP COLUMN `payload`");
        assertTargetCreateSql(directTable, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE"}, new String[] {"`PAYLOAD`", "_ADDR_"});

        String mceTable = "ext_replica_mce";
        recreateSourceTable(mceTable,
            "`id` BIGINT NOT NULL PRIMARY KEY, `body` LONGTEXT, `payload` LONGBLOB, `note` VARCHAR(64)"
                + ") PARTITION BY KEY(`id`) PARTITIONS 2");
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO `" + mceTable + "` VALUES (1,'plain',X'01','plain')");
            statement.execute("ALTER TABLE `" + mceTable + "` MODIFY COLUMN `body` LONGTEXT EXTERNALIZE");
            // RPL submits MCE asynchronously. Keep consecutive same-table MCE DDLs out of the target planning
            // window until RPL provides a durable same-table async-DDL ordering barrier.
            awaitTargetDdlTerminal(mceTable, MCE_REPLICA_TIMEOUT_MS,
                new String[] {"`BODY` LONGTEXT EXTERNALIZE", "`PAYLOAD` LONGBLOB"},
                new String[] {"`PAYLOAD` LONGBLOB EXTERNALIZE", "_ADDR_"});
            statement.execute("ALTER TABLE `" + mceTable + "` MODIFY COLUMN `payload` LONGBLOB EXTERNALIZE");
            statement.executeUpdate("UPDATE `" + mceTable
                + "` SET body='external',payload=X'0200FE',note='external' WHERE id=1");
        }
        assertReplicaQueryEventually(externalDigestQuery(mceTable, "id", "note"), MCE_REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(mceTable, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT EXTERNALIZE", "`PAYLOAD` LONGBLOB EXTERNALIZE"},
            new String[] {"_ADDR_"});

        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + mceTable + "` MODIFY COLUMN `body` LONGTEXT");
            statement.execute("ALTER TABLE `" + mceTable + "` MODIFY COLUMN `payload` LONGBLOB");
            statement.executeUpdate("UPDATE `" + mceTable
                + "` SET body='plain-again',payload=X'0300FD',note='internalized' WHERE id=1");
        }
        assertReplicaQueryEventually(externalDigestQuery(mceTable, "id", "note"), MCE_REPLICA_TIMEOUT_MS);
        assertTargetCreateSql(mceTable, MCE_REPLICA_TIMEOUT_MS,
            new String[] {"`BODY` LONGTEXT", "`PAYLOAD` LONGBLOB"},
            new String[] {"EXTERNALIZE", "_ADDR_"});
    }

    private void runMceWriter(String table, AtomicBoolean running, AtomicLong committedTransactions,
                              AtomicReference<Throwable> trafficFailure) {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + qualified(table)
                    + " (`id`,`body`,`body_len`,`body_md5`,`note`) VALUES (?,?,OCTET_LENGTH(?),MD5(?),?)");
            PreparedStatement updateHot = connection.prepareStatement(
                "UPDATE " + qualified(table)
                    + " SET body=?,body_len=OCTET_LENGTH(?),body_md5=MD5(?),note=? WHERE id=1");
            PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(table) + " WHERE id=?")) {
            connection.setAutoCommit(false);
            long sequence = 0L;
            while (running.get()) {
                sequence++;
                try {
                    long id = 1_000_000L + sequence;
                    String body = "mce-traffic-" + sequence + "-" + repeat('x', 1024);
                    String note = "insert-" + sequence;
                    insert.setLong(1, id);
                    insert.setString(2, body);
                    insert.setString(3, body);
                    insert.setString(4, body);
                    insert.setString(5, note);
                    insert.executeUpdate();

                    String hotBody = "mce-hot-" + sequence + "-" + repeat('h', 512);
                    updateHot.setString(1, hotBody);
                    updateHot.setString(2, hotBody);
                    updateHot.setString(3, hotBody);
                    updateHot.setString(4, "hot-" + sequence);
                    Assert.assertEquals("hot row must remain writable during MCE", 1, updateHot.executeUpdate());

                    if (sequence > 5L && sequence % 5L == 0L) {
                        delete.setLong(1, id - 5L);
                        delete.executeUpdate();
                    }
                    connection.commit();
                    committedTransactions.incrementAndGet();
                } catch (SQLException e) {
                    rollbackBestEffort(connection);
                    if (!isExpectedConcurrentDdlFlashbackError(e)) {
                        throw e;
                    }
                }
                Thread.sleep(20L);
            }
        } catch (Throwable t) {
            if (running.get()) {
                trafficFailure.compareAndSet(null, t);
            }
            running.set(false);
        }
    }

    private void runMceReader(String table, AtomicBoolean running, AtomicLong successfulReadRounds,
                              AtomicReference<Throwable> trafficFailure) {
        try (Connection source = ConnectionManager.getInstance().getDruidPolardbxConnection();
            Connection target = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            while (running.get()) {
                try {
                    assertNoMceTrafficCorruption(source, table);
                    assertNoMceTrafficCorruption(target, table);
                    successfulReadRounds.incrementAndGet();
                } catch (SQLException e) {
                    if (!isExpectedConcurrentDdlFlashbackError(e)) {
                        throw e;
                    }
                }
                Thread.sleep(20L);
            }
        } catch (Throwable t) {
            if (running.get()) {
                trafficFailure.compareAndSet(null, t);
            }
            running.set(false);
        }
    }

    private static void awaitTrafficAdvance(AtomicLong committedTransactions, long start, long minimumAdvance,
                                            AtomicReference<Throwable> trafficFailure, long timeoutMs)
        throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            assertNoTrafficFailure(trafficFailure);
            if (committedTransactions.get() - start >= minimumAdvance) {
                return;
            }
            Thread.sleep(50L);
        }
        Assert.fail("background DML did not commit " + minimumAdvance + " transactions during the MCE window; start="
            + start + ", current=" + committedTransactions.get());
    }

    private static void assertNoTrafficFailure(AtomicReference<Throwable> trafficFailure) {
        Throwable failure = trafficFailure.get();
        if (failure == null) {
            return;
        }
        AssertionError error = new AssertionError("continuous MCE traffic failed: " + failure.getMessage());
        error.initCause(failure);
        throw error;
    }

    private static boolean isExpectedConcurrentDdlFlashbackError(SQLException e) {
        return e.getMessage() != null && e.getMessage().contains(
            "The definition of the table required by the flashback query has changed");
    }

    private static void rollbackBestEffort(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original exception so only the known flashback error can be ignored.
        }
    }

    private static void stopTrafficFuture(Future<?> future) {
        if (future == null) {
            return;
        }
        try {
            future.get(30L, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            future.cancel(true);
        }
    }

    private void insertMceTrafficRow(String table, long id, String body, String note) throws Exception {
        try (Connection connection = getPolardbxConnection(DB_NAME);
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` "
                    + "(`id`,`body`,`body_len`,`body_md5`,`note`) VALUES (?,?,OCTET_LENGTH(?),MD5(?),?)")) {
            statement.setLong(1, id);
            statement.setString(2, body);
            statement.setString(3, body);
            statement.setString(4, body);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private static void assertNoMceTrafficCorruption(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM " + qualified(table)
                    + " WHERE body IS NULL OR body_len<>OCTET_LENGTH(body) "
                    + "OR LOWER(body_md5)<>LOWER(MD5(body))")) {
            Assert.assertTrue(resultSet.next());
            Assert.assertEquals("MCE traffic row body/length/MD5 invariant", 0L, resultSet.getLong(1));
        }
    }

    private static String mceTrafficDigestQuery(String table) {
        return "SELECT `id`,`body_len`,`body_md5`,`note`" + digestExpression("body")
            + " FROM " + qualified(table) + " ORDER BY `id`";
    }

    private static long awaitDdlJob(Connection connection, String table, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long jobId = findDdlJob(connection, table);
            if (jobId > 0L) {
                return jobId;
            }
            Thread.sleep(100L);
        }
        Assert.fail("DDL job did not appear for " + table);
        return -1L;
    }

    private static void awaitDdlState(Connection connection, long jobId, String expectedState,
                                      boolean expectedCancelable, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (hasDdlState(connection, jobId, expectedState, expectedCancelable)) {
                return;
            }
            Thread.sleep(100L);
        }
        Assert.fail("DDL job " + jobId + " did not reach " + expectedState
            + " with cancelable=" + expectedCancelable);
    }

    private static void assertDdlState(Connection connection, long jobId, String expectedState,
                                       boolean expectedCancelable) throws Exception {
        Assert.assertTrue("DDL job " + jobId + " must remain " + expectedState
                + " with cancelable=" + expectedCancelable,
            hasDdlState(connection, jobId, expectedState, expectedCancelable));
    }

    private static boolean hasDdlState(Connection connection, long jobId, String expectedState,
                                       boolean expectedCancelable) throws Exception {
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SHOW DDL")) {
            while (resultSet.next()) {
                if (resultSet.getLong("JOB_ID") == jobId) {
                    return expectedState.equalsIgnoreCase(resultSet.getString("STATE"))
                        && expectedCancelable == Boolean.parseBoolean(resultSet.getString("CANCELABLE"));
                }
            }
        }
        return false;
    }

    private static long findDdlJob(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SHOW DDL")) {
            while (resultSet.next()) {
                if (table.equalsIgnoreCase(resultSet.getString("OBJECT_NAME"))) {
                    return resultSet.getLong("JOB_ID");
                }
            }
        }
        return -1L;
    }

    private static void continueDdl(Connection connection, long jobId) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=true)*/ CONTINUE DDL " + jobId);
        }
    }

    private static void awaitDdlDone(Connection connection, long jobId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean found = false;
            try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW DDL")) {
                while (resultSet.next()) {
                    if (resultSet.getLong("JOB_ID") == jobId) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return;
            }
            Thread.sleep(200L);
        }
        Assert.fail("DDL job " + jobId + " did not finish");
    }

    private void awaitTargetDdlTerminal(String table, long timeoutMs, String[] included, String[] excluded)
        throws Exception {
        assertTargetCreateSql(table, timeoutMs, included, excluded);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (findDdlJob(cdcSyncDbConnection, table) <= 0L) {
                assertCreateSql(cdcSyncDbConnection, "target", table, included, excluded);
                return;
            }
            Thread.sleep(200L);
        }
        Assert.fail("target DDL did not finish for " + table);
    }

    private void finishPausedDdlBestEffort(boolean target, String table) {
        try (Connection connection = target
            ? getCdcSyncDbConnection(DB_NAME)
            : getPolardbxConnection(DB_NAME)) {
            long deadline = System.currentTimeMillis() + 60_000L;
            while (System.currentTimeMillis() < deadline) {
                long jobId = findDdlJob(connection, table);
                if (jobId <= 0L) {
                    return;
                }
                boolean paused = false;
                try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SHOW DDL")) {
                    while (resultSet.next()) {
                        if (resultSet.getLong("JOB_ID") == jobId
                            && "PAUSED".equalsIgnoreCase(resultSet.getString("STATE"))) {
                            paused = true;
                            break;
                        }
                    }
                }
                if (paused) {
                    continueDdl(connection, jobId);
                }
                Thread.sleep(200L);
            }
        } catch (Throwable ignored) {
            // Preserve the original test failure; SHOW DDL and component logs retain cleanup errors.
        }
    }

    private void recreateSourceTable(String table, String definition) throws Exception {
        executeSource("DROP TABLE IF EXISTS `" + table + "`");
        executeSource("CREATE TABLE `" + table + "` (" + definition);
    }

    private void executeSource(String sql) throws Exception {
        try (Connection connection = getPolardbxConnection(DB_NAME);
            Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long resolveTargetOnlyServerId() throws Exception {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        String targetHost = connectionManager.getCdcSyncDbAddress();
        int targetPort = Integer.parseInt(connectionManager.getCdcSyncDbPort());
        Set<Long> commonIgnoreServerIds = null;
        int matchingTasks = 0;

        try (Statement statement = polardbxConnection.createStatement();
            ResultSet resultSet = statement.executeQuery("SHOW SLAVE STATUS")) {
            while (resultSet.next()) {
                String masterHost = resultSet.getString("Master_Host");
                int masterPort = resultSet.getInt("Master_Port");
                if (masterPort != targetPort || !hostsEquivalent(masterHost, targetHost)) {
                    continue;
                }

                Set<Long> ignoreServerIds =
                    parseServerIds(resultSet.getString("Replicate_Ignore_Server_Ids"));
                Assert.assertFalse("backward Replica task from " + targetHost + ":" + targetPort
                    + " has no Replicate_Ignore_Server_Ids", ignoreServerIds.isEmpty());
                if (commonIgnoreServerIds == null) {
                    commonIgnoreServerIds = new HashSet<>(ignoreServerIds);
                } else {
                    commonIgnoreServerIds.retainAll(ignoreServerIds);
                }
                matchingTasks++;
            }
        }

        Assert.assertTrue("no backward Replica task found from target endpoint " + targetHost + ":" + targetPort,
            matchingTasks > 0);
        Assert.assertNotNull(commonIgnoreServerIds);
        Assert.assertFalse("backward Replica tasks from " + targetHost + ":" + targetPort
            + " have no common ignored server id", commonIgnoreServerIds.isEmpty());
        return Collections.min(commonIgnoreServerIds);
    }

    private void executeTargetOnly(long serverId, String sql) throws Exception {
        try (Connection connection = ConnectionManager.getInstance().newCdcSyncDbConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("SET polardbx_server_id = " + serverId);
            statement.execute("USE `" + DB_NAME + "`");
            statement.executeUpdate(sql);
        }
    }

    private Set<Long> parseServerIds(String serverIds) {
        Set<Long> result = new HashSet<>();
        if (serverIds == null || serverIds.trim().isEmpty()) {
            return result;
        }
        for (String serverId : serverIds.split(",")) {
            String value = serverId.trim();
            if (!value.isEmpty()) {
                result.add(Long.parseLong(value));
            }
        }
        return result;
    }

    private boolean hostsEquivalent(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.equalsIgnoreCase(second)) {
            return true;
        }
        try {
            Set<String> firstAddresses = resolveAddresses(first);
            Set<String> secondAddresses = resolveAddresses(second);
            firstAddresses.retainAll(secondAddresses);
            return !firstAddresses.isEmpty();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private Set<String> resolveAddresses(String host) throws UnknownHostException {
        Set<String> result = new HashSet<>();
        for (InetAddress address : InetAddress.getAllByName(host)) {
            result.add(address.getHostAddress());
        }
        return result;
    }

    private void assertReplicaQueryEventually(String sql, long timeoutMs) throws Exception {
        List<List<String>> expected = queryRows(polardbxConnection, sql);
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<List<String>> actual = null;
        Throwable lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                actual = queryRows(cdcSyncDbConnection, sql);
                if (expected.equals(actual)) {
                    return;
                }
                lastError = null;
            } catch (Throwable t) {
                lastError = t;
            }
            Thread.sleep(500L);
        }
        AssertionError error = new AssertionError("replica result did not converge for SQL " + sql
            + ", expected=" + expected + ", actual=" + actual);
        if (lastError != null) {
            error.initCause(lastError);
        }
        throw error;
    }

    private String awaitTargetScalar(String sql, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Throwable lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try (Statement statement = cdcSyncDbConnection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
                lastError = null;
            } catch (Throwable t) {
                lastError = t;
            }
            Thread.sleep(500L);
        }
        AssertionError error = new AssertionError("replica query returned no row before timeout: " + sql);
        if (lastError != null) {
            error.initCause(lastError);
        }
        throw error;
    }

    private void assertTargetCreateSql(String table, long timeoutMs, String[] included, String[] excluded)
        throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastCreateSql = null;
        Throwable lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try (Statement statement = cdcSyncDbConnection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + qualified(table))) {
                if (resultSet.next()) {
                    lastCreateSql = resultSet.getString(2).toUpperCase();
                    if (containsAll(lastCreateSql, included) && containsNone(lastCreateSql, excluded)) {
                        return;
                    }
                }
                lastError = null;
            } catch (Throwable t) {
                lastError = t;
            }
            Thread.sleep(500L);
        }
        AssertionError error = new AssertionError("target SHOW CREATE TABLE did not converge for " + table
            + ", required=" + Arrays.toString(included) + ", forbidden=" + Arrays.toString(excluded)
            + ", actual=" + lastCreateSql);
        if (lastError != null) {
            error.initCause(lastError);
        }
        throw error;
    }

    private static void assertCreateSql(Connection connection, String endpoint, String table,
                                        String[] included, String[] excluded) throws Exception {
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + qualified(table))) {
            Assert.assertTrue(endpoint + " SHOW CREATE TABLE returned no row for " + table, resultSet.next());
            String createSql = resultSet.getString(2).toUpperCase();
            Assert.assertTrue(endpoint + " schema is missing " + Arrays.toString(included) + ": " + createSql,
                containsAll(createSql, included));
            Assert.assertTrue(endpoint + " schema contains " + Arrays.toString(excluded) + ": " + createSql,
                containsNone(createSql, excluded));
        }
    }

    private static List<List<String>> queryRows(Connection connection, String sql) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            while (resultSet.next()) {
                List<String> row = new ArrayList<>(metadata.getColumnCount());
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String value = resultSet.getString(i);
                    row.add(value == null ? "<NULL>" : value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static String externalDigestQuery(String table, String... ordinaryColumns) {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < ordinaryColumns.length; i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('`').append(ordinaryColumns[i]).append('`');
        }
        sql.append(digestExpression("body"));
        sql.append(digestExpression("payload"));
        sql.append(" FROM ").append(qualified(table)).append(" ORDER BY `id`");
        return sql.toString();
    }

    private static String digestExpression(String column) {
        return ",IF(`" + column + "` IS NULL,'<NULL>',CONCAT(OCTET_LENGTH(`" + column
            + "`),':',MD5(`" + column + "`))) AS `" + column + "_digest`";
    }

    private static String qualified(String table) {
        return qualified(DB_NAME, table);
    }

    private static String qualified(String database, String table) {
        return "`" + database + "`.`" + table + "`";
    }

    private static void addExternalBatch(PreparedStatement statement, long id, String body, byte[] payload,
                                         String note) throws Exception {
        statement.setLong(1, id);
        statement.setString(2, body);
        statement.setBytes(3, payload);
        statement.setString(4, note);
        statement.addBatch();
    }

    private static String insertSql(String table, long id, long sk, String body, byte[] payload, String note) {
        return "INSERT INTO `" + table + "` VALUES (" + id + "," + sk + "," + quote(body) + ","
            + hex(payload) + "," + quote(note) + ")";
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

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] patternedBytes(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (i * 31 + 17);
        }
        return result;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static String endpoint(String addressKey, String portKey) {
        return PropertiesUtil.configProp.getProperty(addressKey, "") + ":"
            + PropertiesUtil.configProp.getProperty(portKey, "");
    }

    private int sourceIntConfigOrDefault(String key, int defaultValue) throws Exception {
        try (Connection connection = getMetaConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT param_val FROM inst_config WHERE param_key=? ORDER BY gmt_modified DESC LIMIT 1")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Integer.parseInt(resultSet.getString(1)) : defaultValue;
            }
        }
    }

    private static boolean containsAll(String value, String[] tokens) {
        for (String token : tokens) {
            if (!value.contains(token.toUpperCase())) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNone(String value, String[] tokens) {
        for (String token : tokens) {
            if (value.contains(token.toUpperCase())) {
                return false;
            }
        }
        return true;
    }

    private static long[] findDifferentRouteValues(Connection connection, String table, String partitionColumn)
        throws Exception {
        String firstGroup = null;
        long firstValue = 0L;
        try (Statement statement = connection.createStatement()) {
            for (long candidate = 1L; candidate <= 256L; candidate++) {
                boolean hasResult = statement.execute("TRACE SELECT id FROM `" + table + "` WHERE `"
                    + partitionColumn + "`=" + candidate);
                if (hasResult) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        while (ignored.next()) {
                            // Drain the traced query before SHOW TRACE reuses this statement.
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
                if (firstGroup == null) {
                    firstGroup = group;
                    firstValue = candidate;
                } else if (!firstGroup.equals(group)) {
                    return new long[] {firstValue, candidate};
                }
            }
        }
        throw new AssertionError("cannot find values routed to different DNs for " + table + "." + partitionColumn);
    }

    private enum TransactionPolicy {
        TSO("TSO", true, "TSO"),
        XA("XA", false, "XA"),
        XA_TSO("XA", true, "XA_TSO");

        private final String configuredName;
        private final boolean enableXaTso;
        private final String expectedName;

        TransactionPolicy(String configuredName, boolean enableXaTso, String expectedName) {
            this.configuredName = configuredName;
            this.enableXaTso = enableXaTso;
            this.expectedName = expectedName;
        }

        private void apply(Statement statement) throws Exception {
            statement.execute("SET enable_xa_tso = " + enableXaTso);
            statement.execute("SET transaction_policy = " + configuredName);
            try (ResultSet resultSet = statement.executeQuery("SHOW VARIABLES LIKE 'transaction_policy'")) {
                Assert.assertTrue("transaction_policy is not visible after setting " + expectedName,
                    resultSet.next());
                Assert.assertEquals("effective transaction policy", expectedName,
                    resultSet.getString("Value").toUpperCase());
            }
        }
    }
}
