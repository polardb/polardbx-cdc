/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.enums.CompressionType;

import static com.aliyun.polardbx.binlog.enums.TransactionPayloadFiled.OTW_PAYLOAD_COMPRESSION_TYPE_FIELD;
import static com.aliyun.polardbx.binlog.enums.TransactionPayloadFiled.OTW_PAYLOAD_HEADER_END_MARK;
import static com.aliyun.polardbx.binlog.enums.TransactionPayloadFiled.OTW_PAYLOAD_SIZE_FIELD;
import static com.aliyun.polardbx.binlog.enums.TransactionPayloadFiled.OTW_PAYLOAD_UNCOMPRESSED_SIZE_FIELD;

public class TransactionPayloadLogEvent extends LogEvent {

    public static final short COMPRESSION_TYPE_MIN_LENGTH = 1;
    public static final short COMPRESSION_TYPE_MAX_LENGTH = 9;
    public static final short PAYLOAD_SIZE_MIN_LENGTH = 0;
    public static final short PAYLOAD_SIZE_MAX_LENGTH = 9;
    public static final short UNCOMPRESSED_SIZE_MIN_LENGTH = 0;
    public static final short UNCOMPRESSED_SIZE_MAX_LENGTH = 9;
    public static final int MAX_DATA_LENGTH = COMPRESSION_TYPE_MAX_LENGTH
        + PAYLOAD_SIZE_MAX_LENGTH
        + UNCOMPRESSED_SIZE_MAX_LENGTH;

    /**
     * Marks the end of the payload header.
     */
    public static final int OTW_PAYLOAD_HEADER_END_MARK = 0;

    /**
     * The payload field
     */
    public static final int OTW_PAYLOAD_SIZE_FIELD = 1;

    /**
     * The compression type field
     */
    public static final int OTW_PAYLOAD_COMPRESSION_TYPE_FIELD = 2;

    /**
     * The uncompressed size field
     */
    public static final int OTW_PAYLOAD_UNCOMPRESSED_SIZE_FIELD = 3;

    /* ZSTD compression. */
    public final static int COMPRESS_TYPE_ZSTD = 0;
    /* No compression. */
    public final static int COMPRESS_TYPE_NONE = 255;
    private final String info = "compression='%s', decompressed_size=%d bytes";

    private CompressionType m_compression_type = CompressionType.NONE;
    private long m_payload_size;
    private long m_uncompressed_size;
    private byte[] m_payload;

    public TransactionPayloadLogEvent(LogHeader header, LogBuffer buffer,
                                      FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.getCommonHeaderLen();

        int offset = commonHeaderLen;
        buffer.position(offset);
        long type = 0, length = 0;
        while (buffer.hasRemaining()) {
            type = buffer.getPackedLong();
            if (type == OTW_PAYLOAD_HEADER_END_MARK) {
                break;
            }

            length = buffer.getPackedLong();
            switch ((int) type) {
            case OTW_PAYLOAD_SIZE_FIELD:
                m_payload_size = buffer.getPackedLong();
                break;
            case OTW_PAYLOAD_COMPRESSION_TYPE_FIELD:
                m_compression_type = CompressionType.fromValue(buffer.getPackedLong());
                break;
            case OTW_PAYLOAD_UNCOMPRESSED_SIZE_FIELD:
                m_uncompressed_size = buffer.getPackedLong();
                break;
            default:
                buffer.forward((int) length);
                break;
            }
        }

        if (m_uncompressed_size == 0) {
            m_uncompressed_size = m_payload_size;
        }
        m_payload = buffer.getData((int) m_payload_size);
    }

    public boolean isCompressByZstd() {
        return m_compression_type == CompressionType.ZSTD;
    }

    public boolean isCompressByNone() {
        return m_compression_type == CompressionType.NONE;
    }

    public byte[] getPayload() {
        return m_payload;
    }

    @Override
    public String info() {
        return String.format(info, m_compression_type.toString(), m_uncompressed_size);
    }
}
