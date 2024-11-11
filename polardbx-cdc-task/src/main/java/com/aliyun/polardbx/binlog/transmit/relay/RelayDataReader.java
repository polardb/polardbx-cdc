/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * created by ziyang.lb
 */
public interface RelayDataReader {
    List<Pair<byte[], byte[]>> getData(int maxItemSize, long maxByteSize);

    void close();
}
