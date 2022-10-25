/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
