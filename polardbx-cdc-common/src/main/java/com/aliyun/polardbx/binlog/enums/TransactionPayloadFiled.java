/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author zm
 */
public enum TransactionPayloadFiled {
    /**
     * Marks the end of the payload header.
     */
    OTW_PAYLOAD_HEADER_END_MARK(0),

    /**
     * The payload field
     */
    OTW_PAYLOAD_SIZE_FIELD(1),

    /**
     * The compression type field
     */
    OTW_PAYLOAD_COMPRESSION_TYPE_FIELD(2),

    /**
     * The uncompressed size field
     */
    OTW_PAYLOAD_UNCOMPRESSED_SIZE_FIELD(3);

    private final int value;

    TransactionPayloadFiled(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
