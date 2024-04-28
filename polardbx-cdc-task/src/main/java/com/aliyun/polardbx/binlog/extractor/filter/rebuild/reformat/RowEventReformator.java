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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
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
    private final boolean binlogx;
    private final PolarDbXTableMetaManager tableMetaManager;

    public RowEventReformator(boolean binlogx,
                              PolarDbXTableMetaManager tableMetaManager) {
        this.binlogx = binlogx;
        this.tableMetaManager = tableMetaManager;
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
        return !tableMeta.isCompatible() || binlogx;
    }

    private void doReformat(RowsLogEvent rle, LogicTableMeta tableMeta, TxnItemRef txnItemRef,
                            ReformatContext context, EventData eventData) throws Exception {

        boolean splitRow = false;
        boolean extractPk = false;
        if (binlogx) {
            splitRow = true;
            extractPk = true;
        }
        List<RowEventBuilder> rebList =
            RowsLogEventRebuilder.convert(rle, tableMeta, context.getServerId(), splitRow, extractPk);

        //将需要进行拆分的Event进行remove
        boolean convertToMulti = rebList.size() > 1;
        IteratorBuffer it = context.getIt();
        if (convertToMulti) {
            it.remove();
        }

        Iterator<RowEventBuilder> rebIt = rebList.iterator();
        while (rebIt.hasNext()) {
            RowEventBuilder reb = rebIt.next();
            if (!tableMeta.isCompatible()) {
                rebuildRowEventBuilder(tableMeta, reb, rle.getTable());
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

            if (needReformat(tableMeta)) {
                // 单独update header中的 serverId即可.
                doReformat(rle, tableMeta, txnItemRef, context, eventData);
            } else {
                byte[] data = DirectByteOutput.unsafeFetch(eventData.getPayload());
                ByteArray byteArray = new ByteArray(data);
                // 修改serverId
                byteArray.skip(5);
                byteArray.writeLong(context.getServerId(), 4);
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_REFORMAT_NO_FOREIGN_KEY_CHECK)) {
                    int postHeaderLen = FormatDescriptionEvent.EVENT_HEADER_LENGTH[rle.getHeader().getType() - 1];
                    int skipBytes = FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN;
                    if (postHeaderLen == 6) {
                        skipBytes += 4;
                    } else {
                        skipBytes += 6;
                    }
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
            .primaryKey(Lists.newArrayList(reb.getPrimaryKey()))
            .build();
    }

    private void rebuildRowEventBuilder(LogicTableMeta tableMeta, RowEventBuilder reb, TableMapLogEvent table) {
        List<LogicTableMeta.FieldMetaExt> fieldMetas = tableMeta.getLogicFields();
        int newColSize = fieldMetas.size();
        reb.setColumnCount(newColSize);
        List<RowData> rowDataList = reb.getRowDataList();
        BitMap columnBitMap = new BitMap(newColSize);
        reb.setColumnsBitMap(columnBitMap);
        List<RowData> newRowDataList = new ArrayList<>();
        for (RowData rowData : rowDataList) {
            RowData newRowData = new RowData();
            rebuildLogger.logRowBegin(tableMeta, table, rowData, reb.getEventType());
            // 先处理before image
            processBIImage(tableMeta, fieldMetas, table, rowData, newRowData, reb);
            if (reb.isUpdate()) {
                // 处理 after image
                processAIImage(tableMeta, fieldMetas, rowData, newRowData, reb, table);
            }
            if (newColSize < table.getColumnCnt()) {
                rebuildLogger.logRemoveField();
            }
            rebuildLogger.logEnd();

            newRowDataList.add(newRowData);
        }
        if (reb.isUpdate()) {
            resetChangeRowColumnBitMap(fieldMetas, reb);
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
                                TableMapLogEvent table,
                                RowData oldRowData, RowData newRowData, RowEventBuilder reb) {
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
                String charset = fieldMetaExt.getCharset();
                biField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    fieldMetaExt.getDefaultValue(),
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
                    if (!fieldMetaExt.isTypeMatch()) {
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

    private void processAIImage(LogicTableMeta tableMeta, List<LogicTableMeta.FieldMetaExt> fieldMetas,
                                RowData oldRowData, RowData newRowData,
                                RowEventBuilder reb, TableMapLogEvent table) {
        BitMap newAINullBitMap = new BitMap(fieldMetas.size());
        List<Field> newAIFiledList = new ArrayList<>();
        BitMap orgAiChangeBitMap = reb.getColumnsChangeBitMap();
        BitMap orgAiNullBitMap = oldRowData.getAiNullBitMap();
        List<Field> orgFieldList = oldRowData.getAiFieldList();
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();
            if (phyIndex < 0) {
                String charset = fieldMetaExt.getCharset();
                Field aiField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    fieldMetaExt.getDefaultValue(),
                    charset,
                    fieldMetaExt.isNullable(), fieldMetaExt.isUnsigned());
                newAINullBitMap.set(logicIndex, aiField.isNull());
                if (!aiField.isNull()) {
                    newAIFiledList.add(aiField);
                    rebuildLogger.logAddField(fieldMetaExt, aiField.getMysqlType().getType(), aiField.doGetTableMeta(),
                        aiField.encode());
                }
            } else {
                if (orgAiChangeBitMap.get(phyIndex)) {
                    boolean isNull = orgAiNullBitMap.get(phyIndex);

                    if (!isNull) {
                        Field aiField = orgFieldList.get(phyIndex);
                        if (!fieldMetaExt.isTypeMatch()) {
                            aiField = resolveDataTypeNotMatch(aiField, table, fieldMetaExt);
                        }
                        newAINullBitMap.set(logicIndex, aiField.isNull());
                        if (!aiField.isNull()) {
                            newAIFiledList.add(aiField);
                        }
                    } else {
                        newAINullBitMap.set(logicIndex, isNull);
                    }
                }
            }
        }
        newRowData.setAiNullBitMap(newAINullBitMap);
        newRowData.setAiFieldList(newAIFiledList);
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

}

