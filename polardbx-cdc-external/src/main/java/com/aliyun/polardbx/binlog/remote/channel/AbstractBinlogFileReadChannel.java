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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractInterruptibleChannel;

/**
 * @author yudong
 * @since 2022/10/19
 **/
@Slf4j
public abstract class AbstractBinlogFileReadChannel extends AbstractInterruptibleChannel {
    protected long fileSize = -1;
    protected long position = 0;
    protected InputStream inputStream;
    protected RemoteBinlogFileReadBuffer readBuffer;

    /**
     * 给remote server发送请求，获得从start position开始的文件内容
     *
     * @param startPosition 文件开始位置
     */
    protected abstract void getRange(long startPosition);

    public final int read(ByteBuffer dst, long startPosition) throws IOException {
        getRange(startPosition);
        return readHelper(dst);
    }

    public final int read(ByteBuffer dst) throws IOException {
        if (dst.remaining() == 0) {
            if (log.isDebugEnabled()) {
                log.debug("dst has no remain space!");
            }
            return 0;
        }

        if (position == 0) {
            getRange(4L);
        }

        return readHelper(dst);
    }

    public final long position() {
        return position;
    }

    /**
     * todo @yudong 亟待优化， 如果newPosition > position, 调用skip方法
     */
    public final void position(long newPosition) throws IOException {
        getRange(newPosition);
    }

    /**
     * todo @yudong 优化, 避免调用getRange函数
     */
    public final long size() throws IOException {
        if (fileSize == -1) {
            getRange(4L);
        }
        return fileSize;
    }

    private int readHelper(ByteBuffer dst) throws IOException {
        int total = 0;
        while (dst.remaining() > 0) {
            int readLen = readBuffer.read(dst);
            if (readLen == -1) {
                return total > 0 ? total : -1;
            }
            total += readLen;
            position += readLen;
        }
        return total;
    }
}
