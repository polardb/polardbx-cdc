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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.NullField;
import com.aliyun.polardbx.binlog.format.field.SimpleField;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.google.common.collect.Lists;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author yanfenglin
 */
public class RowsLogEventRebuilder {

    public static List<RowEventBuilder> convert(RowsLogEvent rowsLogEvent, LogicTableMeta tableMeta, long serverId,
                                                boolean split,
                                                boolean extractPk) throws IOException {
        List<LogicTableMeta.FieldMetaExt> fieldMetaExtList = tableMeta.getLogicFields();
        List<RowEventBuilder> rowEventBuilderList = Lists.newArrayList();
        LogHeader lg = rowsLogEvent.getHeader();

        int columnLen = rowsLogEvent.getColumnLen();
        // prepare prop
        RowEventBuilder rowEvent = new RowEventBuilder(rowsLogEvent.getTableId(),
            rowsLogEvent.getColumnLen(),
            rowsLogEvent.getHeader().getType(),
            (int) rowsLogEvent.getWhen(),
            serverId);
        rowEvent.setTimestamp((int) lg.getWhen());
        rowEvent.setFlags((short) 0);
        rowEvent.set_flags(rowsLogEvent.getFlags());
        rowEvent.setColumnCount(columnLen);
        rowEvent.setEventType(lg.getType());

        TableMapLogEvent table = rowsLogEvent.getTable();
        TableMapLogEvent.ColumnInfo[] columnInfos = table.getColumnInfo();
        // prepare column
        BitSet columnbitSet = rowsLogEvent.getColumns();
        BitSet columnChangeBitSet = rowsLogEvent.getChangeColumns();
        rowEvent.setColumnsBitMap(new BitMap(columnLen, columnbitSet));
        rowEvent.setColumnsChangeBitMap(new BitMap(columnLen, columnChangeBitSet));
        RowsLogBuffer logBuffer = rowsLogEvent.getRowsBuf("utf8");
        while (logBuffer.nextOneRow(columnbitSet)) {
            RowData rowData = new RowData();
            rowData.setBiNullBitMap(new BitMap(columnLen, logBuffer.getNullBits()));
            List<Field> fieldList = new ArrayList<>();
            extractField(columnLen, columnInfos, logBuffer.getNullBits(), logBuffer, fieldList);
            rowData.setBiFieldList(fieldList);
            if (lg.getType() == LogEvent.UPDATE_ROWS_EVENT || lg.getType() == LogEvent.UPDATE_ROWS_EVENT_V1) {
                fieldList = new ArrayList<>();
                if (logBuffer.nextOneRow(columnChangeBitSet)) {
                    rowData.setAiNullBitMap(new BitMap(columnLen, logBuffer.getNullBits()));
                    extractField(columnLen, columnInfos, logBuffer.getNullBits(), logBuffer, fieldList);
                    rowData.setAiFieldList(fieldList);
                }
            }
            rowEvent.addRowData(rowData);
            if (split) {
                rowEvent.set_flags(0);
                rowEventBuilderList.add(rowEvent);
                rowEvent = rowEvent.duplicateHeader();
            }
        }
        if (rowEventBuilderList.isEmpty()) {
            rowEventBuilderList.add(rowEvent);
        }

        if (extractPk) {
            rowEventBuilderList = splitAndExtract(rowEventBuilderList, tableMeta.getPkList());
        }

        return rowEventBuilderList;
    }

    private static void extractField(int columnLen, TableMapLogEvent.ColumnInfo[] columnInfos,
                                     BitSet nullBitSet, RowsLogBuffer logBuffer, List<Field> fieldList) {
        for (int i = 0; i < columnLen; i++) {
            if (nullBitSet.get(i)) {
                fieldList.add(NullField.INSTANCE);
                continue;
            }
            TableMapLogEvent.ColumnInfo columnInfo = columnInfos[i];
            byte[] data = logBuffer.fetchBinaryValue(columnInfo.type, columnInfo.meta);
            Field field = new SimpleField(data, columnInfo.type, columnInfo.meta);
            fieldList.add(field);
        }
    }

    /**
     * 提取key数据
     * update => delete + insert
     */
    private static List<RowEventBuilder> splitAndExtract(List<RowEventBuilder> rebuilderList,
                                                         List<LogicTableMeta.FieldMetaExt> keyFieldList)
        throws IOException {
        Iterator<RowEventBuilder> it = rebuilderList.iterator();
        List<RowEventBuilder> returnBuilderList = Lists.newArrayList();
        while (it.hasNext()) {
            RowEventBuilder reb = it.next();
            if (reb.getEventType() == LogEvent.UPDATE_ROWS_EVENT
                || reb.getEventType() == LogEvent.UPDATE_ROWS_EVENT_V1) {
                // delete + insert
                if (isPkChange(reb, keyFieldList)) {
                    updateToDeleteAndInsert(reb, returnBuilderList);
                } else {
                    returnBuilderList.add(reb);
                }
            } else {
                returnBuilderList.add(reb);
            }
        }
        Iterator<RowEventBuilder> returnIt = returnBuilderList.iterator();
        while (returnIt.hasNext()) {
            RowEventBuilder reb = returnIt.next();
            extractPk(reb, keyFieldList);
        }
        return returnBuilderList;
    }

    private static boolean isPkChange(RowEventBuilder reb, List<LogicTableMeta.FieldMetaExt> keyFieldList) {
        for (LogicTableMeta.FieldMetaExt fieldMetaExt : keyFieldList) {
            RowData rowData = reb.getRowDataList().get(0);
            if (!rowData.getAiFieldList().get(fieldMetaExt.getPhyIndex())
                .equals(rowData.getBiFieldList().get(fieldMetaExt.getPhyIndex()))) {
                return true;
            }
        }
        return false;
    }

    private static void extractPk(RowEventBuilder eventBuilder, List<LogicTableMeta.FieldMetaExt> keyFieldList)
        throws IOException {
        if (keyFieldList.isEmpty()) {
            eventBuilder.setHashKey(0);
            eventBuilder.setPrimaryKey(new ArrayList<>());
        } else {
            List<byte[]> primaryKeyList = new ArrayList<>();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            for (LogicTableMeta.FieldMetaExt fieldMetaExt : keyFieldList) {
                RowData rowData = eventBuilder.getRowDataList().get(0);
                SimpleField sf = (SimpleField) rowData.getBiFieldList().get(fieldMetaExt.getPhyIndex());
                baos.write(sf.getData());
                primaryKeyList.add(sf.decode().toString().getBytes());
            }
            byte[] bytes = baos.toByteArray();
            eventBuilder.setHashKey(Arrays.hashCode(bytes));
            eventBuilder.setPrimaryKey(primaryKeyList);
        }
    }

    private static void updateToDeleteAndInsert(RowEventBuilder updateEvent, List<RowEventBuilder> returnBuilderList) {
        RowEventBuilder delete = updateEvent.duplicateHeader();
        delete.setEventType(LogEvent.DELETE_ROWS_EVENT);
        RowEventBuilder insert = updateEvent.duplicateHeader();
        insert.setEventType(LogEvent.WRITE_ROWS_EVENT);
        RowData rowData = updateEvent.getRowDataList().get(0);
        RowData biRowData = new RowData();
        RowData aiData = new RowData();
        biRowData.setBiNullBitMap(rowData.getBiNullBitMap());
        biRowData.setBiFieldList(rowData.getBiFieldList());
        delete.addRowData(biRowData);

        aiData.setBiNullBitMap(rowData.getAiNullBitMap());
        aiData.setBiFieldList(rowData.getAiFieldList());
        insert.addRowData(aiData);

        returnBuilderList.add(delete);
        returnBuilderList.add(insert);
    }
}
