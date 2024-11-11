/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AutoExpandBuffer {

    private byte[] data;
    private final int incSize;
    private long totalSize;
    private long counts;

    private ByteBuffer buffer;

    private ByteOrder order = ByteOrder.LITTLE_ENDIAN;

    public AutoExpandBuffer(int capacity, int incSize) {
        data = new byte[capacity];
        this.incSize = incSize;
        this.initBuffer();
    }

    private void initBuffer() {
        int orderPosition = 0;
        if (this.buffer != null) {
            orderPosition = this.buffer.position();
        }
        this.buffer = ByteBuffer.wrap(data);
        this.buffer.order(order);
        this.buffer.position(orderPosition);
    }

    public void order(ByteOrder order) {
        this.order = order;
    }

    public void reset() {
        counts++;
        totalSize += this.buffer.position();
        int averageSize = (int) (totalSize / counts);
        this.buffer.position(0);
        if (Math.abs(averageSize - data.length) > incSize) {
            data = new byte[averageSize];
            initBuffer();
        }
    }

    private void ensureCapacity(int needLength) {
        while (this.buffer.remaining() < needLength) {
            int addLength = needLength / incSize + 1;
            byte[] newData = new byte[data.length + addLength * incSize];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
            initBuffer();
        }
    }

    public void put(byte[] b) {
        ensureCapacity(b.length);
        this.buffer.put(b);
    }

    public void put(byte[] b, int len) {
        ensureCapacity(len);
        this.buffer.put(b, 0, len);
    }

    public void putInt(int pos, int v) {
        ensureCapacity(Integer.BYTES);
        this.buffer.putInt(pos, v);
    }

    public void putDouble(double v) {
        ensureCapacity(Double.BYTES);
        this.buffer.putDouble(v);
    }

    public void putInt(int v) {
        ensureCapacity(Integer.BYTES);
        this.buffer.putInt(v);
    }

    public void putShort(int pos, int v) {
        ensureCapacity(Short.BYTES);
        this.buffer.putShort(pos, (short) v);
    }

    public void putShort(short v) {
        ensureCapacity(Short.BYTES);
        this.buffer.putShort(v);
    }

    public void put(byte b) {
        ensureCapacity(1);
        this.buffer.put(b);
    }

    public void put(int pos, byte b) {
        ensureCapacity(1);
        this.buffer.put(pos, b);
    }

    public void putLong(long v) {
        ensureCapacity(Long.BYTES);
        this.buffer.putLong(v);
    }

    public int size() {
        return this.buffer.position();
    }

    public int position() {
        return this.buffer.position();
    }

    public void writeTo(byte[] target) {
        if (target.length < this.buffer.position()) {
            throw new IllegalArgumentException("target length is less than buffer length");
        }
        System.arraycopy(data, 0, target, 0, this.buffer.position());
    }

    public byte[] toBytes() {
        return data;
    }
}
