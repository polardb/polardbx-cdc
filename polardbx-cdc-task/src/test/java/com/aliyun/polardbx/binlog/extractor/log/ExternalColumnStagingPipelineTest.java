/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.DecodeMode;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.RowEventReformator;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.TableMapEventReformator;
import com.aliyun.polardbx.binlog.format.BinlogBuilder;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.binlog.storage.Repository;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.google.protobuf.ByteString;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExternalColumnStagingPipelineTest {

    private static final long SLOT_ADDR = 0x8000000000000403L;
    private static final long TABLE_ID = 9876L;

    private MockedStatic<DynamicApplicationConfig> mockedConfig;
    private MockedStatic<SpringContextHolder> mockedSpringContext;
    private Storage storage;
    private RuntimeContext runtimeContext;
    private EventFixture events;
    private boolean blobRefErrorFallbackEnabled;
    private boolean partialUpdateRowImageEnabled;

    @Before
    public void before() {
        blobRefErrorFallbackEnabled = false;
        partialUpdateRowImageEnabled = true;
        mockedConfig = Mockito.mockStatic(DynamicApplicationConfig.class, Mockito.CALLS_REAL_METHODS);
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MEMORY_LIMIT_BYTES)).thenReturn("1024");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_TXN_BYTES)).thenReturn("4096");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_ENTRIES)).thenReturn("16");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
                ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED))
            .thenAnswer(i -> Boolean.toString(blobRefErrorFallbackEnabled));
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
                ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_PARTIAL_UPDATE_ROW_IMAGE_ENABLED))
            .thenAnswer(i -> Boolean.toString(partialUpdateRowImageEnabled));
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(ConfigKeys.IS_LAB_ENV)).thenReturn("false");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_EXTRACT_WATCH_MEMORY_LEAK_ENABLED)).thenReturn("false");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG)).thenReturn("false");
        mockedConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.TASK_REFORMAT_NO_FOREIGN_KEY_CHECK)).thenReturn("false");
        mockedSpringContext = Mockito.mockStatic(SpringContextHolder.class, Mockito.CALLS_REAL_METHODS);
        ISystemDBProvider systemDBProvider = mock(ISystemDBProvider.class);
        mockedSpringContext.when(() -> SpringContextHolder.getObject(ISystemDBProvider.class))
            .thenReturn(systemDBProvider);

        storage = mock(Storage.class);
        Repository repository = mock(Repository.class);
        when(storage.getRepository()).thenReturn(repository);
        when(repository.selectUnit(anyLong())).thenReturn(mock(RepoUnit.class));
        runtimeContext = mock(RuntimeContext.class);
        when(runtimeContext.getStorageInstId()).thenReturn("dn-test");
        when(runtimeContext.getStorageHashCode()).thenReturn("1");
        when(runtimeContext.getBinlogFile()).thenReturn("binlog.000001");
        events = new EventFixture();
    }

    @After
    public void after() {
        mockedSpringContext.close();
        mockedConfig.close();
    }

    @Test
    public void testRealBinaryStagingEventsFlowThroughTransaction() throws Exception {
        byte[] raw = "staging payload".getBytes("UTF-8");
        List<Field> fields = stagingFields(SLOT_ADDR, TABLE_ID, raw, true);
        TableMapLogEvent tableMap = events.tableMap("__polarx_ext_staging", "polarx_ext_staging_12", fields);
        RowsLogEvent rows = events.rows(BinlogEventType.WRITE_ROWS_EVENT, tableMap, fields, allColumns());

        Assert.assertTrue(ExternalColumnStagingEvent.isStagingEvent(tableMap));
        Assert.assertTrue(ExternalColumnStagingEvent.isStagingEvent(rows));
        Assert.assertEquals(12, ExternalColumnStagingEvent.validateAndGetSeqId(tableMap));
        List<Object> consumed = new ArrayList<>();
        ExternalColumnStagingEvent.consumeWriteRows(rows, (seqId, slotAddr, tableId, value) -> {
            consumed.add(seqId);
            consumed.add(slotAddr);
            consumed.add(tableId);
            consumed.add(value);
        });
        Assert.assertEquals(Arrays.asList(12, SLOT_ADDR, TABLE_ID), consumed.subList(0, 3));
        Assert.assertArrayEquals(raw, (byte[]) consumed.get(3));

        Transaction transaction = newTransaction();
        transaction.processEvent(tableMap, runtimeContext);
        transaction.processEvent(rows, runtimeContext);
        Assert.assertEquals(0, transaction.getEventCount());
        Assert.assertNotNull(transaction.getExternalColumnTxnContext());
        Assert.assertEquals(1, transaction.getExternalColumnTxnContext().size());
        ExternalColumnBlobRef blobRef = ExternalColumnBlobRef.decode(blobRef(12, SLOT_ADDR, raw));
        Assert.assertArrayEquals(raw, transaction.getExternalColumnTxnContext().resolve(blobRef));

        transaction.releaseExternalColumnTxnContext();
        transaction.releaseExternalColumnTxnContext();
        Assert.assertNull(transaction.getExternalColumnTxnContext());
        transaction.release();
    }

    @Test
    public void testRestartRecoveryRequiresFullTransactionReplay() throws Exception {
        int seqId = 14;
        byte[] raw = "replayed staging payload".getBytes("UTF-8");
        String blobRef = blobRef(seqId, SLOT_ADDR, raw);
        List<Field> fields = stagingFields(SLOT_ADDR, TABLE_ID, raw, true);
        TableMapLogEvent tableMap =
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_" + seqId, fields);
        RowsLogEvent rows = events.rows(BinlogEventType.WRITE_ROWS_EVENT, tableMap, fields, allColumns());

        // A process restart discards the branch-local context. Starting after the staging event therefore cannot
        // restore the raw value. The default policy fails closed instead of publishing the physical address.
        Transaction interrupted = newTransaction();
        interrupted.processEvent(tableMap, runtimeContext);
        interrupted.processEvent(rows, runtimeContext);
        ExternalColumnTxnContext interruptedContext = interrupted.getExternalColumnTxnContext();
        Assert.assertNotNull(interruptedContext);
        interrupted.release();
        Assert.assertNull(interrupted.getExternalColumnTxnContext());

        assertFailureInCause("external-column raw is unavailable",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
                externalLogicMeta(0, null), null));

        // Malformed staging metadata also fails closed before a business row can consume incomplete staging state.
        List<Field> malformedFields = new ArrayList<>(fields);
        malformedFields.set(0, MakeFieldFactory.makeField("varchar(128)", "1", "utf8", true, false));
        Transaction rejectedReplay = newTransaction();
        assertFailure("TABLE_MAP schema", () -> rejectedReplay.processEvent(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_" + seqId, malformedFields), runtimeContext));
        rejectedReplay.release();

        // The existing emergency switch deliberately restores the old availability-first behavior. Missing raw is
        // emitted as its canonical address, and one malformed staging event degrades the whole physical branch.
        blobRefErrorFallbackEnabled = true;
        ReformatResult missingReplay = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), null);
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), missingReplay.beforeValue);

        Transaction degradedReplay = newTransaction();
        degradedReplay.processEvent(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_" + seqId, malformedFields), runtimeContext);
        degradedReplay.processEvent(tableMap, runtimeContext);
        degradedReplay.processEvent(rows, runtimeContext);
        Assert.assertNull(degradedReplay.getExternalColumnTxnContext());
        ReformatResult degradedResult = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), degradedReplay.getExternalColumnTxnContext());
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), degradedResult.beforeValue);
        degradedReplay.release();

        // Normal recovery replays the complete source transaction. A fresh context is rebuilt from staging before
        // the business row is reformatted, so the logical row contains the original bytes rather than the BlobRef.
        Transaction replayed = newTransaction();
        replayed.processEvent(tableMap, runtimeContext);
        replayed.processEvent(rows, runtimeContext);
        ExternalColumnTxnContext replayedContext = replayed.getExternalColumnTxnContext();
        Assert.assertNotNull(replayedContext);
        Assert.assertNotSame(interruptedContext, replayedContext);
        ReformatResult restored = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), replayedContext);
        Assert.assertArrayEquals(raw, restored.beforeValue);
        replayed.release();
    }

    @Test
    public void testRollbackReleasesStagingContext() throws Exception {
        byte[] raw = "rollback payload".getBytes("UTF-8");
        List<Field> fields = stagingFields(SLOT_ADDR, TABLE_ID, raw, true);
        TableMapLogEvent tableMap = events.tableMap("__polarx_ext_staging", "polarx_ext_staging_13", fields);
        Transaction transaction = newTransaction();
        transaction.processEvent(tableMap, runtimeContext);
        transaction.processEvent(events.rows(BinlogEventType.WRITE_ROWS_EVENT, tableMap, fields, allColumns()),
            runtimeContext);
        Assert.assertNotNull(transaction.getExternalColumnTxnContext());
        java.lang.reflect.Field entityField = Transaction.class.getDeclaredField("entity");
        entityField.setAccessible(true);
        ((TransEntity) entityField.get(transaction)).ignore = true;

        transaction.setRollback(runtimeContext);

        Assert.assertNull(transaction.getExternalColumnTxnContext());
        transaction.release();
    }

    @Test
    public void testDeleteIsSkippedAndUpdateUsesConfiguredFallback() throws Exception {
        byte[] raw = new byte[] {1, 2, 3};
        List<Field> fields = stagingFields(SLOT_ADDR, TABLE_ID, raw, true);
        TableMapLogEvent tableMap = events.tableMap("__polarx_ext_staging", "POLARX_EXT_STAGING_3", fields);

        Transaction deleteTransaction = newTransaction();
        deleteTransaction.processEvent(tableMap, runtimeContext);
        deleteTransaction.processEvent(
            events.rows(BinlogEventType.DELETE_ROWS_EVENT, tableMap, fields, allColumns()), runtimeContext);
        Assert.assertNull(deleteTransaction.getExternalColumnTxnContext());
        deleteTransaction.release();

        Transaction updateTransaction = newTransaction();
        updateTransaction.processEvent(tableMap, runtimeContext);
        assertFailure("unsupported external-column staging row event type",
            () -> updateTransaction.processEvent(
                events.rows(BinlogEventType.UPDATE_ROWS_EVENT, tableMap, fields, allColumns()), runtimeContext));
        Assert.assertNull(updateTransaction.getExternalColumnTxnContext());
        updateTransaction.release();

        blobRefErrorFallbackEnabled = true;
        Transaction degradedUpdateTransaction = newTransaction();
        degradedUpdateTransaction.processEvent(tableMap, runtimeContext);
        degradedUpdateTransaction.processEvent(
            events.rows(BinlogEventType.UPDATE_ROWS_EVENT, tableMap, fields, allColumns()), runtimeContext);
        degradedUpdateTransaction.processEvent(tableMap, runtimeContext);
        degradedUpdateTransaction.processEvent(
            events.rows(BinlogEventType.WRITE_ROWS_EVENT, tableMap, fields, allColumns()), runtimeContext);
        Assert.assertNull(degradedUpdateTransaction.getExternalColumnTxnContext());
        degradedUpdateTransaction.release();
    }

    @Test
    public void testMalformedTableMapAndMissingColumnsFailClosed() throws Exception {
        byte[] raw = new byte[] {4, 5};
        List<Field> fields = stagingFields(SLOT_ADDR, TABLE_ID, raw, true);
        TableMapLogEvent valid = events.tableMap("__polarx_ext_staging", "polarx_ext_staging_1", fields);

        BitMap missingRaw = allColumns();
        missingRaw.set(2, false);
        RowsLogEvent missingColumnRows = events.rows(BinlogEventType.WRITE_ROWS_EVENT, valid, fields, missingRaw);
        assertFailure("misses a required column",
            () -> ExternalColumnStagingEvent.consumeWriteRows(missingColumnRows, (a, b, c, d) -> {
            }));
        RowsLogEvent deleteRows = events.rows(BinlogEventType.DELETE_ROWS_EVENT, valid, fields, allColumns());
        assertFailure("only WRITE_ROWS",
            () -> ExternalColumnStagingEvent.consumeWriteRows(deleteRows, (a, b, c, d) -> {
            }));

        RowsLogEvent nullRawRows = events.rowsWithNull(
            BinlogEventType.WRITE_ROWS_EVENT, valid, fields, allColumns(), 2);
        assertFailure("value types",
            () -> ExternalColumnStagingEvent.consumeWriteRows(nullRawRows, (a, b, c, d) -> {
            }));

        List<Field> wrongCount = new ArrayList<>(fields.subList(0, 3));
        assertFailure("column count", () -> ExternalColumnStagingEvent.validateAndGetSeqId(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_1", wrongCount)));

        List<Field> wrongType = new ArrayList<>(fields);
        wrongType.set(0, MakeFieldFactory.makeField("varchar(128)", "1", "utf8", true, false));
        assertFailure("TABLE_MAP schema", () -> ExternalColumnStagingEvent.validateAndGetSeqId(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_1", wrongType)));

        List<Field> nullableMismatch = stagingFields(SLOT_ADDR, TABLE_ID, raw, false);
        assertFailure("TABLE_MAP schema", () -> ExternalColumnStagingEvent.validateAndGetSeqId(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_1", nullableMismatch)));

        assertFailure("not from", () -> ExternalColumnStagingEvent.validateAndGetSeqId(
            events.tableMap("business_db", "t1", fields)));
        assertFailure("seqId", () -> ExternalColumnStagingEvent.validateAndGetSeqId(
            events.tableMap("__polarx_ext_staging", "polarx_ext_staging_2147483648", fields)));

        TableMapLogEvent malformed = events.tableMap("__polarx_ext_staging", "polarx_ext_staging_1", wrongType);
        Transaction rejectedTransaction = newTransaction();
        assertFailure("TABLE_MAP schema", () -> rejectedTransaction.processEvent(malformed, runtimeContext));
        rejectedTransaction.release();

        blobRefErrorFallbackEnabled = true;
        Transaction degradedTransaction = newTransaction();
        degradedTransaction.processEvent(malformed, runtimeContext);
        degradedTransaction.processEvent(valid, runtimeContext);
        degradedTransaction.processEvent(events.rows(BinlogEventType.WRITE_ROWS_EVENT, valid, fields, allColumns()),
            runtimeContext);
        Assert.assertNull(degradedTransaction.getExternalColumnTxnContext());
        degradedTransaction.release();
    }

    @Test
    public void testWriteRowsRestoresRawThroughRowReformatPipeline() throws Exception {
        byte[] raw = "restored external payload".getBytes("UTF-8");
        String blobRef = blobRef(8, SLOT_ADDR, raw);
        ExternalColumnTxnContext txnContext = stagingContext(8, SLOT_ADDR, raw);
        ReformatResult result = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), txnContext);

        Assert.assertEquals("logic_db", result.eventData.getSchemaName());
        Assert.assertEquals("logic_table", result.eventData.getTableName());
        Assert.assertArrayEquals(raw, result.beforeValue);
        Assert.assertNull(result.afterValue);
        txnContext.release();
    }

    @Test
    public void testWriteRowsFallbackAndFailCloseBoundaries() throws Exception {
        byte[] raw = "not staged".getBytes("UTF-8");
        String blobRef = blobRef(9, SLOT_ADDR, raw);
        String unsupportedBlobRef = "03" + blobRef.substring(2);

        assertFailureInCause("external-column raw is unavailable",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
                externalLogicMeta(0, null), null));

        assertFailureInCause("invalid non-canonical external-column BlobRef hex character",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, "not-a-blob-ref", "not-a-blob-ref",
                externalLogicMeta(0, null), null));
        assertFailureInCause("unsupported external-column BlobRef version 3",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, unsupportedBlobRef, unsupportedBlobRef,
                externalLogicMeta(0, null), null));

        assertFailureInCause("missing physical BlobRef",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
                externalLogicMeta(0, "missing physical BlobRef"), null));
        assertFailureInCause("missing physical BlobRef",
            () -> reformat(BinlogEventType.UPDATE_ROWS_EVENT, blobRef, blobRef,
                externalLogicMeta(-1, "missing physical BlobRef"), null));
        assertFailureInCause("invalid physical BlobRef value type",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, "1", "1",
                externalLogicMeta(0, null), null, "bigint"));

        ExternalColumnTxnContext missingStaging = new ExternalColumnTxnContext(storage);
        assertFailureInCause("external-column raw is unavailable",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
                externalLogicMeta(0, null), missingStaging));
        missingStaging.release();

        blobRefErrorFallbackEnabled = true;
        ReformatResult addressFallback = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), null);
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), addressFallback.beforeValue);

        ExternalColumnTxnContext fallbackMissingStaging = new ExternalColumnTxnContext(storage);
        ReformatResult resolveFailure = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, null), fallbackMissingStaging);
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), resolveFailure.beforeValue);
        fallbackMissingStaging.release();

        ReformatResult mappingFallback = reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, "invalid physical BlobRef"), null, "varchar(127)");
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), mappingFallback.beforeValue);

        ReformatResult fieldShapeFallback = reformat(BinlogEventType.WRITE_ROWS_EVENT, "1", "1",
            externalLogicMeta(0, "invalid physical BlobRef"), null, "bigint");
        Assert.assertNull(fieldShapeFallback.beforeValue);

        ReformatResult missingMapping = reformat(BinlogEventType.UPDATE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(-1, "missing physical BlobRef"), null);
        Assert.assertNull(missingMapping.beforeValue);
        Assert.assertNull(missingMapping.afterValue);

        ReformatResult updateMappingFailure = reformat(BinlogEventType.UPDATE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(0, "invalid physical BlobRef"), null);
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), updateMappingFailure.beforeValue);
        Assert.assertArrayEquals(blobRef.getBytes("US-ASCII"), updateMappingFailure.afterValue);

        assertFailureInCause("invalid non-canonical external-column BlobRef hex character",
            () -> reformat(BinlogEventType.UPDATE_ROWS_EVENT, blobRef, "not-a-blob-ref",
                externalLogicMeta(0, null), null));
        assertFailureInCause("invalid non-canonical external-column BlobRef hex character",
            () -> reformat(BinlogEventType.UPDATE_ROWS_EVENT, "not-a-blob-ref", blobRef,
                externalLogicMeta(0, null), null));

        ReformatResult unsupportedWrite = reformat(BinlogEventType.WRITE_ROWS_EVENT, unsupportedBlobRef,
            unsupportedBlobRef, externalLogicMeta(0, null), null);
        Assert.assertArrayEquals(unsupportedBlobRef.getBytes("US-ASCII"), unsupportedWrite.beforeValue);

        ReformatResult unsupportedUpdate = reformat(BinlogEventType.UPDATE_ROWS_EVENT, unsupportedBlobRef,
            unsupportedBlobRef, externalLogicMeta(0, null), null);
        Assert.assertArrayEquals(unsupportedBlobRef.getBytes("US-ASCII"), unsupportedUpdate.beforeValue);
        Assert.assertArrayEquals(unsupportedBlobRef.getBytes("US-ASCII"), unsupportedUpdate.afterValue);

        assertFailureInCause("invalid non-canonical external-column BlobRef hex character",
            () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, "not-a-blob-ref", "not-a-blob-ref",
                externalLogicMeta(0, null), null));

        assertFailure("reformat log pos", () -> reformat(BinlogEventType.WRITE_ROWS_EVENT, blobRef, blobRef,
            externalLogicMeta(1, null), null));

        ReformatContext context = new ReformatContext("utf8", "utf8", 0, "dn-test");
        Assert.assertTrue(context.markExternalColumnAddressFallbackLogged());
        Assert.assertFalse(context.markExternalColumnAddressFallbackLogged());
        Assert.assertTrue(context.markExternalColumnNullFallbackLogged());
        Assert.assertFalse(context.markExternalColumnNullFallbackLogged());
        context.setExternalColumnTxnContext(null);
        Assert.assertTrue(context.markExternalColumnAddressFallbackLogged());
        Assert.assertTrue(context.markExternalColumnNullFallbackLogged());
    }

    @Test
    public void testDeleteKeepsAddressAndUpdateRestoresOnlyChangedAfterImage() throws Exception {
        byte[] beforeRaw = "before".getBytes("UTF-8");
        byte[] afterRaw = "after".getBytes("UTF-8");
        String beforeBlobRef = blobRef(10, SLOT_ADDR, beforeRaw);
        String afterBlobRef = blobRef(10, SLOT_ADDR + 1, afterRaw);
        ExternalColumnTxnContext txnContext = stagingContext(10, SLOT_ADDR, beforeRaw);
        txnContext.put(10, SLOT_ADDR + 1, TABLE_ID, afterRaw);

        ReformatResult delete = reformat(BinlogEventType.DELETE_ROWS_EVENT, beforeBlobRef, beforeBlobRef,
            externalLogicMeta(0, null), txnContext);
        Assert.assertArrayEquals(beforeBlobRef.getBytes("US-ASCII"), delete.beforeValue);

        ReformatResult sameAddress = reformat(BinlogEventType.UPDATE_ROWS_EVENT, beforeBlobRef, beforeBlobRef,
            externalLogicMeta(0, null), txnContext);
        Assert.assertArrayEquals(beforeBlobRef.getBytes("US-ASCII"), sameAddress.beforeValue);
        Assert.assertFalse(sameAddress.afterColumnPresent);
        Assert.assertNull(sameAddress.afterValue);

        partialUpdateRowImageEnabled = false;
        ReformatResult legacySameAddress = reformat(BinlogEventType.UPDATE_ROWS_EVENT, beforeBlobRef, beforeBlobRef,
            externalLogicMeta(0, null), txnContext);
        Assert.assertTrue(legacySameAddress.afterColumnPresent);
        Assert.assertArrayEquals(beforeBlobRef.getBytes("US-ASCII"), legacySameAddress.afterValue);

        ReformatResult legacyChangedAddress = reformat(BinlogEventType.UPDATE_ROWS_EVENT, beforeBlobRef, afterBlobRef,
            externalLogicMeta(0, null), txnContext);
        Assert.assertTrue(legacyChangedAddress.afterColumnPresent);
        Assert.assertArrayEquals(afterRaw, legacyChangedAddress.afterValue);
        partialUpdateRowImageEnabled = true;

        ReformatResult changedAddress = reformat(BinlogEventType.UPDATE_ROWS_EVENT, beforeBlobRef, afterBlobRef,
            externalLogicMeta(0, null), txnContext);
        Assert.assertArrayEquals(beforeBlobRef.getBytes("US-ASCII"), changedAddress.beforeValue);
        Assert.assertTrue(changedAddress.afterColumnPresent);
        Assert.assertArrayEquals(afterRaw, changedAddress.afterValue);
        txnContext.release();
    }

    @Test
    public void testTableMapUsesNullablePlaceholderForHistoricalNotNullExternalColumn() throws Exception {
        List<Field> physicalFields = Arrays.asList(
            MakeFieldFactory.makeField("varchar(128)", blobRef(30, SLOT_ADDR, new byte[] {1}),
                "utf8", true, false));
        TableMapLogEvent physicalTable = events.tableMap("phy_db", "phy_table", physicalFields);

        PolarDbXTableMetaManager tableMetaManager = mock(PolarDbXTableMetaManager.class);
        when(tableMetaManager.compare("phy_db", "phy_table", 1))
            .thenReturn(externalLogicMeta(0, null, false));
        when(tableMetaManager.getTableId("logic_db", "logic_table")).thenReturn(100L);

        TableMapEventReformator reformator = new TableMapEventReformator(tableMetaManager);
        ReformatContext context = new ReformatContext("utf8", "utf8", 0, "dn-test");
        context.setServerId(1000L);
        TxnItemRef txnItemRef = mock(TxnItemRef.class);
        EventData original = EventData.newBuilder()
            .setSchemaName("phy_db")
            .setTableName("phy_table")
            .build();

        Logger createFieldLogger = (Logger) LoggerFactory.getLogger(CreateField.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        createFieldLogger.addAppender(appender);
        try {
            Assert.assertTrue(reformator.reformat(physicalTable, txnItemRef, context, original));
        } finally {
            createFieldLogger.detachAppender(appender);
            appender.stop();
        }
        Assert.assertFalse(appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.startsWith("check nullable flag false")));

        ArgumentCaptor<EventData> eventCaptor = ArgumentCaptor.forClass(EventData.class);
        verify(txnItemRef).setEventData(eventCaptor.capture());
        TableMapLogEvent rebuilt = events.decodeTableMap(eventCaptor.getValue().getPayload().toByteArray());
        Assert.assertFalse(rebuilt.getNullBits().get(0));
    }

    @Test
    public void testPartialUpdateRowImageSwitchControlsMultiRowSplit() throws Exception {
        byte[] beforeRaw = "before-batch".getBytes("UTF-8");
        byte[] changedRaw = "changed-batch".getBytes("UTF-8");
        String beforeBlobRef = blobRef(20, SLOT_ADDR, beforeRaw);
        String changedBlobRef = blobRef(20, SLOT_ADDR + 1, changedRaw);
        List<Field> beforeFields = Arrays.asList(
            MakeFieldFactory.makeField("varchar(128)", beforeBlobRef, "utf8", true, false));
        List<Field> unchangedAfterFields = Arrays.asList(
            MakeFieldFactory.makeField("varchar(128)", beforeBlobRef, "utf8", true, false));
        List<Field> changedAfterFields = Arrays.asList(
            MakeFieldFactory.makeField("varchar(128)", changedBlobRef, "utf8", true, false));
        TableMapLogEvent physicalTable = events.tableMap("phy_db", "phy_table", beforeFields);
        ExternalColumnTxnContext txnContext = stagingContext(20, SLOT_ADDR, beforeRaw);
        txnContext.put(20, SLOT_ADDR + 1, TABLE_ID, changedRaw);

        PolarDbXTableMetaManager tableMetaManager = mock(PolarDbXTableMetaManager.class);
        when(tableMetaManager.compare("phy_db", "phy_table", 1)).thenReturn(externalLogicMeta(0, null));
        when(tableMetaManager.getTableId("logic_db", "logic_table")).thenReturn(100L);
        RowEventReformator reformator = new RowEventReformator(false, tableMetaManager);

        RowsLogEvent enabledRows = events.rowsBatch(BinlogEventType.UPDATE_ROWS_EVENT, physicalTable,
            Arrays.asList(beforeFields, beforeFields),
            Arrays.asList(unchangedAfterFields, changedAfterFields), columnMap(1));
        IteratorBuffer enabledIterator = mock(IteratorBuffer.class);
        TxnItemRef enabledItem = mock(TxnItemRef.class);
        ReformatContext enabledContext = reformatContext(txnContext, enabledIterator);
        Assert.assertTrue(reformator.reformat(enabledRows, enabledItem, enabledContext, eventData(enabledRows)));
        verify(enabledIterator).remove();
        ArgumentCaptor<TxnBufferItem> splitItems = ArgumentCaptor.forClass(TxnBufferItem.class);
        verify(enabledIterator, Mockito.times(2)).appendAfter(splitItems.capture());
        Assert.assertEquals(2, splitItems.getAllValues().size());
        Assert.assertNull(splitItems.getAllValues().get(0).getPrimaryKey());
        Assert.assertNull(splitItems.getAllValues().get(1).getPrimaryKey());

        partialUpdateRowImageEnabled = false;
        RowsLogEvent disabledRows = events.rowsBatch(BinlogEventType.UPDATE_ROWS_EVENT, physicalTable,
            Arrays.asList(beforeFields, beforeFields),
            Arrays.asList(unchangedAfterFields, changedAfterFields), columnMap(1));
        IteratorBuffer disabledIterator = mock(IteratorBuffer.class);
        TxnItemRef disabledItem = mock(TxnItemRef.class);
        ReformatContext disabledContext = reformatContext(txnContext, disabledIterator);
        Assert.assertTrue(reformator.reformat(disabledRows, disabledItem, disabledContext, eventData(disabledRows)));
        verify(disabledIterator, Mockito.never()).remove();
        verify(disabledIterator, Mockito.never()).appendAfter(Mockito.any(TxnBufferItem.class));
        verify(disabledItem).setEventData(Mockito.any(EventData.class));
        txnContext.release();
    }

    @Test
    public void testBlobRefDecodeFailsClosedForCorruptMetadataAndFieldShapes() throws Exception {
        byte[] raw = "blob-ref".getBytes("UTF-8");
        String blobRef = blobRef(15, SLOT_ADDR, raw);
        List<Field> physicalFields = Arrays.asList(
            MakeFieldFactory.makeField("varchar(128)", blobRef, "utf8", true, false));
        TableMapLogEvent table = events.tableMap("phy_db", "phy_table", physicalFields);
        LogicTableMeta.FieldMetaExt fieldMeta = externalLogicMeta(0, null).getLogicFields().get(0);
        RowEventReformator reformator = new RowEventReformator(false, mock(PolarDbXTableMetaManager.class));
        ReformatContext context = new ReformatContext("utf8", "utf8", 0, "dn-test");

        java.lang.reflect.Field columnInfoField = TableMapLogEvent.class.getDeclaredField("columnInfo");
        columnInfoField.setAccessible(true);
        TableMapLogEvent.ColumnInfo[] originalColumnInfos = table.getColumnInfo();
        columnInfoField.set(table, null);
        assertDecodeFailure("invalid TABLE_MAP column mapping",
            () -> invokeDecodeBlobRef(reformator, physicalFields.get(0), table, fieldMeta, context));
        columnInfoField.set(table, originalColumnInfos);

        TableMapLogEvent.ColumnInfo originalColumnInfo = originalColumnInfos[0];
        originalColumnInfos[0] = null;
        assertDecodeFailure("missing TABLE_MAP column info",
            () -> invokeDecodeBlobRef(reformator, physicalFields.get(0), table, fieldMeta, context));
        originalColumnInfos[0] = originalColumnInfo;

        assertDecodeFailure("invalid physical BlobRef field implementation",
            () -> invokeDecodeBlobRef(reformator, mock(Field.class), table, fieldMeta, context));
    }

    @Test
    public void testStagingTableRecognitionContract() {
        Assert.assertTrue(ExternalColumnStagingEvent.isStagingTable(
            "__POLARX_EXT_STAGING", "POLARX_EXT_STAGING_99"));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingTable(null, "polarx_ext_staging_1"));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingTable("__polarx_ext_staging", null));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingTable("business", "polarx_ext_staging_1"));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingTable("__polarx_ext_staging", "polarx_ext_staging_0"));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingTable("__polarx_ext_staging", "staging_1"));
        Assert.assertFalse(ExternalColumnStagingEvent.isStagingEvent(mock(LogEvent.class)));
    }

    @Test
    public void testTransactionLeavesNonStagingEventForNormalProcessing() throws Exception {
        Transaction transaction = newTransaction();
        Method method = Transaction.class.getDeclaredMethod("processExternalColumnStagingEvent", LogEvent.class);
        method.setAccessible(true);
        Assert.assertEquals(Boolean.FALSE, method.invoke(transaction, mock(LogEvent.class)));
        transaction.release();
    }

    private Transaction newTransaction() {
        return new Transaction(storage, events.descriptionLogEvent, mock(FormatDescriptionEvent.class), runtimeContext);
    }

    private ExternalColumnTxnContext stagingContext(int seqId, long slotAddr, byte[] raw) {
        ExternalColumnTxnContext result = new ExternalColumnTxnContext(storage);
        result.put(seqId, slotAddr, TABLE_ID, raw);
        return result;
    }

    private ReformatContext reformatContext(ExternalColumnTxnContext txnContext, IteratorBuffer iterator) {
        ReformatContext context = new ReformatContext("utf8", "utf8", 0, "dn-test");
        context.setServerId(1000L);
        context.setVirtualTSO("test-tso");
        context.setExternalColumnTxnContext(txnContext);
        context.setIt(iterator);
        return context;
    }

    private EventData eventData(RowsLogEvent rows) {
        return EventData.newBuilder()
            .setRowsQuery("trace")
            .setSchemaName("phy_db")
            .setTableName("phy_table")
            .setPayload(ByteString.copyFrom(events.encoded(rows)))
            .build();
    }

    private ReformatResult reformat(BinlogEventType type, String beforeBlobRef, String afterBlobRef,
                                    LogicTableMeta logicMeta, ExternalColumnTxnContext txnContext) throws Exception {
        return reformat(type, beforeBlobRef, afterBlobRef, logicMeta, txnContext, "varchar(128)");
    }

    private ReformatResult reformat(BinlogEventType type, String beforeBlobRef, String afterBlobRef,
                                    LogicTableMeta logicMeta, ExternalColumnTxnContext txnContext,
                                    String physicalType) throws Exception {
        List<Field> beforeFields = Arrays.asList(
            MakeFieldFactory.makeField(physicalType, beforeBlobRef, "utf8", true, false));
        List<Field> afterFields = Arrays.asList(
            MakeFieldFactory.makeField(physicalType, afterBlobRef, "utf8", true, false));
        TableMapLogEvent physicalTable = events.tableMap("phy_db", "phy_table", beforeFields);
        RowsLogEvent physicalRows = events.rows(type, physicalTable, beforeFields, afterFields, columnMap(1));

        PolarDbXTableMetaManager tableMetaManager = mock(PolarDbXTableMetaManager.class);
        when(tableMetaManager.compare("phy_db", "phy_table", 1)).thenReturn(logicMeta);
        when(tableMetaManager.getTableId("logic_db", "logic_table")).thenReturn(100L);
        RowEventReformator reformator = new RowEventReformator(false, tableMetaManager);
        ReformatContext context = new ReformatContext("utf8", "utf8", 0, "dn-test");
        context.setServerId(1000L);
        context.setVirtualTSO("test-tso");
        context.setExternalColumnTxnContext(txnContext);
        TxnItemRef txnItemRef = mock(TxnItemRef.class);
        EventData original = EventData.newBuilder()
            .setRowsQuery("trace")
            .setSchemaName("phy_db")
            .setTableName("phy_table")
            .setPayload(ByteString.copyFrom(events.encoded(physicalRows)))
            .build();

        Assert.assertTrue(reformator.reformat(physicalRows, txnItemRef, context, original));
        ArgumentCaptor<EventData> eventCaptor = ArgumentCaptor.forClass(EventData.class);
        verify(txnItemRef).setEventData(eventCaptor.capture());
        EventData result = eventCaptor.getValue();

        List<Field> logicalFields = Arrays.asList(
            MakeFieldFactory.makeField("longblob", new byte[0], "utf8", true, false));
        TableMapLogEvent logicalTable = events.tableMap("logic_db", "logic_table", logicalFields);
        RowsLogEvent decoded = events.decodeRows(result.getPayload().toByteArray(), type, logicalTable);
        RowsLogBuffer rowsBuffer = decoded.getRowsBuf("utf8");
        Assert.assertTrue(rowsBuffer.nextOneRow(decoded.getColumns()));
        Object beforeValue = rowsBuffer.nextValue(
            logicalTable.getColumnInfo()[0].type, logicalTable.getColumnInfo()[0].meta, true);
        Object afterValue = null;
        boolean afterColumnPresent = false;
        if (type == BinlogEventType.UPDATE_ROWS_EVENT || type == BinlogEventType.UPDATE_ROWS_EVENT_V1) {
            afterColumnPresent = decoded.getChangeColumns().get(0);
            if (afterColumnPresent) {
                Assert.assertTrue(rowsBuffer.nextOneRow(decoded.getChangeColumns()));
                afterValue = rowsBuffer.nextValue(
                    logicalTable.getColumnInfo()[0].type, logicalTable.getColumnInfo()[0].meta, true);
            }
        }
        return new ReformatResult(result, (byte[]) beforeValue, (byte[]) afterValue, afterColumnPresent);
    }

    private static LogicTableMeta externalLogicMeta(int physicalIndex, String mappingError) {
        return externalLogicMeta(physicalIndex, mappingError, true);
    }

    private static LogicTableMeta externalLogicMeta(int physicalIndex, String mappingError, boolean nullable) {
        TableMeta.FieldMeta logicalField =
            new TableMeta.FieldMeta("payload", "longblob", nullable, false, null, false, "utf8");
        logicalField.setExternalized(true);
        LogicTableMeta.FieldMetaExt field = new LogicTableMeta.FieldMetaExt(logicalField, 0, physicalIndex);
        field.setPhyFieldMeta(
            new TableMeta.FieldMeta("payload_addr_", "varchar(128)", true, false, null, false, "utf8"));
        if (mappingError != null) {
            field.markExternalMappingUnavailable(mappingError);
        }
        LogicTableMeta result = new LogicTableMeta();
        result.setCompatible(false);
        result.setLogicSchema("logic_db");
        result.setLogicTable("logic_table");
        result.setPhySchema("phy_db");
        result.setPhyTable("phy_table");
        result.add(field);
        return result;
    }

    private static List<Field> stagingFields(long slotAddr, long tableId, byte[] raw, boolean nullable) {
        return Arrays.asList(
            MakeFieldFactory.makeField("bigint unsigned", Long.toUnsignedString(slotAddr), "utf8", nullable, true),
            MakeFieldFactory.makeField("bigint unsigned", Long.toUnsignedString(tableId), "utf8", nullable, true),
            MakeFieldFactory.makeField("longblob", raw, "utf8", nullable, false),
            MakeFieldFactory.makeField("timestamp", "2026-08-06 12:00:00", "utf8", nullable, false));
    }

    private static BitMap allColumns() {
        return columnMap(4);
    }

    private static BitMap columnMap(int count) {
        BitMap columns = new BitMap(count);
        for (int i = 0; i < count; i++) {
            columns.set(i, true);
        }
        return columns;
    }

    private static String blobRef(int seqId, long slotAddr, byte[] raw) throws Exception {
        byte[] binary = new byte[33];
        binary[0] = 2;
        ByteBuffer.wrap(binary, 1, Integer.BYTES).putInt(seqId);
        ByteBuffer.wrap(binary, 5, Long.BYTES).putLong(slotAddr);
        ByteBuffer.wrap(binary, 13, Integer.BYTES).putInt(raw.length);
        System.arraycopy(MessageDigest.getInstance("MD5").digest(raw), 0, binary, 17, 16);
        char[] chars = new char[binary.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < binary.length; i++) {
            chars[i * 2] = digits[binary[i] >>> 4 & 0xF];
            chars[i * 2 + 1] = digits[binary[i] & 0xF];
        }
        return new String(chars);
    }

    private static void assertFailure(String messageFragment, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected PolardbxException containing: " + messageFragment);
        } catch (PolardbxException e) {
            Assert.assertTrue("unexpected message: " + e.getMessage(), e.getMessage().contains(messageFragment));
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    private static void assertFailureInCause(String messageFragment, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected PolardbxException containing: " + messageFragment);
        } catch (PolardbxException e) {
            Throwable current = e;
            while (current != null) {
                if (current.getMessage() != null && current.getMessage().contains(messageFragment)) {
                    return;
                }
                current = current.getCause();
            }
            throw new AssertionError("expected cause containing: " + messageFragment, e);
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    private static void assertDecodeFailure(String messageFragment, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected BlobRef decode failure containing: " + messageFragment);
        } catch (InvocationTargetException e) {
            Assert.assertTrue(e.getCause() instanceof PolardbxException);
            Assert.assertTrue("unexpected message: " + e.getCause().getMessage(),
                e.getCause().getMessage().contains(messageFragment));
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    private static void invokeDecodeBlobRef(RowEventReformator reformator, Field field, TableMapLogEvent table,
                                            LogicTableMeta.FieldMetaExt fieldMeta, ReformatContext context)
        throws Exception {
        Method method = RowEventReformator.class.getDeclaredMethod("decodeExternalBlobRef", Field.class,
            TableMapLogEvent.class, LogicTableMeta.FieldMetaExt.class, ReformatContext.class);
        method.setAccessible(true);
        method.invoke(reformator, field, table, fieldMeta, context);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class ReformatResult {
        private final EventData eventData;
        private final byte[] beforeValue;
        private final byte[] afterValue;
        private final boolean afterColumnPresent;

        private ReformatResult(EventData eventData, byte[] beforeValue, byte[] afterValue,
                               boolean afterColumnPresent) {
            this.eventData = eventData;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.afterColumnPresent = afterColumnPresent;
        }
    }

    private static class EventFixture {
        private static final long TABLE_MAP_ID = 1L;
        private final FormatDescriptionLogEvent descriptionLogEvent = new FormatDescriptionLogEvent((short) 4, 1);
        private final Map<RowsLogEvent, byte[]> encodedRows = new IdentityHashMap<>();

        private EventFixture() {
        }

        private TableMapLogEvent tableMap(String schema, String table, List<Field> fields) throws Exception {
            TableMapEventBuilder builder = new TableMapEventBuilder(
                (int) (System.currentTimeMillis() / 1000), 1, TABLE_MAP_ID, schema, table, "utf8");
            builder.setFieldList(fields);
            DecodedEvent event = rebuild(builder);
            return new TableMapLogEvent(event.header, event.buffer, descriptionLogEvent, "utf8");
        }

        private RowsLogEvent rows(BinlogEventType type, TableMapLogEvent tableMap, List<Field> fields,
                                  BitMap columns) throws Exception {
            return rows(type, tableMap, fields, fields, columns);
        }

        private RowsLogEvent rows(BinlogEventType type, TableMapLogEvent tableMap, List<Field> beforeFields,
                                  List<Field> afterFields, BitMap columns) throws Exception {
            RowEventBuilder builder = new RowEventBuilder(TABLE_MAP_ID, beforeFields.size(), type,
                (int) (System.currentTimeMillis() / 1000), 1);
            builder.setColumnsBitMap(columns);
            RowData row = new RowData();
            row.setBiFieldList(selectedFields(beforeFields, columns));
            row.setBiNullBitMap(new BitMap(countSetColumns(columns, beforeFields.size())));
            if (type == BinlogEventType.UPDATE_ROWS_EVENT || type == BinlogEventType.UPDATE_ROWS_EVENT_V1) {
                builder.setColumnsChangeBitMap(columns);
                row.setAiFieldList(selectedFields(afterFields, columns));
                row.setAiNullBitMap(new BitMap(countSetColumns(columns, afterFields.size())));
            }
            builder.addRowData(row);

            RowsLogEvent result;
            DecodedEvent event = rebuild(builder);
            if (type == BinlogEventType.WRITE_ROWS_EVENT || type == BinlogEventType.WRITE_ROWS_EVENT_V1) {
                result = new WriteRowsLogEvent(event.header, event.buffer, descriptionLogEvent, DecodeMode.NORMAL);
            } else if (type == BinlogEventType.DELETE_ROWS_EVENT || type == BinlogEventType.DELETE_ROWS_EVENT_V1) {
                result = new DeleteRowsLogEvent(event.header, event.buffer, descriptionLogEvent, DecodeMode.NORMAL);
            } else {
                result = new UpdateRowsLogEvent(event.header, event.buffer, descriptionLogEvent);
            }
            result.setTable(tableMap);
            encodedRows.put(result, event.encoded);
            return result;
        }

        private RowsLogEvent rowsBatch(BinlogEventType type, TableMapLogEvent tableMap,
                                       List<List<Field>> beforeRows, List<List<Field>> afterRows,
                                       BitMap columns) throws Exception {
            Assert.assertFalse(beforeRows.isEmpty());
            Assert.assertEquals(beforeRows.size(), afterRows.size());
            RowEventBuilder builder = new RowEventBuilder(TABLE_MAP_ID, beforeRows.get(0).size(), type,
                (int) (System.currentTimeMillis() / 1000), 1);
            builder.setColumnsBitMap(columns);
            builder.setColumnsChangeBitMap(columns);
            for (int i = 0; i < beforeRows.size(); i++) {
                List<Field> beforeFields = beforeRows.get(i);
                List<Field> afterFields = afterRows.get(i);
                RowData row = new RowData();
                row.setBiFieldList(selectedFields(beforeFields, columns));
                row.setBiNullBitMap(new BitMap(countSetColumns(columns, beforeFields.size())));
                row.setAiFieldList(selectedFields(afterFields, columns));
                row.setAiNullBitMap(new BitMap(countSetColumns(columns, afterFields.size())));
                builder.addRowData(row);
            }

            DecodedEvent event = rebuild(builder);
            RowsLogEvent result = new UpdateRowsLogEvent(event.header, event.buffer, descriptionLogEvent);
            result.setTable(tableMap);
            encodedRows.put(result, event.encoded);
            return result;
        }

        private RowsLogEvent rowsWithNull(BinlogEventType type, TableMapLogEvent tableMap, List<Field> fields,
                                          BitMap columns, int nullColumnIndex) throws Exception {
            RowEventBuilder builder = new RowEventBuilder(TABLE_MAP_ID, fields.size(), type,
                (int) (System.currentTimeMillis() / 1000), 1);
            builder.setColumnsBitMap(columns);
            RowData row = new RowData();
            List<Field> nonNullFields = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                if (columns.get(i) && i != nullColumnIndex) {
                    nonNullFields.add(fields.get(i));
                }
            }
            row.setBiFieldList(nonNullFields);
            BitMap nullBitMap = new BitMap(countSetColumns(columns, fields.size()));
            nullBitMap.set(nullColumnIndex, true);
            row.setBiNullBitMap(nullBitMap);
            builder.addRowData(row);

            DecodedEvent event = rebuild(builder);
            RowsLogEvent result = new WriteRowsLogEvent(
                event.header, event.buffer, descriptionLogEvent, DecodeMode.NORMAL);
            result.setTable(tableMap);
            encodedRows.put(result, event.encoded);
            return result;
        }

        private byte[] encoded(RowsLogEvent event) {
            return encodedRows.get(event);
        }

        private RowsLogEvent decodeRows(byte[] encoded, BinlogEventType type, TableMapLogEvent tableMap) {
            EventBuffer input = new EventBuffer(encoded);
            LogHeader header = new LogHeader(input, descriptionLogEvent);
            input.limit(encoded.length - LogEvent.BINLOG_CHECKSUM_LEN);
            RowsLogEvent result;
            if (type == BinlogEventType.WRITE_ROWS_EVENT || type == BinlogEventType.WRITE_ROWS_EVENT_V1) {
                result = new WriteRowsLogEvent(header, input, descriptionLogEvent, DecodeMode.NORMAL);
            } else if (type == BinlogEventType.DELETE_ROWS_EVENT || type == BinlogEventType.DELETE_ROWS_EVENT_V1) {
                result = new DeleteRowsLogEvent(header, input, descriptionLogEvent, DecodeMode.NORMAL);
            } else {
                result = new UpdateRowsLogEvent(header, input, descriptionLogEvent);
            }
            result.setTable(tableMap);
            return result;
        }

        private TableMapLogEvent decodeTableMap(byte[] encoded) {
            EventBuffer input = new EventBuffer(encoded);
            LogHeader header = new LogHeader(input, descriptionLogEvent);
            input.limit(encoded.length - LogEvent.BINLOG_CHECKSUM_LEN);
            return new TableMapLogEvent(header, input, descriptionLogEvent, "utf8");
        }

        private static List<Field> selectedFields(List<Field> fields, BitMap columns) {
            List<Field> selected = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                if (columns.get(i)) {
                    selected.add(fields.get(i));
                }
            }
            return selected;
        }

        private static int countSetColumns(BitMap columns, int columnCount) {
            int result = 0;
            for (int i = 0; i < columnCount; i++) {
                if (columns.get(i)) {
                    result++;
                }
            }
            return result;
        }

        private DecodedEvent rebuild(BinlogBuilder builder) throws Exception {
            AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);
            int size = builder.write(output);
            byte[] encoded = Arrays.copyOf(output.toBytes(), size);
            EventBuffer input = new EventBuffer(encoded);
            LogHeader header = new LogHeader(input, descriptionLogEvent);
            input.limit(size - LogEvent.BINLOG_CHECKSUM_LEN);
            return new DecodedEvent(header, input, encoded);
        }

        private static class DecodedEvent {
            private final LogHeader header;
            private final EventBuffer buffer;
            private final byte[] encoded;

            private DecodedEvent(LogHeader header, EventBuffer buffer, byte[] encoded) {
                this.header = header;
                this.buffer = buffer;
                this.encoded = encoded;
            }
        }

        private static class EventBuffer extends LogBuffer {
            private EventBuffer(byte[] encoded) {
                super(encoded, 0, encoded.length);
            }
        }
    }
}
