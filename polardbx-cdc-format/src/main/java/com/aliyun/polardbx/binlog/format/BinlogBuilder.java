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

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import lombok.Data;

import java.io.UnsupportedEncodingException;

@Data
public abstract class BinlogBuilder {

    public static final String ISO_8859_1 = "ISO-8859-1";
    protected static final int EVENT_TYPE_OFFSET = 4;
    protected static final int SERVER_ID_OFFSET = 5;
    protected static final int EVENT_LEN_OFFSET = 9;
    protected static final int LOG_POS_OFFSET = 13;
    protected static final int FLAGS_OFFSET = 17;
    /**
     * 将事件写入日志后，导致二进制日志内部的表映射版本增加。
     * 猜测应该是tableId 增加
     */
    protected static final int LOG_EVENT_UPDATE_TABLE_MAP_VERSION_F = 0x10; //（5.1.4中的新增功能）
    protected static final int INT64 = 8;
    protected static final int INT32 = 4;
    protected static final int INT16 = 2;
    protected static final int INT8 = 1;
    protected static final int BYTE_BIT_COUNT = 8;
    /**
     * 通用header长度
     */
    private static long _2p16 = 1L << 16;
    private static long _2p24 = 1L << 24;
    private static long _2p64 = 1L << 64;

    /**
     * event header begin
     * This is the time at which the statement began executing.
     * It is represented as the number of seconds since 1970 (UTC), like the TIMESTAMP SQL data type.
     **/
    protected int timestamp;
    /**
     * seconds since unix epoch
     */
    protected int eventType;
    protected long serverId;

    /**
     * event header end
     **/
    /**
     * size of the event (header, post-header, body)
     * Most events are less than 1000 bytes
     */
    protected int eventSize;
    /**
     * position of the next event
     */
    protected long nextPosition;
    /**
     * see Binlog Event Flag
     * LOG_EVENT_BINLOG_IN_USE_F { https://dev.mysql.com/doc/internals/en/binlog-event-flag.html}
     * gets unset in the FORMAT_DESCRIPTION_EVENT when the file gets closed to detect broken binlogs
     * LOG_EVENT_FORCED_ROTATE_F
     * unused
     * LOG_EVENT_THREAD_SPECIFIC_F
     * event is thread specific (CREATE TEMPORARY TABLE ...)
     * LOG_EVENT_SUPPRESS_USE_F
     * event doesn't need default database to be updated (CREATE DATABASE, ...)
     * LOG_EVENT_UPDATE_TABLE_MAP_VERSION_F
     * Causes the table map version internal to the binary log to be increased after the event has been written to the log.
     * unused
     * LOG_EVENT_ARTIFICIAL_F
     * event is created by the slaves SQL-thread and shouldn't update the master-log pos
     * LOG_EVENT_RELAY_LOG_F
     * event is created by the slaves IO-thread when written to the relay log
     */
    protected short flags;

    protected int startPos;

    public BinlogBuilder() {
    }

    public BinlogBuilder(int timestamp, int eventType, long serverId) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.serverId = serverId;
    }

    /**
     * An integer that consumes 1, 3, 4, or 9 bytes, depending on its numeric value
     * To convert a number value into a length-encoded integer:
     * If the value is < 251, it is stored as a 1-byte integer.
     * If the value is ≥ 251 and < (2^16), it is stored as fc + 2-byte integer.
     * If the value is ≥ (2^16) and < (2^24), it is stored as fd + 3-byte integer.
     * If the value is ≥ (2^24) and < (2^64) it is stored as fe + 8-byte integer.
     */
    protected void writeLenencInteger(AutoExpandBuffer outputData, long value) {
        if (value < 251) {
            numberToBytes(outputData, value, INT8);
        } else if (value >= 251 && value < _2p16) {
            outputData.put((byte) 0xFC);
            numberToBytes(outputData, value, INT16);
        } else if (value >= _2p16 && value < _2p24) {
            outputData.put((byte) 0xFD);
            numberToBytes(outputData, value, 3);
        } else if (value >= _2p24) {
            outputData.put((byte) 0xFE);
            numberToBytes(outputData, value, 8);
        }
    }

    protected void numberToBytes(AutoExpandBuffer outputData, long value, int byteSize) {
        for (int i = 0; i < byteSize; i++) {
            outputData.put((byte) ((value >> (BYTE_BIT_COUNT * i)) & 0xFF));
        }
    }

    protected int getStringLength(String value) throws UnsupportedEncodingException {
        return value.getBytes(ISO_8859_1).length;
    }

    protected void writeFixString(AutoExpandBuffer outputData, String value, int len, String charset) throws Exception {
        byte[] bytes = value.getBytes(charset);
        int lastLen = len - bytes.length;
        if (lastLen <= 0) {
            throw new ArrayIndexOutOfBoundsException(String.format("length is %d, try to write string size is %d",
                len,
                bytes.length));
        }
        outputData.put(bytes);
        for (int i = 0; i < lastLen; i++) {
            outputData.put((byte) '\0');
        }
    }

    protected void writeString(AutoExpandBuffer outputData, String value, String charset, boolean insertStringLength)
        throws UnsupportedEncodingException {
        byte[] bytes = value.getBytes(charset);
        if (insertStringLength) {
            numberToBytes(outputData, bytes.length, INT8);
        }
        outputData.put(bytes);
    }

    protected void writeBytes(AutoExpandBuffer outputData, byte[] value) {
        outputData.put(value);
    }

    protected void writeBytes(AutoExpandBuffer outputData, byte[] value, int len) {
        outputData.put(value, len);
    }

    /**
     * 输出payload 到output
     * The variable part of the event data can differ in size among events of a given type.
     */
    protected abstract void writePayload(AutoExpandBuffer outputData) throws Exception;

    /**
     * 输出postHeader
     * The fixed part of the event data is the same size for all events of a given type.
     */
    protected abstract void writePostHeader(AutoExpandBuffer outputData) throws Exception;

    /**
     * header
     * +============================+
     * | timestamp         0 : 4    |
     * +----------------------------+
     * | type_code         4 : 1    |
     * +----------------------------+
     * | server_id         5 : 4    |
     * +----------------------------+
     * | event_length      9 : 4    |
     * +----------------------------+
     * | next_position    13 : 4    |
     * +----------------------------+
     * | flags            17 : 2    |
     * +----------------------------+
     * | extra_headers    19 : x-19 |
     * +============================+
     */
    public int write(AutoExpandBuffer buffer) throws Exception {
        startPos = buffer.position();
        writeEvent(buffer);
        writeCrc32(buffer);
        resetEventSize(buffer);
        return eventSize;
    }

    protected void writeCrc32(AutoExpandBuffer outputData) {
        numberToBytes(outputData, 0, INT32);
    }

    protected void writeEvent(AutoExpandBuffer outputData) throws Exception {
        writeCommonHeader(outputData);
        writePostHeader(outputData);
        writePayload(outputData);
    }

    private void resetEventSize(AutoExpandBuffer outputData) {
        eventSize = outputData.size() - startPos;
        // 回填event size
        outputData.putInt(eventSize, EVENT_LEN_OFFSET + startPos);
    }

    protected void writeCommonHeader(AutoExpandBuffer outputData) {
        numberToBytes(outputData, timestamp, INT32);
        numberToBytes(outputData, eventType, INT8);
        numberToBytes(outputData, serverId, INT32);
        // occupation event size pos
        numberToBytes(outputData, eventSize, INT32);
        // binlog-version > 1
        numberToBytes(outputData, nextPosition, INT32);
        numberToBytes(outputData, flags, INT16);
    }
}
