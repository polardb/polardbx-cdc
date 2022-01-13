/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.NullField;
import com.aliyun.polardbx.binlog.format.field.SimpleField;
import com.aliyun.polardbx.binlog.format.utils.BitMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * @author yanfenglin
 */
public class RowsLogEventRebuilder {

    public static RowEventBuilder convert(RowsLogEvent rowsLogEvent, long serverId) {

        LogHeader lg = rowsLogEvent.getHeader();

        int columnLen = rowsLogEvent.getColumnLen();
        // prepare prop
        RowEventBuilder rowEvent = new RowEventBuilder((int) rowsLogEvent.getTableId(),
            rowsLogEvent.getColumnLen(),
            rowsLogEvent.getHeader().getType(),
            (int) rowsLogEvent.getWhen(),
            serverId);
        rowEvent.setTimestamp((int) lg.getWhen());
        rowEvent.setFlags((short) lg.getFlags());
        rowEvent.setColumnCount(columnLen);
        rowEvent.set_flags(rowsLogEvent.getFlags());
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
        }
        return rowEvent;
    }

    private static void extractField(int columnLen, TableMapLogEvent.ColumnInfo[] columnInfos,
                                     BitSet columnChangeBitSet, RowsLogBuffer logBuffer, List<Field> fieldList) {
        for (int i = 0; i < columnLen; i++) {
            if (columnChangeBitSet.get(i)) {
                fieldList.add(new NullField());
                continue;
            }
            TableMapLogEvent.ColumnInfo columnInfo = columnInfos[i];
            byte[] data = logBuffer.fetchBinaryValue(columnInfo.type, columnInfo.meta);
            Field field = new SimpleField(data, columnInfo.type, columnInfo.meta);
            fieldList.add(field);
        }
    }
}
