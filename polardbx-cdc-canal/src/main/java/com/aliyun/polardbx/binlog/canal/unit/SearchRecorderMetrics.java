/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.unit;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SearchRecorderMetrics {

    private static final Logger logger = LoggerFactory.getLogger(SearchRecorderMetrics.class);
    @Getter
    private static Map<Long, SearchRecorder> searchRecorderMap = new ConcurrentHashMap<>();

    public static void addSearchRecorder(SearchRecorder searchRecorder) {
        if (searchRecorderMap.put(searchRecorder.getTid(), searchRecorder) != null) {
            logger.error("duplicate add search recorder for " + searchRecorder.getStorageName() + ", tid : "
                + Thread.currentThread().getId() + "@" + Thread.currentThread().getName());
        }
    }

    public static void reset() {
        searchRecorderMap.clear();
    }

    public static boolean isEmpty() {
        return searchRecorderMap.isEmpty();
    }

    public static boolean allFinish() {
        for (SearchRecorder recorder : searchRecorderMap.values()) {
            if (!recorder.isFinish()) {
                return false;
            }
        }
        return true;
    }

}
