/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

public class LogFetcherFactory {
    public static URLLogFetcher createURLLogFetcher(String storageInstanceId, String binlogName) {
        return new URLLogFetcher(storageInstanceId, binlogName);
    }
}
