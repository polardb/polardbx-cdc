/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
    public static final int ROW_FLAG_HAS_HIDDEN_PK = 128;
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
    private int hashKey;
    private List<byte[]> primaryKey;

    public RowEventBuilder(long tableId, int columnCount, BinlogEventType type, int createTime, long serverId) {
        this(tableId, columnCount, type.getType(), createTime, serverId);
    }

    public RowEventBuilder(long tableId, int columnCount, int eventType, int createTime, long serverId) {
        super(createTime, eventType, serverId);
        this.tableId = tableId;
        this.columnCount = columnCount;
        this._flags = ROW_FLAG_END_STATMENT;
    }

    public RowEventBuilder duplicateHeader() {
        RowEventBuilder rowEventBuilder = new RowEventBuilder(tableId, columnCount, eventType, timestamp, serverId);
        rowEventBuilder.set_flags(_flags);
        rowEventBuilder.setColumnsBitMap(columnsBitMap);
        rowEventBuilder.setColumnsChangeBitMap(columnsChangeBitMap);
        return rowEventBuilder;
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
}
