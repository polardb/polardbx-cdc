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

package com.aliyun.polardbx.binlog.dumper.dump.util;

/**
 * Created by ShuGuang
 */
public class ByteArray {

    private final byte[] data;
    private int origin;
    private int pos;
    private int limit;

    public ByteArray(byte[] data) {
        assert data != null;

        this.data = data;
        this.origin = 0;
        this.pos = 0;
        this.limit = data.length;
    }

    public ByteArray(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            throw new IllegalArgumentException("capacity exceed: " + (offset + length));
        }

        this.data = data;
        this.origin = offset;
        this.pos = origin;
        this.limit = length;
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

    /**
     * Write fixed length string.
     */
    public void writeString(String value, int length) {
        final byte[] bytes = value.getBytes();
        for (int i = 0; i < length && i < bytes.length; ++i) {
            write((byte) (bytes[i] & 0xff));
        }
        if (length > bytes.length) {
            skip(length - bytes.length);
        }
    }

    public int read() {
        assert pos < data.length;
        return data[pos++] & 0xff;
    }

    public void write(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            write(bytes[i]);
        }
    }

    public void write(byte b) {
        assert pos < data.length;
        data[pos++] = b;
    }

    public void skip(int n) {
        this.pos += n;
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
}
