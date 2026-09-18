/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.github.luben.zstd.ZstdInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * <pre>
 * FileLogFetcher fetcher = new FileLogFetcher();
 * fetcher.open(file, 0);
 *
 * while (fetcher.fetch()) {
 *     LogEvent event;
 *     do {
 *         event = decoder.decode(fetcher, context);
 *
 *         // process log event.
 *     } while (event != null);
 * }
 * // file ending reached.
 * </pre>
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class FileLogFetcher extends LogFetcher {

    public static final byte[] BINLOG_MAGIC = {-2, 0x62, 0x69, 0x6e};

    /**
     * zstd 压缩文件的 magic number（0x28 0xB5 0x2F 0xFD，对应有符号字节 40,-75,47,-3）。
     * 下载下来的 binlog 文件可能是 zstd 压缩格式，通过该文件头识别后进行解压。
     */
    private static final byte[] ZSTD_MAGIC = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};

    private FileInputStream fin;

    /**
     * 实际用于读取的输入流。普通 binlog 文件时等于 {@link #fin}；
     * 当文件为 zstd 压缩格式时，为包裹了 {@link ZstdInputStream} 的解压流。
     */
    private InputStream in;

    public FileLogFetcher() {
        super(DEFAULT_INITIAL_CAPACITY, DEFAULT_GROWTH_FACTOR);
    }

    public FileLogFetcher(final int initialCapacity) {
        super(initialCapacity, DEFAULT_GROWTH_FACTOR);
    }

    public FileLogFetcher(final int initialCapacity, final float growthFactor) {
        super(initialCapacity, growthFactor);
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(File file) throws FileNotFoundException, IOException {
        open(file, 0L);
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String filePath) throws FileNotFoundException, IOException {
        open(new File(filePath), 0L);
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String filePath, final long filePosition) throws FileNotFoundException, IOException {
        open(new File(filePath), filePosition);
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(File file, final long filePosition) throws FileNotFoundException, IOException {
        fin = new FileInputStream(file);
        in = fin;

        ensureCapacity(BIN_LOG_HEADER_SIZE);
        if (BIN_LOG_HEADER_SIZE != in.read(buffer, 0, BIN_LOG_HEADER_SIZE)) {
            throw new IOException("No binlog file header");
        }

        // 下载下来的 binlog 可能是 zstd 压缩文件（magic number 0x28B52FFD），
        // 直接按原始 binlog 读取会导致文件头校验失败。这里参考 MultiPartInputStreamFactory 的做法，
        // 识别到 zstd 文件头后用 ZstdInputStream 对输入流进行解压装饰，再重新读取真正的 binlog 文件头。
        final boolean compressed = isZstdCompressed(buffer);
        if (compressed) {
            in.close();
            in = openZstdStream(file);
            if (BIN_LOG_HEADER_SIZE != in.read(buffer, 0, BIN_LOG_HEADER_SIZE)) {
                throw new IOException("No binlog file header");
            }
        }

        if (buffer[0] != BINLOG_MAGIC[0] || buffer[1] != BINLOG_MAGIC[1] || buffer[2] != BINLOG_MAGIC[2]
            || buffer[3] != BINLOG_MAGIC[3]) {
            throw new IOException("Error binlog file header: "
                + Arrays.toString(Arrays.copyOf(buffer, BIN_LOG_HEADER_SIZE)));
        }

        limit = 0;
        origin = 0;
        position = 0;

        if (filePosition > BIN_LOG_HEADER_SIZE) {
            final int maxFormatDescriptionEventLen = FormatDescriptionLogEvent.LOG_EVENT_MINIMAL_HEADER_LEN
                + FormatDescriptionLogEvent.ST_COMMON_HEADER_LEN_OFFSET
                + LogEvent.ENUM_END_EVENT + LogEvent.BINLOG_CHECKSUM_ALG_DESC_LEN
                + LogEvent.CHECKSUM_CRC32_SIGNATURE_LEN;

            ensureCapacity(maxFormatDescriptionEventLen);
            limit = in.read(buffer, 0, maxFormatDescriptionEventLen);
            limit = (int) getUint32(LogEvent.EVENT_LEN_OFFSET);
            if (compressed) {
                // zstd 解压流不支持基于 FileChannel 的随机定位，重新打开并跳过到指定位点。
                in.close();
                in = openZstdStream(file);
                skipFully(in, filePosition);
            } else {
                fin.getChannel().position(filePosition);
            }
        }
    }

    /**
     * 以 zstd 解压流方式重新打开文件，并同步更新 {@link #fin} 字段。
     * 若 {@link ZstdInputStream} 构造失败，确保底层 {@link FileInputStream} 被关闭，避免文件句柄泄漏。
     *
     * @param file 待打开的本地 binlog 文件
     * @return 包裹底层文件流的 zstd 解压流
     */
    private InputStream openZstdStream(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            InputStream zis = new ZstdInputStream(fis);
            fin = fis;
            return zis;
        } catch (IOException | RuntimeException e) {
            try {
                fis.close();
            } catch (IOException ignore) {
                // 关闭失败无需处理，优先抛出构造阶段的原始异常
            }
            throw e;
        }
    }

    /**
     * 判断给定文件头是否为 zstd magic number。
     *
     * @param header 至少包含 {@link #BIN_LOG_HEADER_SIZE} 个字节的文件头
     * @return 若为 zstd 压缩文件返回 true
     */
    private static boolean isZstdCompressed(byte[] header) {
        return header[0] == ZSTD_MAGIC[0] && header[1] == ZSTD_MAGIC[1]
            && header[2] == ZSTD_MAGIC[2] && header[3] == ZSTD_MAGIC[3];
    }

    /**
     * 从输入流中确定性地跳过指定字节数，单次 skip 不足时循环补齐，直到跳完或流结束。
     *
     * @param input 待跳过的输入流
     * @param bytesToSkip 需要跳过的字节数
     */
    private static void skipFully(InputStream input, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    break;
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see LogFetcher#fetch()
     */
    public boolean fetch() throws IOException {
        if (limit == 0) {
            final int len = in.read(buffer, 0, buffer.length);
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
            final int len = in.read(buffer, limit, buffer.length - limit);
            if (len >= 0) {
                limit += len;

                /* More binlog to fetch */
                return true;
            }
        } else if (limit > 0) {
            if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
                int lenPosition = position + 4 + 1 + 4;
                long eventLen = ((long) (0xff & buffer[lenPosition++])) | ((long) (0xff & buffer[lenPosition++]) << 8)
                    | ((long) (0xff & buffer[lenPosition++]) << 16)
                    | ((long) (0xff & buffer[lenPosition++]) << 24);

                if (limit >= eventLen) {
                    return true;
                } else {
                    ensureCapacity((int) eventLen);
                }
            }

            System.arraycopy(buffer, origin, buffer, 0, limit);
            position -= origin;
            origin = 0;
            final int len = in.read(buffer, limit, buffer.length - limit);
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
    public void close() throws IOException {
        if (in != null) {
            in.close();
        }

        in = null;
        fin = null;
    }
}
