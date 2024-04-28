/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
