/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import java.util.BitSet;

public class BitMap {

    private byte[] data;
    private int size;

    public BitMap(int size) {
        this.data = new byte[(size + 7) / 8];
        this.size = size;
    }

    public BitMap(int size, boolean defaultValue) {
        this(size);
        if (defaultValue) {
            for (int i = 0; i < size; i++) {
                set(i, true);
            }
        }
    }

    public BitMap(int size, BitSet bitSet) {
        this(size);
        set(bitSet);
    }

    public BitMap(int size, BitMap org) {
        this(size);
        int maxSize = Math.min(data.length, org.data.length);
        System.arraycopy(org.data, 0, data, 0, maxSize);
    }

    public BitMap(BitMap org) {
        data = new byte[org.data.length];
        this.size = org.size;
        System.arraycopy(org.data, 0, data, 0, data.length);
    }

    public static void main(String[] args) {
        BitMap map = new BitMap(4);
        map.set(1, true);
        map.set(3, true);
        System.out.println(map.get(0));
        System.out.println(map.get(1));
        System.out.println(map.get(2));
        System.out.println(map.get(3));
    }

    public void set(BitSet bitSet) {
        byte[] b = bitSet.toByteArray();
        System.arraycopy(b, 0, data, 0, b.length);
    }

    public void set(int i, boolean v) {
        int idx = i / 8;
        if (v) {
            data[idx] |= (1 << (i % 8));
        } else {
            data[idx] &= ~(1 << (i % 8));
        }
    }

    public boolean get(int i) {
        byte mask = (byte) (1 << (i % 8));
        return (data[i / 8] & mask) == mask;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < size; i++) {
            sb.append(get(i)).append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}
