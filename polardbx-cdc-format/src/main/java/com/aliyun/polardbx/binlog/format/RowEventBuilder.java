/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.ROWS_HEADER_LEN_V2;

/**
 * v2
 */
@Data
public class RowEventBuilder extends BinlogBuilder {

    public static final int ROW_FLAG_END_STATMENT = 0x0001;
    public static final int ROW_FLAG_NO_FOREIGN_KEY_CHECK = 0x0002;
    public static final int ROW_FLAG_NO_UNIQUE_KEY_CHECK = 0x0004;
    public static final int ROW_FLAG_HAS_ONE_COLUMN = 0x0008;
    private long tableId;
    private byte[] extraData = new byte[0];
    /**
     * for update bitset
     */
    private BitMap columnsBitMap;
    private BitMap columnsChangeBitMap;
    private List<RowData> rowDataList = new ArrayList<>();
    private int columnCount;
    private int _flags;
    private String commitLog;

    public RowEventBuilder(long tableId, int columnCount, BinlogEventType type, int createTime, long serverId) {
        this(tableId, columnCount, type.getType(), createTime, serverId);
    }

    public RowEventBuilder(long tableId, int columnCount, int eventType, int createTime, long serverId) {
        super(createTime, eventType, serverId);
        this.tableId = tableId;
        this.columnCount = columnCount;
        _flags = ROW_FLAG_END_STATMENT;
    }

    @Override
    protected void writeCommonHeader(AutoExpandBuffer outputData) {
        convertType();
        super.writeCommonHeader(outputData);
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        writeLenencInteger(outputData, columnCount);
        if (columnsBitMap != null) {
            writeBytes(outputData, columnsBitMap.getData());
        }
        if (isUpdate()) {
            writeBytes(outputData, columnsChangeBitMap.getData());
        }

        //rows

        int size = rowDataList.size();
        for (int i = 0; i < size; i++) {
            RowData rowData = rowDataList.get(i);
            writeBytes(outputData, rowData.getBiNullBitMap().getData());
            if (!isEmpty(rowData.getBiFieldList())) {
                for (Field value : rowData.getBiFieldList()) {
                    writeBytes(outputData, value.encode());
                }
            }
            if (isUpdate()) {
                writeBytes(outputData, rowData.getAiNullBitMap().getData());
                for (Field value : rowData.getAiFieldList()) {
                    writeBytes(outputData, value.encode());
                }
            }
        }

    }

    public boolean isUpdate() {
        return eventType == BinlogEventType.UPDATE_ROWS_EVENT.getType()
            || eventType == BinlogEventType.UPDATE_ROWS_EVENT_V1.getType();
    }

    public boolean isInsert() {
        return eventType == BinlogEventType.WRITE_ROWS_EVENT.getType()
            || eventType == BinlogEventType.WRITE_ROWS_EVENT_V1.getType();
    }

    public boolean isDelete() {
        return eventType == BinlogEventType.DELETE_ROWS_EVENT.getType()
            || eventType == BinlogEventType.DELETE_ROWS_EVENT_V1.getType();
    }

    private void convertType() {
        if (isUpdate()) {
            eventType = BinlogEventType.UPDATE_ROWS_EVENT.getType();
            return;
        }
        if (isInsert()) {
            eventType = BinlogEventType.WRITE_ROWS_EVENT.getType();
            return;
        }
        if (isDelete()) {
            eventType = BinlogEventType.DELETE_ROWS_EVENT.getType();
            return;
        }
        throw new UnsupportedOperationException("not support event type : " + eventType);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {
        int postHeaderLen = FormatDescriptionEvent.EVENT_HEADER_LENGTH[eventType - 1];
        if (postHeaderLen == 6) {
            numberToBytes(outputData, tableId, INT32);
        } else {
            numberToBytes(outputData, tableId, 6);
        }
        numberToBytes(outputData, _flags, INT16);
        if (postHeaderLen == ROWS_HEADER_LEN_V2.getLength()) {
            numberToBytes(outputData, extraData.length + 2, INT16);
            writeBytes(outputData, extraData);
        }
    }

    public void addRowData(RowData rowData) {
        this.rowDataList.add(rowData);
    }

    public String getCommitLog() {
        return commitLog;
    }

    public void setCommitLog(String commitLog) {
        this.commitLog = commitLog;
    }
}
