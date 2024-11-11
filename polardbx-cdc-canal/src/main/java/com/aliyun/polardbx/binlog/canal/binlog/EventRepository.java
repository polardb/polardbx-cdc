/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

/**
 * created by ziyang.lb
 */
public interface EventRepository {
    boolean isSupportPersist();

    boolean isForcePersist();

    long persistThreshold();

    void put(String key, byte[] value) throws Throwable;

    byte[] get(String key) throws Throwable;

    void delete(String key) throws Throwable;
}
