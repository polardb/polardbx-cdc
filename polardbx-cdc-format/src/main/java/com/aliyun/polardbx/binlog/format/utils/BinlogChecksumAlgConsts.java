/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

public class BinlogChecksumAlgConsts {

    /**
     * Events are without checksum though its generator is checksum-capable
     * New Master (NM).
     */
    public static final int BINLOG_CHECKSUM_ALG_OFF = 0;
    /**
     * CRC32 of zlib algorithm
     */
    public static final int BINLOG_CHECKSUM_ALG_CRC32 = 1;
    /**
     * Special value to tag undetermined yet checksum or events from
     * checksum-unaware servers
     */
    public static final int BINLOG_CHECKSUM_ALG_UNDEF = 255;
}
