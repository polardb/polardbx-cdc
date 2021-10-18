/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created by ziyang.lb
 **/
public class MergeController {

    private static final Logger logger = LoggerFactory.getLogger(MergeController.class);
    private final PriorityQueue<MergeItem> priorityQueue = new PriorityQueue<>();
    private final Set<String> sourceIds = new HashSet<>();

    public boolean contains(String sourceId) {
        return sourceIds.contains(sourceId);
    }

    public void push(MergeItem item) {
        if (logger.isDebugEnabled()) {
            logger.debug("push item {}", item);
        }

        if (sourceIds.contains(item.getSourceId())) {
            throw new PolardbxException("should not push duplicated item for source " + item.getSourceId());
        }

        priorityQueue.offer(item);
        sourceIds.add(item.getSourceId());
    }

    public MergeItem pop() {
        if (logger.isDebugEnabled()) {
            logger.debug("pop item...");
        }

        MergeItem item = priorityQueue.poll();
        if (logger.isDebugEnabled()) {
            logger.debug("pop item {}", item);
        }

        if (item == null) {
            logger.error("no item exist.");
            return null;
        }

        sourceIds.remove(item.getSourceId());
        return item;
    }
}
