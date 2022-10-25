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
package com.aliyun.polardbx.binlog.format.utils;

public class AutoExpandBuffer {

    private byte data[];
    private int pos;
    private int stepBuffer;
    private long totalSize;
    private long counts;

    public AutoExpandBuffer(int capacity, int stepBuffer) {
        data = new byte[capacity];
        this.pos = 0;
        this.stepBuffer = stepBuffer;
    }

    public void reset() {
        counts++;
        totalSize += pos;
        int averageSize = (int) (totalSize / counts);
        pos = 0;
        if (Math.abs(averageSize - data.length) > stepBuffer) {
            data = new byte[averageSize];
        }
    }

    private void ensureCapacity(int needLength) {
        while (data.length - pos < needLength) {
            int addLength = needLength / stepBuffer + 1;
            byte[] newData = new byte[data.length + addLength * stepBuffer];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }
    }

    public void put(byte[] b) {
        ensureCapacity(b.length);
        System.arraycopy(b, 0, data, pos, b.length);
        pos += b.length;
    }

    public void put(byte[] b, int len) {
        ensureCapacity(len);
        System.arraycopy(b, 0, data, pos, len);
        pos += len;
    }

    public void putInt(int v, int pos) {
        ensureCapacity(4);
        for (int i = 0; i < 4; i++) {
            data[pos++] = (byte) ((v >> (8 * i)) & 0xFF);
        }
    }

    public void putShort(int v, int pos) {
        ensureCapacity(2);
        for (int i = 0; i < 2; i++) {
            data[pos++] = (byte) ((v >> (8 * i)) & 0xFF);
        }
    }

    public void put(byte b) {
        ensureCapacity(1);
        data[pos++] = b;
    }

    public int size() {
        return pos;
    }

    public int position() {
        return pos;
    }

    public byte[] toBytes() {
        return data;
    }
}
