/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

/**
 * created by ziyang.lb
 **/
public interface StoreEngine {
    void append(WriteItem writeItem);

    void open();

    void close();

    String seekMaxTso();

    void clean(String tso);

    void setOriginStartTso(String tso);

    String getOriginStartTso();

    void setMaxReadKey(byte[] key);

    String getMaxCleanTso();

    RelayDataReader newRelayDataReader(byte[] beginKey);

    LockingCleaner getLockingCleaner();
}
