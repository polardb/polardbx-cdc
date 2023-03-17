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
package com.aliyun.polardbx.binlog.remote.channel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * @author yudong
 * @since 2022/9/14
 **/
public class RemoteBinlogFileReadBuffer {
    private final ByteBuffer buffer;
    private final InputStream inputStream;
    private static final int DEFAULT_CAPACITY = 8192;

    public RemoteBinlogFileReadBuffer(InputStream in, int cap) {
        inputStream = in;
        buffer = ByteBuffer.allocate(cap);
        buffer.flip();
    }

    public RemoteBinlogFileReadBuffer(InputStream inputStream) {
        this(inputStream, DEFAULT_CAPACITY);
    }

    /**
     * attention: 不保证能把dst读满，所以上层可能需要调用多次read才能把dst读满
     * read data from buffer into dst
     * @param dst destination buffer
     * @return number of bytes read, -1 if reach end of the file
     */
    public int read(ByteBuffer dst) throws IOException {
        if (!buffer.hasRemaining()) {
            if (fill() < 0) {
                return -1;
            }
        }
        int readSize = Math.min(dst.remaining(), buffer.remaining());
        for (int i = 0; i < readSize; i++) {
            dst.put(buffer.get());
        }
        return readSize;
    }

    private int fill() throws IOException {
        assert buffer.remaining() == 0;
        int readLen = inputStream.read(buffer.array());
        if (readLen < 0) {
            return -1;
        }
        buffer.clear();
        buffer.limit(readLen);
        return readLen;
    }
}
