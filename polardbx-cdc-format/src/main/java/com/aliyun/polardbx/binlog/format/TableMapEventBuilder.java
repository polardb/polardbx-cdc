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
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Data
public class TableMapEventBuilder extends BinlogBuilder {

    /**
     * Fixed header length, where 4.x and 5.0 agree. That is, 5.0 may have a longer
     * header (it will for sure when we have the unique event's ID), but at least
     * the first 19 bytes are the same in 4.x and 5.0. So when we have the unique
     * event's ID, LOG_EVENT_HEADER_LEN will be something like 26, but
     * LOG_EVENT_MINIMAL_HEADER_LEN will remain 19.
     */
    private static final int MAX_NAME_LENGTH = 128;
    /**
     * The table ID.
     */
    private long tableId;
    /**
     * Reserved for future use.
     */
    private int _flags;
    private String schema;
    private String tableName;
    private byte[][] columnMetaData;
    private byte[] columnDefType;
    /**
     * nullable column
     */
    private BitMap nullBitmap;

    private List<Field> fieldList;

    private String charSet;

    public TableMapEventBuilder(int timestamp, long serverId, long tableId, String schema, String tableName,
                                String charSet) {
        super(timestamp, BinlogEventType.TABLE_MAP_EVENT.getType(), serverId);
        this.tableId = tableId;
        this.schema = schema;
        this.tableName = tableName;
        this.charSet = charSet;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        writeString(outputData, schema, StringUtils.isNotBlank(charSet) ? charSet : ISO_8859_1, true);
        numberToBytes(outputData, 0, INT8);
        writeString(outputData, tableName, StringUtils.isNotBlank(charSet) ? charSet : ISO_8859_1, true);
        numberToBytes(outputData, 0, INT8);
        if (fieldList != null) {
            writeLenencInteger(outputData, fieldList.size());
            for (Field f : fieldList) {
                numberToBytes(outputData, f.getMysqlType().getType(), INT8);
            }
            byte[] metaBlock = new byte[fieldList.size() * 2];
            int pos = 0;
            for (Field f : fieldList) {
                byte[] metas = f.doGetTableMeta();
                for (int i = 0; i < metas.length; i++) {
                    metaBlock[pos++] = metas[i];
                }
            }
            writeLenencInteger(outputData, pos);
            writeBytes(outputData, metaBlock, pos);
            nullBitmap = new BitMap(fieldList.size());

            for (int i = 0; i < fieldList.size(); i++) {
                Field f = fieldList.get(i);
                if (!f.isNullable()) {
                    nullBitmap.set(i, true);
                }

            }
            writeBytes(outputData, nullBitmap.getData());
        } else {
            writeLenencInteger(outputData, columnDefType.length);
            writeBytes(outputData, columnDefType);

            int metLen = 0;

            for (byte[] def : columnMetaData) {
                metLen += def.length;
            }
            writeLenencInteger(outputData, metLen);
            for (byte[] def : columnMetaData) {
                writeBytes(outputData, def);
            }
            writeBytes(outputData, nullBitmap.getData());

        }

    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {
        if (FormatDescriptionEvent.EVENT_HEADER_LENGTH[BinlogEventType.TABLE_MAP_EVENT.getType() - 1] == 6) {
            numberToBytes(outputData, tableId, INT32);
        } else {
            numberToBytes(outputData, tableId, 6);
        }
        numberToBytes(outputData, _flags, INT16);
    }
}
