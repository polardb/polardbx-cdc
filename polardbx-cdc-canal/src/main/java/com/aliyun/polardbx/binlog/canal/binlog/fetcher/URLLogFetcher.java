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
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;

public class URLLogFetcher extends LogFetcher {

    public static final byte[] BINLOG_MAGIC = {-2, 0x62, 0x69, 0x6e};
    private static final Logger logger = LoggerFactory.getLogger(URLLogFetcher.class);
    private MultiPartInputStream fin;

    private long readPos;

    private String url;

    private int tryTimes = 0;

    private long fileSize = -1;

    public URLLogFetcher() {
        super(DEFAULT_INITIAL_CAPACITY, DEFAULT_GROWTH_FACTOR);
    }

    public URLLogFetcher(final int initialCapacity) {
        super(initialCapacity, DEFAULT_GROWTH_FACTOR);
    }

    public URLLogFetcher(final int initialCapacity, final float growthFactor) {
        super(initialCapacity, growthFactor);
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String url, long fileSize) throws FileNotFoundException, IOException {
        open(url, 0L, fileSize);
    }

    public long readSize() {
        return this.readPos;
    }

    /**
     * Open binlog file in local disk to fetch.
     */
    public void open(String url, final long filePosition, final long fileSize) throws IOException {
        this.url = url;
        this.fileSize = fileSize;
        prepareInputStream();

        ensureCapacity(BIN_LOG_HEADER_SIZE);
        if (BIN_LOG_HEADER_SIZE != fin.read(buffer, 0, BIN_LOG_HEADER_SIZE)) {
            throw new IOException("No binlog file header");
        }

        if (buffer[0] != BINLOG_MAGIC[0] || buffer[1] != BINLOG_MAGIC[1] || buffer[2] != BINLOG_MAGIC[2]
            || buffer[3] != BINLOG_MAGIC[3]) {
            throw new IOException("Error binlog file header: "
                + Arrays.toString(Arrays.copyOf(buffer, BIN_LOG_HEADER_SIZE)));
        }

        limit = 0;
        origin = 0;
        position = 0;
        this.readPos = BIN_LOG_HEADER_SIZE;

        if (filePosition > BIN_LOG_HEADER_SIZE) {
            final int maxFormatDescriptionEventLen = FormatDescriptionLogEvent.LOG_EVENT_MINIMAL_HEADER_LEN
                + FormatDescriptionLogEvent.ST_COMMON_HEADER_LEN_OFFSET
                + LogEvent.ENUM_END_EVENT + LogEvent.BINLOG_CHECKSUM_ALG_DESC_LEN
                + LogEvent.CHECKSUM_CRC32_SIGNATURE_LEN;

            ensureCapacity(maxFormatDescriptionEventLen);
            limit = fin.read(buffer, 0, maxFormatDescriptionEventLen);
            limit = (int) getUint32(LogEvent.EVENT_LEN_OFFSET);
            prepareInputStream();
            fin.skip(filePosition);
            this.readPos = filePosition;
        }
    }

    private void prepareInputStream() throws IOException {
        if (fin != null) {
            fin.close();
        }

        fin = new MultiPartInputStream(url, this.fileSize);
    }

    private int innerRead(int off, int len) throws IOException {
        try {
            int readLen = fin.read(buffer, off, len);
            if (readLen < 0) {
                if (this.readPos < this.fileSize) {
                    logger.warn("url detected readlen : " + readLen + " and read size : " + this.readPos + " != "
                        + this.fileSize + "， will reRead");

                    prepareInputStream();
                    fin.skip(this.readPos);
                    readLen = fin.read(buffer, off, len);
                    logger.warn("re read len : " + readLen);
                }
            }
            this.readPos += readLen;
            return readLen;
        } catch (SocketException exception) {
            if (tryTimes++ > 1) {
                // 重试一次还是失败，直接抛异常
                throw new IOException("offset : " + readPos, exception);
            }
            logger.warn("reconnect to " + url + " with pos : " + readPos);
            // 重连一下
            prepareInputStream();
            fin.skip(readPos);
            return innerRead(off, len);
        }

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
