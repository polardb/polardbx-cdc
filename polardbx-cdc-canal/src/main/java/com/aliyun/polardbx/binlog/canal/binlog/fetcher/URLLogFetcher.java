/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

public class URLLogFetcher extends LogFetcher {

    public static final byte[] BINLOG_MAGIC = {-2, 0x62, 0x69, 0x6e};
    private static final Logger logger = LoggerFactory.getLogger(URLLogFetcher.class);
    private InputStream fin;

    private long readPos;

    private String url;

    private int tryTimes = 0;

    private long fileSize = -1;

    private String storageInstanceId;

    private String fileName;

    private ExecutorService executorService;

    public URLLogFetcher(String storageInstanceId, String fileName) {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_GROWTH_FACTOR, storageInstanceId, fileName);
        this.storageInstanceId = storageInstanceId;
        this.fileName = fileName;
    }

    public URLLogFetcher(final int initialCapacity, String storageInstanceId, String fileName) {
        this(initialCapacity, DEFAULT_GROWTH_FACTOR, storageInstanceId, fileName);
    }

    public URLLogFetcher(final int initialCapacity, final float growthFactor, String storageInstanceId,
                         String fileName) {
        super(initialCapacity, growthFactor);
        this.storageInstanceId = storageInstanceId;
        this.fileName = fileName;
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String url, long fileSize, final ExecutorService executor)
        throws FileNotFoundException, IOException {
        open(url, 0L, fileSize, executor);
    }

    public long readSize() {
        return this.readPos;
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String url, final long filePosition, final long fileSize, final ExecutorService executor)
        throws IOException {
        this.url = url;
        this.fileSize = fileSize;
        this.executorService = executor;
        prepareInputStream();

        ensureCapacity(BIN_LOG_HEADER_SIZE);
        // 带上实际读取长度，以区分“流本身为空/提前结束”与“确实读到了非法文件头”，避免合法短读被误报成文件头错误。
        // 此处不能拼 url：OSS 下载链接是预签名地址，带有 OSSAccessKeyId 与 Signature，写入日志会泄露凭证
        int headerReadLen = fin.read(buffer, 0, BIN_LOG_HEADER_SIZE);
        if (BIN_LOG_HEADER_SIZE != headerReadLen) {
            throw new IOException("No binlog file header for file : " + fileName + " , read len : " + headerReadLen);
        }

        if (buffer[0] != BINLOG_MAGIC[0] || buffer[1] != BINLOG_MAGIC[1] || buffer[2] != BINLOG_MAGIC[2]
            || buffer[3] != BINLOG_MAGIC[3]) {
            throw new IOException(
                "Error binlog file header: " + Arrays.toString(Arrays.copyOf(buffer, BIN_LOG_HEADER_SIZE)));
        }

        limit = 0;
        origin = 0;
        position = 0;
        this.readPos = BIN_LOG_HEADER_SIZE;

        if (filePosition > BIN_LOG_HEADER_SIZE) {
            final int maxFormatDescriptionEventLen = FormatDescriptionLogEvent.LOG_EVENT_MINIMAL_HEADER_LEN
                + FormatDescriptionLogEvent.ST_COMMON_HEADER_LEN_OFFSET + LogEvent.ENUM_END_EVENT
                + LogEvent.BINLOG_CHECKSUM_ALG_DESC_LEN + LogEvent.CHECKSUM_CRC32_SIGNATURE_LEN;

            ensureCapacity(maxFormatDescriptionEventLen);
            // limit 必须先记录实际读到的字节数，下面的 getUint32 依赖它做边界检查
            limit = fin.read(buffer, 0, maxFormatDescriptionEventLen);
            // 读到的字节数不足事件头长度时，getUint32 只会抛出语义模糊的 IllegalArgumentException，
            // 这里显式转成带文件名与实际读取长度的 IOException，便于上层按 IO 异常处理并保留诊断信息
            if (limit < LogEvent.EVENT_LEN_OFFSET + 4) {
                throw new IOException("incomplete format description event header for file : " + fileName
                    + " , read len : " + limit);
            }
            limit = (int) getUint32(LogEvent.EVENT_LEN_OFFSET);
            // reset 原因是 skip 包含了上面的读取，所以需要重置
            prepareInputStream();
            skipFully(filePosition);
            this.readPos = filePosition;
        }
    }

    private void prepareInputStream() throws IOException {
        if (fin != null) {
            fin.close();
        }

        fin = MultiPartInputStreamFactory.create(url, this.fileSize, storageInstanceId, fileName, executorService);
    }

    /**
     * 精确跳过 total 个字节。
     * InputStream#skip 的契约允许短跳（返回值小于请求值），而重连后必须精确回到 readPos，
     * 否则已消费过的字节会被再次交付，静默产生重复的 binlog 数据。
     * 这里循环补齐，补不齐时显式失败，把静默的数据重复转换成可见的异常
     */
    private void skipFully(long total) throws IOException {
        long remaining = total;
        while (remaining > 0) {
            long skipped = fin.skip(remaining);
            if (skipped <= 0) {
                throw new IOException(
                    "failed to skip to pos : " + total + " for " + url + " , still remaining : " + remaining);
            }
            remaining -= skipped;
        }
    }

    private int innerRead(int off, int len) throws IOException {
        try {
            int readLen = fin.read(buffer, off, len);
            if (readLen < 0) {
                if (this.readPos < this.fileSize) {
                    logger.warn("url detected readlen : " + readLen + " and read size : " + this.readPos + " != "
                        + this.fileSize + "， will reRead");

                    prepareInputStream();
                    skipFully(this.readPos);
                    readLen = fin.read(buffer, off, len);
                    logger.warn("re read len : " + readLen);
                }
            }
            if (readLen >= 0) {
                // readLen 为 -1 表示已到流末尾，此时不能累加，否则 readPos 会被减 1
                this.readPos += readLen;
                // 读取成功，重置重试计数
                tryTimes = 0;
            }
            return readLen;
        } catch (Exception exception) {
            int maxRetryCount = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_READ_RETRY_COUNT);
            if (!isRetryableNetworkException(exception) || tryTimes++ >= maxRetryCount) {
                // 重试后仍失败或异常不可重试，直接抛异常
                logger.error("read failed for {} at pos : {} , tryTimes : {}", url, readPos, tryTimes, exception);
                if (exception instanceof IOException) {
                    throw (IOException) exception;
                }
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                }
                throw new IOException("offset : " + readPos, exception);
            }
            logger.warn("reconnect to " + url + " with pos : " + readPos, exception);
            // 重连一下
            prepareInputStream();
            skipFully(readPos);
            return innerRead(off, len);
        }

    }

    /**
     * 判断是否为可重连重试的瞬时网络异常。
     * 分段下载模式下异常会被 PartStream 包装成 PolardbxException 抛出，需沿 cause 链回溯识别原始网络异常；
     * SocketTimeoutException 不是 SocketException 的子类，必须单独识别
     */
    private boolean isRetryableNetworkException(Throwable t) {
        Throwable cause = t;
        int depth = 0;
        while (cause != null && depth++ < 8) {
            if (cause instanceof SocketTimeoutException || cause instanceof SocketException
                || cause instanceof EOFException || cause instanceof UnknownHostException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @see LogFetcher#fetch()
     */
    @Override
    public boolean fetch() throws IOException {
        if (limit == 0) {
            final int len = innerRead(0, buffer.length);
            if (len >= 0) {
                limit += len;
                position = 0;
                origin = 0;

                /* More binlog to fetch */
                return true;
            }
        } else if (origin == 0) {
            if (limit > buffer.length / 2) {
                ensureCapacity(buffer.length + limit);
            }
            final int len = innerRead(limit, buffer.length - limit);
            if (len >= 0) {
                limit += len;

                /* More binlog to fetch */
                return true;
            }
        } else if (limit > 0) {
            if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
                int lenPosition = position + 4 + 1 + 4;
                long eventLen =
                    ((long) (0xff & buffer[lenPosition++])) | ((long) (0xff & buffer[lenPosition++]) << 8) | (
                        (long) (0xff & buffer[lenPosition++]) << 16) | ((long) (0xff & buffer[lenPosition++]) << 24);

                if (limit >= eventLen) {
                    return true;
                } else {
                    ensureCapacity((int) eventLen);
                }
            }

            System.arraycopy(buffer, origin, buffer, 0, limit);
            position -= origin;
            origin = 0;
            final int len = innerRead(limit, buffer.length - limit);
            if (len >= 0) {
                limit += len;

                /* More binlog to fetch */
                return true;
            }
        } else {
            /* Should not happen. */
            throw new IllegalArgumentException("Unexcepted limit: " + limit);
        }

        /* Reach binlog file end */
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @see LogFetcher#close()
     */
    @Override
    public void close() throws IOException {
        if (fin != null) {
            fin.close();
        }

        fin = null;
    }
}
