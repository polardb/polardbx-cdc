/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
    private final Set<String> mergeGroupIds = new HashSet<>();

    public boolean contains(String mergeGroupId) {
        return mergeGroupIds.contains(mergeGroupId);
    }

    public void push(MergeItem item) {
        if (logger.isDebugEnabled()) {
            logger.debug("push item {}", item);
        }

        if (mergeGroupIds.contains(item.getMergeGroupId())) {
            throw new PolardbxException("should not push duplicated item for source " + item.getMergeGroupId());
        }

        priorityQueue.offer(item);
        mergeGroupIds.add(item.getMergeGroupId());
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

        mergeGroupIds.remove(item.getMergeGroupId());
        return item;
    }

    public MergeItem peek() {
        return priorityQueue.peek();
    }

    public boolean isEmpty() {
        return mergeGroupIds.isEmpty();
    }

    public int size() {
        return mergeGroupIds.size();
    }
}
