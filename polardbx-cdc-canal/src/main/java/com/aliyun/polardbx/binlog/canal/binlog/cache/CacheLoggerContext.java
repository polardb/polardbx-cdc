/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

public class CacheLoggerContext {
    private static final ThreadLocal<String> storageInstanceId = new ThreadLocal<>();
    private static final ThreadLocal<String> fileName = new ThreadLocal<>();
    private static final ThreadLocal<Integer> seq = new ThreadLocal<>();
    private static final ThreadLocal<String> uuid = new ThreadLocal<>();

    public static void setStorageInstanceId(String storageInstanceId) {
        CacheLoggerContext.storageInstanceId.set(storageInstanceId);
    }

    public static void setFileName(String fileName) {
        CacheLoggerContext.fileName.set(fileName);
    }

    public static void setSeq(Integer seq) {
        CacheLoggerContext.seq.set(seq);
    }

    public static String getStorageInstanceId() {
        return CacheLoggerContext.storageInstanceId.get();
    }

    public static String getUuid() {
        return CacheLoggerContext.uuid.get();
    }

    public static void setUuid(String uuid) {
        CacheLoggerContext.uuid.set(uuid);
    }

    public static String getFileName() {
        return CacheLoggerContext.fileName.get();
    }

    public static Integer getSeq() {
        return CacheLoggerContext.seq.get();
    }

}
