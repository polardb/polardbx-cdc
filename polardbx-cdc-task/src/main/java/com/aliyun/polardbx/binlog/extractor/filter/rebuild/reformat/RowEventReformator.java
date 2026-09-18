/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.EventReformater;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.RowDataRebuildLogger;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.RowsLogEventRebuilder;
import com.aliyun.polardbx.binlog.extractor.log.ExternalColumnBlobRef;
import com.aliyun.polardbx.binlog.extractor.log.ExternalColumnTxnContext;
import com.aliyun.polardbx.binlog.extractor.log.UnsupportedExternalColumnBlobRefVersionException;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.field.SimpleField;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.google.common.collect.Lists;
import com.google.protobuf.UnsafeByteOperations;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext.toByte;

public class RowEventReformator implements EventReformater<RowsLogEvent> {

    private static final Logger log = LoggerFactory.getLogger("rebuildEventLogger");
    private final RowDataRebuildLogger rebuildLogger = new RowDataRebuildLogger();
    private final boolean deepDecodeEvent;
    private final PolarDbXTableMetaManager tableMetaManager;
    private final boolean noForeignKeyCheck;

    public RowEventReformator(boolean deepDecodeEvent, PolarDbXTableMetaManager tableMetaManager) {
        this.deepDecodeEvent = deepDecodeEvent;
        this.tableMetaManager = tableMetaManager;
        this.noForeignKeyCheck = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_REFORMAT_NO_FOREIGN_KEY_CHECK);
    }

    @Override
    public Set<Integer> interest() {
        Set<Integer> idSet = new HashSet<>();
        idSet.add(LogEvent.UPDATE_ROWS_EVENT);
        idSet.add(LogEvent.UPDATE_ROWS_EVENT_V1);
        idSet.add(LogEvent.WRITE_ROWS_EVENT);
        idSet.add(LogEvent.WRITE_ROWS_EVENT_V1);
        idSet.add(LogEvent.DELETE_ROWS_EVENT);
        idSet.add(LogEvent.DELETE_ROWS_EVENT_V1);
        return idSet;
    }

    @Override
    public boolean accept(RowsLogEvent event) {
        if (SystemDB.isSys(event.getTable().getDbName())) {
            return false;
        }
        return true;
    }

    @Override
    public void register(Map<Integer, EventReformater> map) {
        for (int id : interest()) {
            map.put(id, this);
        }
    }

    /**
     * 需要整形或者多流情况
     */
    boolean needReformat(LogicTableMeta tableMeta) {
        return !tableMeta.isCompatible() || deepDecodeEvent;
    }

    private void doReformat(RowsLogEvent rle, LogicTableMeta tableMeta, TxnItemRef txnItemRef,
                            ReformatContext context, EventData eventData) throws Exception {

        boolean splitRow = false;
        boolean extractPk = false;
        if (deepDecodeEvent) {
            splitRow = true;
            extractPk = true;
        }
        /*
         * Read the compatibility switch once for this event. Row splitting and per-row after-bitmap rebuilding
         * are one protocol decision and must observe the same value even if dynamic configuration changes while
         * the transaction is being reformatted.
         *
         * This switch only controls the external-column behavior added for ordinary global CDC. deepDecodeEvent
         * keeps its pre-existing per-row split and PK extraction regardless of this value.
         */
        boolean partialExternalUpdateRowImage = tableMeta.hasExternalizedFields()
            && isUpdateRows(rle.getHeader().getType())
            && DynamicApplicationConfig.getBoolean(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_PARTIAL_UPDATE_ROW_IMAGE_ENABLED);
        if (partialExternalUpdateRowImage) {
            /*
             * One UPDATE_ROWS event owns a single after-column bitmap, even when it contains many rows. An
             * unchanged external BlobRef may be omitted from one row while another row in the same source event
             * has a genuinely new address whose raw value must be restored from staging. Split externalized UPDATEs
             * by row before rebuilding so every output event can describe that row's exact logical after image.
             *
             * This is also required for generic MySQL consumers: emitting an unchanged 66-byte physical BlobRef
             * as a logical LONGTEXT/LONGBLOB value overwrites the target's existing raw value. Omitting that
             * column from the after bitmap is the standard row-binlog representation of "keep the old value".
             */
            splitRow = true;
        }
        List<RowEventBuilder> rebList =
            RowsLogEventRebuilder.convert(rle, tableMeta, context.getServerId(), splitRow, extractPk);

        // 将需要进行拆分的Event进行remove
        boolean convertToMulti = rebList.size() > 1;
        IteratorBuffer it = context.getIt();
        if (convertToMulti) {
            it.remove();
        }

        Iterator<RowEventBuilder> rebIt = rebList.iterator();
        while (rebIt.hasNext()) {
            RowEventBuilder reb = rebIt.next();
            if (!tableMeta.isCompatible()) {
                rebuildRowEventBuilder(tableMeta, reb, rle.getTable(), context, partialExternalUpdateRowImage);
            }
            if (convertToMulti) {
                eventData = eventData.toBuilder()
                    .setSchemaName(tableMeta.getLogicSchema())
                    .setTableName(tableMeta.getLogicTable()).build();
                txnItemRef.setHashKey(reb.getHashKey());
                if (reb.getPrimaryKey() != null) {
                    txnItemRef.setPrimaryKey(Lists.newArrayList(reb.getPrimaryKey()));
                }
                TxnBufferItem txnItem = convert(txnItemRef, reb, eventData);
                it.appendAfter(txnItem);
            } else {
                txnItemRef.setHashKey(reb.getHashKey());
                if (reb.getPrimaryKey() != null) {
                    txnItemRef.setPrimaryKey(Lists.newArrayList(reb.getPrimaryKey()));
                }
                eventData = eventData.toBuilder()
                    .setSchemaName(tableMeta.getLogicSchema())
                    .setTableName(tableMeta.getLogicTable())
                    .setPayload(UnsafeByteOperations.unsafeWrap(toByte(reb))).build();
                txnItemRef.setEventData(eventData);
            }
        }
    }

    @Override
    public boolean reformat(RowsLogEvent rle, TxnItemRef txnItemRef, ReformatContext context, EventData eventData) {
        LogicTableMeta tableMeta =
            tableMetaManager.compare(rle.getTable().getDbName(), rle.getTable().getTableName(), rle.getColumnLen());
        // 整形只考虑 insert,其他可以不考虑,如果 是全镜像导致下游报错，则全部都需要处理
        if (log.isDebugEnabled()) {
            log.debug("detected compatible " + tableMeta.isCompatible() + " table meta for event, "
                + "will reformat event " + tableMeta.getPhySchema() + tableMeta.getPhyTable());
        }
        try {
            long tableId = tableMetaManager.getTableId(tableMeta.getLogicSchema(), tableMeta.getLogicTable());
            rle.setTableId(tableId);
            if (needReformat(tableMeta)) {
                // 单独update header中的 serverId即可.
                doReformat(rle, tableMeta, txnItemRef, context, eventData);
            } else {
                byte[] data = DirectByteOutput.unsafeFetch(eventData.getPayload());
                ByteArray byteArray = new ByteArray(data);
                // 修改serverId
                byteArray.skip(5);
                byteArray.writeLong(context.getServerId(), 4);
                if (noForeignKeyCheck) {
                    int postHeaderLen = FormatDescriptionEvent.EVENT_HEADER_LENGTH[rle.getHeader().getType() - 1];
                    int skipBytes = FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN;

                    // skip common header
                    byteArray.reset();
                    byteArray.skip(skipBytes);
                    if (postHeaderLen == 6) {
                        byteArray.writeLong(tableId, 4);
                        skipBytes += 4;
                    } else {
                        byteArray.writeLong(tableId, 6);
                        skipBytes += 6;
                    }

                    // skip common header + table id
                    byteArray.reset();
                    byteArray.skip(skipBytes);
                    int oldFlags = byteArray.readInteger(2);
                    if ((oldFlags & RowsLogEvent.NO_FOREIGN_KEY_CHECKS_F) == 0) {
                        byteArray.reset();
                        byteArray.skip(skipBytes);
                        byteArray.writeLong(oldFlags | RowsLogEvent.NO_FOREIGN_KEY_CHECKS_F, 2);
                    }
                }
                eventData = eventData.toBuilder()
                    .setSchemaName(tableMeta.getLogicSchema())
                    .setTableName(tableMeta.getLogicTable())
                    .setRowsQuery(eventData.getRowsQuery())
                    .setPayload(UnsafeByteOperations.unsafeWrap(data)).build();
                txnItemRef.setEventData(eventData);
            }

        } catch (Exception e) {
            throw new PolardbxException(" reformat log pos : " + rle.getHeader().getLogPos() + " occur error", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("row event : " + JSONObject.toJSONString(rle.toBytes()));
        }
        return true;
    }

    private TxnBufferItem convert(TxnItemRef txnItemRef, RowEventBuilder reb, EventData eventData)
        throws Exception {
        return TxnBufferItem.builder()
            .traceId(txnItemRef.getTraceId())
            .rowsQuery(eventData.getRowsQuery())
            .eventType(txnItemRef.getEventType())
            .originTraceId(txnItemRef.getTraceId())
            .schema(eventData.getSchemaName())
            .table(eventData.getTableName())
            .payload(toByte(reb))
            .hashKey(reb.getHashKey())
            // Per-row splitting and primary-key extraction are independent. Externalized UPDATEs split rows
            // to carry an exact after bitmap even outside deep-decode mode, where primaryKey is intentionally
            // absent. Preserve that absence instead of asking Guava to copy a null array.
            .primaryKey(reb.getPrimaryKey() == null ? null : Lists.newArrayList(reb.getPrimaryKey()))
            .build();
    }

    private void rebuildRowEventBuilder(LogicTableMeta tableMeta, RowEventBuilder reb, TableMapLogEvent table,
                                        ReformatContext context, boolean partialExternalUpdateRowImage) {
        List<LogicTableMeta.FieldMetaExt> fieldMetas = tableMeta.getLogicFields();
        int newColSize = fieldMetas.size();
        reb.setColumnCount(newColSize);
        List<RowData> rowDataList = reb.getRowDataList();
        BitMap columnBitMap = new BitMap(newColSize);
        reb.setColumnsBitMap(columnBitMap);
        List<RowData> newRowDataList = new ArrayList<>();
        BitMap originalAiChangeBitMap = reb.isUpdate() ? reb.getColumnsChangeBitMap() : null;
        BitMap rebuiltAiChangeBitMap = null;
        for (RowData rowData : rowDataList) {
            RowData newRowData = new RowData();
            rebuildLogger.logRowBegin(tableMeta, table, rowData, reb.getEventType());
            processBIImage(tableMeta, fieldMetas, table, rowData, newRowData, reb, context);
            if (reb.isUpdate()) {
                rebuiltAiChangeBitMap = processAIImage(tableMeta, fieldMetas, rowData, newRowData,
                    originalAiChangeBitMap, table, context, partialExternalUpdateRowImage);
            }
            if (newColSize < table.getColumnCnt()) {
                rebuildLogger.logRemoveField();
            }
            rebuildLogger.logEnd();

            newRowDataList.add(newRowData);
        }
        if (reb.isUpdate()) {
            if (partialExternalUpdateRowImage) {
                reb.setColumnsChangeBitMap(rebuiltAiChangeBitMap);
            } else {
                resetChangeRowColumnBitMap(fieldMetas, reb);
            }
        }
        reb.setRowDataList(newRowDataList);
    }

    private void resetChangeRowColumnBitMap(List<LogicTableMeta.FieldMetaExt> fieldMetas,
                                            RowEventBuilder reb) {
        BitMap newAIChangeBitMap = new BitMap(fieldMetas.size());
        BitMap orgAiChangeBitMap = reb.getColumnsChangeBitMap();
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();
            if (phyIndex < 0) {
                newAIChangeBitMap.set(logicIndex, true);
            } else {
                boolean exist = orgAiChangeBitMap.get(phyIndex);
                newAIChangeBitMap.set(logicIndex, exist);
            }
        }
        reb.setColumnsChangeBitMap(newAIChangeBitMap);
    }

    private void processBIImage(LogicTableMeta tableMeta, List<LogicTableMeta.FieldMetaExt> fieldMetas,
                                TableMapLogEvent table, RowData oldRowData, RowData newRowData,
                                RowEventBuilder reb, ReformatContext context) {
        List<Field> dataField = oldRowData.getBiFieldList();
        BitMap biNullBitMap = oldRowData.getBiNullBitMap();
        List<Field> newBiFieldList = new ArrayList<>(fieldMetas.size());
        BitMap newBiNullBitMap = new BitMap(fieldMetas.size());
        BitMap newColumnBitMap = new BitMap(fieldMetas.size());
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int phyIndex = fieldMetaExt.getPhyIndex();
            int logicIdx = fieldMetaExt.getLogicIndex();
            newColumnBitMap.set(logicIdx, true);
            Field biField;
            if (phyIndex < 0) {
                /*
                 * phyIndex is the source-column position in the DN physical row image. A negative value means that
                 * Meta compare could not find a physical column for this logical column. Existing non-external
                 * schema-evolution behavior synthesizes its default value. An external column has neither a BlobRef
                 * address nor raw data to read, so it fails closed unless the emergency switch explicitly allows
                 * this before-image field to be represented as NULL.
                 */
                if (fieldMetaExt.isExternalized()) {
                    fallbackExternalFieldToNull(fieldMetaExt, context, "before image",
                        fieldMetaExt.getExternalMappingError(), null);
                    newBiNullBitMap.set(logicIdx, true);
                    continue;
                }
                String charset = fieldMetaExt.getCharset();
                biField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    resolveDefaultValue(fieldMetaExt),
                    charset,
                    fieldMetaExt.isNullable(), fieldMetaExt.isUnsigned());
                boolean isNull = biField.isNull();
                newBiNullBitMap.set(logicIdx, isNull);
                if (!isNull) {
                    newBiFieldList.add(biField);
                    rebuildLogger.logAddField(fieldMetaExt, biField.getMysqlType().getType(), biField.doGetTableMeta(),
                        biField.encode());
                }
            } else {
                biField = dataField.get(phyIndex);
                boolean isNull = biNullBitMap.get(phyIndex);
                if (!isNull) {
                    if (fieldMetaExt.isExternalized()) {
                        boolean mappingUnavailable = fieldMetaExt.isExternalMappingUnavailable();
                        if (mappingUnavailable) {
                            ensureExternalColumnFallbackEnabled(fieldMetaExt.getExternalMappingError(), null);
                        }
                        DecodedExternalBlobRef blobRef = decodeExternalBlobRefOrNull(
                            biField, table, fieldMetaExt, context, "before image");
                        if (blobRef == null) {
                            newBiNullBitMap.set(logicIdx, true);
                            continue;
                        }
                        if (mappingUnavailable) {
                            biField = fallbackToExternalAddress(fieldMetaExt, blobRef, context,
                                fieldMetaExt.getExternalMappingError(), null);
                        } else if (isWriteRows(reb.getEventType())) {
                            biField = restoreExternalField(fieldMetaExt, blobRef, context);
                        } else if (blobRef.unsupportedVersion != null) {
                            biField = fallbackToExternalAddress(fieldMetaExt, blobRef, context,
                                blobRef.unsupportedVersion);
                        } else {
                            biField = makeExternalField(fieldMetaExt,
                                blobRef.text.getBytes(StandardCharsets.US_ASCII));
                        }
                    } else if (!fieldMetaExt.isTypeMatch()) {
                        biField = resolveDataTypeNotMatch(biField, table, fieldMetaExt);
                    }
                    newBiNullBitMap.set(logicIdx, biField.isNull());
                    if (!biField.isNull()) {
                        newBiFieldList.add(biField);
                    }
                } else {
                    newBiNullBitMap.set(logicIdx, true);
                }
            }
        }
        newRowData.setBiNullBitMap(newBiNullBitMap);
        newRowData.setBiFieldList(newBiFieldList);
        reb.setColumnsBitMap(newColumnBitMap);
    }

    private BitMap processAIImage(LogicTableMeta tableMeta, List<LogicTableMeta.FieldMetaExt> fieldMetas,
                                  RowData oldRowData, RowData newRowData, BitMap orgAiChangeBitMap,
                                  TableMapLogEvent table, ReformatContext context,
                                  boolean partialExternalUpdateRowImage) {
        BitMap newAIChangeBitMap =
            partialExternalUpdateRowImage ? new BitMap(fieldMetas.size()) : null;
        BitMap newAINullBitMap =
            partialExternalUpdateRowImage ? null : new BitMap(fieldMetas.size());
        List<Boolean> compactAINulls = partialExternalUpdateRowImage ? new ArrayList<>() : null;
        List<Field> newAIFiledList = new ArrayList<>();
        BitMap orgAiNullBitMap = oldRowData.getAiNullBitMap();
        List<Field> orgFieldList = oldRowData.getAiFieldList();
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();
            if (phyIndex < 0) {
                // Same missing physical-source case as the before image. The caller below records the fallback as an
                // explicit NULL in the compact after-image bitmap only when the emergency switch permits it.
                if (fieldMetaExt.isExternalized()) {
                    fallbackExternalFieldToNull(fieldMetaExt, context, "after image",
                        fieldMetaExt.getExternalMappingError(), null);
                    recordAIColumn(logicIndex, true, partialExternalUpdateRowImage,
                        newAIChangeBitMap, newAINullBitMap, compactAINulls);
                    continue;
                }
                String charset = fieldMetaExt.getCharset();
                Field aiField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    resolveDefaultValue(fieldMetaExt),
                    charset,
                    fieldMetaExt.isNullable(), fieldMetaExt.isUnsigned());
                recordAIColumn(logicIndex, aiField.isNull(), partialExternalUpdateRowImage,
                    newAIChangeBitMap, newAINullBitMap, compactAINulls);
                if (!aiField.isNull()) {
                    newAIFiledList.add(aiField);
                    rebuildLogger.logAddField(fieldMetaExt, aiField.getMysqlType().getType(), aiField.doGetTableMeta(),
                        aiField.encode());
                }
            } else {
                if (orgAiChangeBitMap.get(phyIndex)) {
                    boolean isNull = orgAiNullBitMap.get(phyIndex);
                    boolean omitUnchangedExternal = partialExternalUpdateRowImage && fieldMetaExt.isExternalized()
                        && isNull && oldRowData.getBiNullBitMap().get(phyIndex);

                    if (!isNull) {
                        Field aiField = orgFieldList.get(phyIndex);
                        if (fieldMetaExt.isExternalized()) {
                            boolean mappingUnavailable = fieldMetaExt.isExternalMappingUnavailable();
                            if (mappingUnavailable) {
                                ensureExternalColumnFallbackEnabled(fieldMetaExt.getExternalMappingError(), null);
                            }
                            DecodedExternalBlobRef afterBlobRef = decodeExternalBlobRefOrNull(
                                aiField, table, fieldMetaExt, context, "after image");
                            if (afterBlobRef == null) {
                                isNull = true;
                            } else if (mappingUnavailable) {
                                aiField = fallbackToExternalAddress(fieldMetaExt, afterBlobRef, context,
                                    fieldMetaExt.getExternalMappingError(), null);
                            } else {
                                DecodedExternalBlobRef beforeBlobRef = null;
                                if (!newRowData.getBiNullBitMap().get(logicIndex)
                                    && !oldRowData.getBiNullBitMap().get(phyIndex)) {
                                    beforeBlobRef = decodeExternalBlobRef(oldRowData.getBiFieldList().get(phyIndex),
                                        table, fieldMetaExt, context);
                                }
                                if (afterBlobRef.unsupportedVersion != null) {
                                    aiField = fallbackToExternalAddress(fieldMetaExt, afterBlobRef, context,
                                        afterBlobRef.unsupportedVersion);
                                } else if (beforeBlobRef != null && afterBlobRef.text.equals(beforeBlobRef.text)) {
                                    if (partialExternalUpdateRowImage) {
                                        /*
                                         * Equality of canonical BlobRef strings proves that the logical external
                                         * value did not change. Do not serialize the physical address as a
                                         * logical value. Omitting the after-image column makes MySQL and PolarDB-X
                                         * consumers retain their already materialized raw value without requiring
                                         * unavailable staging.
                                         */
                                        omitUnchangedExternal = true;
                                    } else {
                                        /*
                                         * Compatibility rollback: preserve the legacy full-row-looking output.
                                         * Generic MySQL consumers may persist this physical BlobRef as the logical
                                         * TEXT/BLOB value, but operators can use this mode if a consumer cannot
                                         * accept the partial UPDATE row image emitted by the default behavior.
                                         */
                                        aiField = makeExternalField(fieldMetaExt,
                                            afterBlobRef.text.getBytes(StandardCharsets.US_ASCII));
                                    }
                                } else {
                                    aiField = restoreExternalField(fieldMetaExt, afterBlobRef, context);
                                }
                            }
                        } else if (!fieldMetaExt.isTypeMatch()) {
                            aiField = resolveDataTypeNotMatch(aiField, table, fieldMetaExt);
                        }
                        if (!omitUnchangedExternal) {
                            isNull = isNull || aiField.isNull();
                            if (!isNull) {
                                newAIFiledList.add(aiField);
                            }
                        }
                    }
                    if (!omitUnchangedExternal) {
                        recordAIColumn(logicIndex, isNull, partialExternalUpdateRowImage,
                            newAIChangeBitMap, newAINullBitMap, compactAINulls);
                    }
                }
            }
        }
        if (partialExternalUpdateRowImage) {
            newAINullBitMap = new BitMap(compactAINulls.size());
            for (int i = 0; i < compactAINulls.size(); i++) {
                newAINullBitMap.set(i, compactAINulls.get(i));
            }
        }
        newRowData.setAiNullBitMap(newAINullBitMap);
        newRowData.setAiFieldList(newAIFiledList);
        return newAIChangeBitMap;
    }

    private void recordAIColumn(int logicIndex, boolean isNull, boolean partialExternalUpdateRowImage,
                                BitMap newAIChangeBitMap, BitMap newAINullBitMap,
                                List<Boolean> compactAINulls) {
        if (partialExternalUpdateRowImage) {
            newAIChangeBitMap.set(logicIndex, true);
            compactAINulls.add(isNull);
        } else {
            newAINullBitMap.set(logicIndex, isNull);
        }
    }

    private Field restoreExternalField(LogicTableMeta.FieldMetaExt fieldMeta, DecodedExternalBlobRef blobRef,
                                       ReformatContext context) {
        if (blobRef.unsupportedVersion != null) {
            return fallbackToExternalAddress(fieldMeta, blobRef, context, blobRef.unsupportedVersion);
        }
        ExternalColumnTxnContext txnContext = context.getExternalColumnTxnContext();
        if (txnContext == null) {
            return fallbackToExternalAddress(fieldMeta, blobRef, context, null);
        }

        final byte[] raw;
        try {
            raw = txnContext.resolve(blobRef.decodedValue);
        } catch (RuntimeException e) {
            return fallbackToExternalAddress(fieldMeta, blobRef, context, e);
        }
        return makeExternalField(fieldMeta, raw);
    }

    private Field fallbackToExternalAddress(LogicTableMeta.FieldMetaExt fieldMeta, DecodedExternalBlobRef blobRef,
                                            ReformatContext context,
                                            RuntimeException failure) {
        return fallbackToExternalAddress(fieldMeta, blobRef, context, null, failure);
    }

    private Field fallbackToExternalAddress(LogicTableMeta.FieldMetaExt fieldMeta, DecodedExternalBlobRef blobRef,
                                            ReformatContext context, String fallbackReason,
                                            RuntimeException failure) {
        /*
         * This is the common boundary for every intentional BlobRef output: BlobRef compatibility fallback and a
         * valid V2 BlobRef whose staging raw cannot be recovered. Keeping the switch check here prevents a future
         * staging rename (which may bypass staging-event validation entirely) from silently leaking addresses.
         */
        ensureExternalColumnFallbackEnabled(
            StringUtils.defaultIfBlank(fallbackReason, "external-column raw is unavailable"), failure);
        if (context.markExternalColumnAddressFallbackLogged()) {
            if (blobRef.unsupportedVersion != null) {
                log.error("external-column BlobRef version is unsupported, emergency fallback to the original "
                        + "BlobRef is enabled, tso {}, storage {}, column {}, observedVersion {}, "
                        + "supportedVersion {}, blobRefLength {}, config {}",
                    context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(),
                    blobRef.unsupportedVersion.getObservedVersion(),
                    blobRef.unsupportedVersion.getSupportedVersion(), blobRef.text.length(),
                    ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED,
                    blobRef.unsupportedVersion);
                return makeExternalField(fieldMeta, blobRef.text.getBytes(StandardCharsets.US_ASCII));
            }
            if (StringUtils.isNotBlank(fallbackReason)) {
                log.error("external-column BlobRef metadata is incompatible, emergency fallback to the original "
                        + "BlobRef is enabled, tso {}, storage {}, column {}, reason {}, config {}",
                    context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(), fallbackReason,
                    ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED, failure);
                return makeExternalField(fieldMeta, blobRef.text.getBytes(StandardCharsets.US_ASCII));
            }
            String message = "external-column raw is unavailable, fallback to BlobRef address, tso {}, storage {}, "
                + "column {}, seqId {}, slotAddr {}";
            if (failure == null) {
                log.error(message, context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(),
                    blobRef.decodedValue.getSeqId(), Long.toUnsignedString(blobRef.decodedValue.getSlotAddr()));
            } else {
                log.error(message, context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(),
                    blobRef.decodedValue.getSeqId(), Long.toUnsignedString(blobRef.decodedValue.getSlotAddr()),
                    failure);
            }
        }
        return makeExternalField(fieldMeta, blobRef.text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Handles the emergency NULL fallback when no usable physical BlobRef address exists for a logical external
     * field. This covers both a missing logical-to-physical mapping and an address field whose value cannot be
     * decoded. A valid BlobRef whose staging raw is unavailable uses {@link #fallbackToExternalAddress} instead.
     *
     * <p>This method does not mutate {@link RowData}. It enforces the shared fallback switch and records the diagnostic
     * log; the before/after-image caller is responsible for setting the corresponding logical NULL bitmap bit.</p>
     */
    private void fallbackExternalFieldToNull(LogicTableMeta.FieldMetaExt fieldMeta, ReformatContext context,
                                             String rowImage, String reason, RuntimeException failure) {
        ensureExternalColumnFallbackEnabled(reason, failure);
        if (context.markExternalColumnNullFallbackLogged()) {
            String message = "external-column field is unavailable, fallback this row-image field to NULL, tso {}, "
                + "storage {}, column {}, rowImage {}, reason {}";
            String resolvedReason = StringUtils.defaultIfBlank(reason, "external BlobRef mapping is unavailable");
            if (failure == null) {
                log.error(message, context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(),
                    rowImage, resolvedReason);
            } else {
                log.error(message, context.getVirtualTSO(), context.getStorageInstanceId(), fieldMeta.getColumnName(),
                    rowImage, resolvedReason, failure);
            }
        }
    }

    private void ensureExternalColumnFallbackEnabled(String reason, RuntimeException failure) {
        if (DynamicApplicationConfig.getBoolean(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED)) {
            return;
        }
        String resolvedReason = StringUtils.defaultIfBlank(reason, "external-column data is unavailable");
        throw new PolardbxException(resolvedReason, failure);
    }

    private DecodedExternalBlobRef decodeExternalBlobRefOrNull(Field field, TableMapLogEvent table,
                                                               LogicTableMeta.FieldMetaExt fieldMeta,
                                                               ReformatContext context, String rowImage) {
        try {
            return decodeExternalBlobRef(field, table, fieldMeta, context);
        } catch (ExternalColumnFieldValueException e) {
            fallbackExternalFieldToNull(fieldMeta, context, rowImage, e.getMessage(), e);
            return null;
        }
    }

    private Field makeExternalField(LogicTableMeta.FieldMetaExt fieldMeta, byte[] raw) {
        return MakeFieldFactory.makeField(fieldMeta.getColumnType(), raw, fieldMeta.getCharset(),
            fieldMeta.isNullable(), fieldMeta.isUnsigned());
    }

    private DecodedExternalBlobRef decodeExternalBlobRef(Field field, TableMapLogEvent table,
                                                         LogicTableMeta.FieldMetaExt fieldMeta,
                                                         ReformatContext context) {
        int phyIndex = fieldMeta.getPhyIndex();
        TableMapLogEvent.ColumnInfo[] columnInfos = table.getColumnInfo();
        if (columnInfos == null || phyIndex < 0 || phyIndex >= columnInfos.length) {
            throw new PolardbxException("invalid TABLE_MAP column mapping for external-column physical BlobRef "
                + fieldMeta.getColumnName());
        }
        TableMapLogEvent.ColumnInfo columnInfo = columnInfos[phyIndex];
        if (columnInfo == null) {
            throw new PolardbxException("missing TABLE_MAP column info for external-column physical BlobRef "
                + fieldMeta.getColumnName());
        }
        final String blobRefText;
        try {
            if (!(field instanceof SimpleField)) {
                throw new ExternalColumnFieldValueException("invalid physical BlobRef field implementation for "
                    + fieldMeta.getColumnName());
            }
            byte[] encoded = ((SimpleField) field).getData();
            RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(new LogBuffer(encoded, 0, encoded.length), 0, "utf8");
            Serializable blobRefValue = rowsLogBuffer.fetchValue(columnInfo.type, columnInfo.meta, false, false);
            if (!(blobRefValue instanceof String)) {
                throw new ExternalColumnFieldValueException("invalid physical BlobRef value type for "
                    + fieldMeta.getColumnName());
            }
            blobRefText = (String) blobRefValue;
        } catch (ExternalColumnFieldValueException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExternalColumnFieldValueException("invalid BlobRef value for "
                + fieldMeta.getColumnName(), e);
        }

        try {
            return new DecodedExternalBlobRef(blobRefText, ExternalColumnBlobRef.decode(blobRefText), null);
        } catch (UnsupportedExternalColumnBlobRefVersionException e) {
            if (!DynamicApplicationConfig.getBoolean(
                ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED)) {
                throw e;
            }
            return new DecodedExternalBlobRef(blobRefText, null, e);
        }
    }

    private static class DecodedExternalBlobRef {
        private final String text;
        private final ExternalColumnBlobRef decodedValue;
        private final UnsupportedExternalColumnBlobRefVersionException unsupportedVersion;

        private DecodedExternalBlobRef(String text, ExternalColumnBlobRef decodedValue,
                                       UnsupportedExternalColumnBlobRefVersionException unsupportedVersion) {
            this.text = text;
            this.decodedValue = decodedValue;
            this.unsupportedVersion = unsupportedVersion;
        }
    }

    private static class ExternalColumnFieldValueException extends PolardbxException {
        private ExternalColumnFieldValueException(String message) {
            super(message);
        }

        private ExternalColumnFieldValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private boolean isWriteRows(int eventType) {
        return eventType == LogEvent.WRITE_ROWS_EVENT || eventType == LogEvent.WRITE_ROWS_EVENT_V1;
    }

    private boolean isUpdateRows(int eventType) {
        return eventType == LogEvent.UPDATE_ROWS_EVENT || eventType == LogEvent.UPDATE_ROWS_EVENT_V1;
    }

    private Field resolveDataTypeNotMatch(Field field, TableMapLogEvent tableMapLogEvent,
                                          LogicTableMeta.FieldMetaExt fieldMetaExt) {
        int phyIndex = fieldMetaExt.getPhyIndex();
        SimpleField simpleField = (SimpleField) field;
        TableMapLogEvent.ColumnInfo columnInfo = tableMapLogEvent.getColumnInfo()[phyIndex];
        byte[] value = simpleField.getData();
        LogBuffer logBuffer = new LogBuffer(value, 0, value.length);
        TableMeta.FieldMeta phyFieldMeta = fieldMetaExt.getPhyFieldMeta();
        String phyJavaCharset =
            CharsetConversion.getJavaCharset(phyFieldMeta.getCharset());
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(logBuffer, 0, phyJavaCharset);
        Serializable serializable =
            rowsLogBuffer.fetchValue(columnInfo.type, columnInfo.meta, false, phyFieldMeta.isUnsigned());

        String logicMySqlCharset = fieldMetaExt.getCharset();
        Field dest = MakeFieldFactory.makField4TypeMisMatch(fieldMetaExt.getColumnType(),
            serializable,
            logicMySqlCharset,
            fieldMetaExt.isNullable(),
            fieldMetaExt.getDefaultValue(),
            fieldMetaExt.isUnsigned());
        rebuildLogger
            .logData(columnInfo.type, serializable, fieldMetaExt, dest.getMysqlType().getType(), dest.doGetTableMeta(),
                dest.encode());
        return dest;
    }

    /**
     * 为新增列解析默认值
     * 优先级: 显式默认值 > NULL(如果可空) > 类型安全的默认值
     *
     * @param fieldMetaExt 字段元数据
     * @return 默认值, 类型与字段匹配
     */
    private Serializable resolveDefaultValue(LogicTableMeta.FieldMetaExt fieldMetaExt) {
        // 1. 优先使用显式定义的默认值
        String defaultValue = fieldMetaExt.getDefaultValue();
        if (StringUtils.isNotEmpty(defaultValue)) {
            return defaultValue;
        }
        // 2. 如果字段可为NULL,返回null
        if (fieldMetaExt.isNullable()) {
            return null;
        }
        // 3. 根据列类型返回类型安全的默认值
        String columnType = fieldMetaExt.getColumnType().toLowerCase().trim();
        // 时间类型
        if (columnType.equals("datetime") || columnType.startsWith("datetime(")) {
            return "1000-01-01 00:00:00";
        }
        if (columnType.equals("timestamp") || columnType.startsWith("timestamp(")) {
            return "1970-01-01 00:00:01";
        }
        if (columnType.equals("date")) {
            return "1000-01-01";
        }
        if (columnType.equals("time") || columnType.startsWith("time(")) {
            return "00:00:00";
        }
        if (columnType.equals("year")) {
            return "1901";
        }
        // 整数类型
        if (columnType.equals("tinyint") || columnType.startsWith("tinyint(")
            || columnType.equals("smallint") || columnType.startsWith("smallint(")
            || columnType.equals("mediumint") || columnType.startsWith("mediumint(")
            || columnType.equals("int") || columnType.startsWith("int(")) {
            return 0;
        }
        if (columnType.equals("bigint") || columnType.startsWith("bigint(")) {
            return 0L;
        }
        // 浮点/精确数值类型
        if (columnType.equals("float") || columnType.startsWith("float(")) {
            return 0.0f;
        }
        if (columnType.equals("double") || columnType.startsWith("double(")) {
            return 0.0d;
        }
        if (columnType.equals("decimal") || columnType.startsWith("decimal(")
            || columnType.equals("numeric") || columnType.startsWith("numeric(")) {
            return "0";  // decimal/numeric 使用字符串表示
        }
        // bit 类型
        if (columnType.equals("bit") || columnType.startsWith("bit(")) {
            return 0L;
        }
        // 字符串类型
        if (columnType.startsWith("varchar") || columnType.startsWith("char")
            || columnType.startsWith("text") || columnType.startsWith("tinytext")
            || columnType.startsWith("mediumtext") || columnType.startsWith("longtext")) {
            return "";
        }
        // 二进制类型
        if (columnType.startsWith("blob") || columnType.startsWith("tinyblob")
            || columnType.startsWith("mediumblob") || columnType.startsWith("longblob")
            || columnType.startsWith("binary") || columnType.startsWith("varbinary")) {
            return new byte[0];
        }
        // enum/set 类型 - 返回空字符串(表示第一个枚举值或空集合)
        if (columnType.startsWith("enum") || columnType.startsWith("set")) {
            return "";
        }
        // json 类型
        if (columnType.equals("json")) {
            return "{}";
        }
        // 兜底: 返回空字符串
        return "";
    }

}
