/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.constants;

import lombok.Getter;

/**
 * Enumeration specifying checksum algorithm used to encode a binary log event
 *
 * @author yudong
 * @since 2023/12/20 17:40
 **/
@Getter
public enum EnumBinlogChecksumAlg {
    BINLOG_CHECKSUM_ALG_OFF(0, "NONE"),
    BINLOG_CHECKSUM_ALG_CRC32(1, "CRC32"),
    BINLOG_CHECKSUM_ALG_UNDEF(255, "UNDEF");

    private final int value;
    private final String name;

    EnumBinlogChecksumAlg(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public static EnumBinlogChecksumAlg fromName(String name) {
        for (EnumBinlogChecksumAlg alg : EnumBinlogChecksumAlg.values()) {
            if (alg.name.equals(name)) {
                return alg;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
