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
