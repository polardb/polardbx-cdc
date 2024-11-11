/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.channel;

import com.aliyun.oss.OSSException;
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

    public final int read(ByteBuffer dst, long startPosition) {
        getRange(startPosition);

        try {
            return readHelper(dst);
        } catch (IOException e) {
            log.error("read from remote error!", e);
            throw new OSSException("Read from oss error!");
        }
    }

    public final int read(ByteBuffer dst) {
        if (dst.remaining() == 0) {
            if (log.isDebugEnabled()) {
                log.debug("dst has no remain space!");
            }
            return 0;
        }

        if (position == 0) {
            getRange(4L);
        }

        try {
            return readHelper(dst);
        } catch (IOException e) {
            log.error("read from remote error!", e);
            throw new OSSException("Read from oss error!");
        }
    }

    public final long position() {
        return position;
    }

    public final void position(long newPosition) throws IOException {
        getRange(newPosition);
    }

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
