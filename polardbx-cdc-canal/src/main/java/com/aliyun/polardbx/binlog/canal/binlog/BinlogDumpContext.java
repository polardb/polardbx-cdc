/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

public class BinlogDumpContext {

    private static ThreadLocal<DumpStage> stageLocal = new ThreadLocal<>();

    public static void setDumpStage(DumpStage stage) {
        stageLocal.set(stage);
    }

    public static String getStage() {
        return String.valueOf(stageLocal.get());
    }

    public static boolean isSearch() {
        return stageLocal.get() == DumpStage.STAGE_SEARCH;
    }

    public static boolean isDump() {
        return stageLocal.get() == DumpStage.STAGE_DUMP;
    }

    public static enum DumpStage {
        STAGE_SEARCH, STAGE_DUMP
    }
}
