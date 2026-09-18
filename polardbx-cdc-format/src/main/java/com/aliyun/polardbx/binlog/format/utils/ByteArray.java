/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.UnsupportedEncodingException;

/**
 * Created by ShuGuang
 */
@Slf4j
public class ByteArray {

    protected byte[] data;
    protected int origin;
    protected int pos;
    protected int limit;

    public ByteArray(byte[] data) {
        assert data != null;

        this.data = data;
        this.origin = 0;
        this.pos = 0;
        this.limit = data.length;
    }

    public ByteArray(byte[] data, int offset) {
        this.data = data;
        this.origin = offset;
        this.pos = origin;
        this.limit = data.length;
    }

    public ByteArray(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            throw new IllegalArgumentException("capacity exceed: " + (offset + length));
        }

        this.data = data;
        this.origin = offset;
        this.pos = origin;
        this.limit = offset + length;
    }

    /**
     * Read int written in little-endian format.
     */
    public int readInteger(int length) {
        int result = 0;
        for (int i = 0; i < length; ++i) {
            result |= (this.read() << (i << 3));
        }
        return result;
    }

    public int readInteger(int pos, int length) {
        this.pos = pos;
        return readInteger(length);
    }

    /**
     * Read int<lenenc> written in little-endian format.
     * Format (first-byte-based):<br/>
     * <0xfb - The first byte is the number (in the range 0-250). No additional bytes are used.<br/>
     * 0xfc - Two more bytes are used. The number is in the range 251-0xffff.<br/>
     * 0xfd - Three more bytes are used. The number is in the range 0xffff-0xffffff.<br/>
     * 0xfe - Eight more bytes are used. The number is in the range 0xffffff-0xffffffffffffffff.
     */
    public long readLenenc() {
        int b = this.read();
        if (b < 0xfb) {
            return b;
        } else if (b == 0xfc) {
            /* 251 is reserved for NULL */
            return readInteger(2);
        } else if (b == 0xfd) {
            return readInteger(3);
        } else if (b == 0xfe) {
            return readLong(8);
        } else {
            assert false : b;
        }
        return b;
    }

    /**
     * Read long written in little-endian format.
     */
    public long readLong(int length) {
        long result = 0;
        for (int i = 0; i < length; ++i) {
            result |= (((long) this.read()) << (i << 3));
        }
        return result;
    }

    /**
     * Read fixed length string.
     */
    public String readString(int length) {
        String result = new String(data, pos, length);
        pos += length;
        return result;
    }

    /**
     * Read string with te length provided by this pos
     */
    public String readString() {
        final int len = (0xff & data[pos]);
        int offset = pos + 1;
        String string = new String(data, offset, len);
        pos += len + 1;
        return string;
    }

    /**
     * Read left string.
     */
    public String readEofString(boolean crc32) {
        String result = new String(data, pos, data.length - pos - (crc32 ? 4 : 0));
        pos = data.length - 1;
        return result;
    }

    /**
     * Write long written in little-endian format.
     */
    public void writeLong(long value, int length) {
        for (int i = 0; i < length; ++i) {
            write((byte) ((value >> (i << 3)) & 0xff));
        }
    }

    public void writeLong(int pos, long value, int length) {
        this.pos = pos;
        writeLong(value, length);
    }

    public void writeLongNetStore(long value) {
        int length = netLengthSize(value);
        writeLongNetStore(value, length);
    }

    public void writeLongNetStore(long value, int length) {
        if (length == 1) {
            writeLong(value, length);
        } else if (length == 3) {
            /* 251 is reserved for NULL */
            writeLong(252, 1);
            writeLong(value, length - 1);
        } else if (length == 4) {
            writeLong(253, 1);
            writeLong(value, length - 1);
        } else if (length == 9) {
            writeLong(254, 1);
            writeLong(value, length - 1);
        } else {
            log.error("No such length in net_store_length format");
        }
    }

    /**
     * Write fixed length string, only for ascii code.
     */
    public void writeString(String value) {
        final byte[] bytes = value.getBytes();
        writeString(bytes);
    }

    public void writeString(byte[] bytes) {
        for (byte aByte : bytes) {
            write((byte) (aByte & 0xff));
        }
    }

    public void writeString(int pos, String value) {
        this.pos = pos;
        writeString(value);
    }

    public void read(byte[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = (byte) read();
        }
    }

    public int read() {
        Assert.isTrue(pos < limit);
        return data[pos++] & 0xff;
    }

    public byte readByte() {
        Assert.isTrue(pos < limit);
        return data[pos++];
    }

    public void write(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            write(bytes[i]);
        }
    }

    public void write(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            write(bytes[i]);
        }
    }

    public void write(byte b) {
        Assert.isTrue(pos < limit);
        data[pos++] = b;
    }

    public void writeByte(int pos, byte b) {
        this.pos = pos;
        write(b);
    }

    public void skip(int n) {
        this.pos += n;
    }

    public void setPos(int pos) {
        Assert.isTrue(pos >= origin && pos <= limit);
        this.pos = pos;
    }

    public void reset() {
        this.pos = origin;
    }

    public int getPos() {
        return pos;
    }

    public byte[] getData() {
        return data;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * mysql 的长度计算公式
     *
     * @return long, the length of value
     */
    public static int netLengthSize(long value) {
        if (value < 251L) {
            return 1;
        } else if (value < 65536L) {
            return 3;
        } else if (value < 16777216L) {
            return 4;
        } else {
            return 9;
        }
    }
}
