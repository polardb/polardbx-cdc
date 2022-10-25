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
package com.aliyun.polardbx.binlog.canal.core;

import java.math.BigInteger;

/**
 * bufferPool对外提供统一的buffer创建流程，使用共享byte环形数组
 * 因为binlog顺序消费场景，最先申请的buffer一定会被优先使用完，所以环形数组环形无碎片使用。
 *
 * @author chengjin.lyf on 2020/7/14 8:09 下午
 * @since 1.0.25
 */
public class ByteBufferPool {

    /* The max ulonglong - 0x ff ff ff ff ff ff ff ff */
    public static final BigInteger BIGINT_MAX_VALUE = new BigInteger("18446744073709551615");
    public static final long NULL_LENGTH = ((long) ~0);
    /* default ANSI charset */
    public static final String ISO_8859_1 = "ISO-8859-1";
    /* decimal representation */
    public static final int DIG_PER_DEC1 = 9;
    public static final int DIG_BASE = 1000000000;
    public static final int DIG_MAX = DIG_BASE - 1;
    public static final int dig2bytes[] = {0, 1, 1, 2, 2, 3, 3, 4, 4, 4};
    public static final int powers10[] = {
        1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000,
        1000000000};
    public static final int DIG_PER_INT32 = 9;
    public static final int SIZE_OF_INT32 = 4;

    private byte[] buffer;

    private long begin = 0;
    private long end = 0;

    public ByteBufferPool(int size) {
        buffer = new byte[size];
    }

    private long allocateMem(int size) {
        long _e = end;
        end += size;
        return _e;
    }

    public ByteBuffer allocateBuffer(int allocateSize) {
        if (ensureCapacity(allocateSize)) {
            return null;
        }
        ByteBuffer byteBuffer = new ByteBuffer(allocateMem(allocateSize), 0, allocateSize);
        return byteBuffer;
    }

    private void needReset() {

    }

    private boolean ensureCapacity(int size) {
        return buffer.length + begin - end > size;
    }

    public class ByteBuffer {

        long origin;
        /**
         * 相对
         */
        int position;
        int limit;

        public ByteBuffer(long start, int position, int limit) {
            this.origin = start;
            this.position = position;
            this.limit = limit;
        }

        public boolean ensureCapacity(int newLimit) {
            if (limit - position < newLimit) {
                if (ByteBufferPool.this.ensureCapacity(newLimit)) {
                    ByteBufferPool.this.allocateMem(newLimit);
                    limit += newLimit;
                    return true;
                }
                return false;
            }
            return true;
        }

    }

}
