/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class MessageChunk {
    private long startTimeMills;
    private long startTimeNanos;
    private long totalMemSize;
    private long timeSum;
    private long countSum;
    private final List<MessageEvent> messageEvents;
    private final ChunkMode chunkMode;

    public MessageChunk(ChunkMode chunkMode, int itemSize) {
        this.chunkMode = chunkMode;
        this.messageEvents = new ArrayList<>(itemSize);
    }

    public void addMessageEvent(MessageEvent event) {
        messageEvents.add(event);
        if (messageEvents.size() == 1) {
            startTimeMills = System.currentTimeMillis();
            startTimeNanos = System.nanoTime();
        }
    }

    public void addMemSize(long memSize) {
        totalMemSize += memSize;
    }

    public List<MessageEvent> getMessageEvents() {
        return messageEvents;
    }

    public long getTotalMemSize() {
        return totalMemSize;
    }

    public long getStartTimeMills() {
        return startTimeMills;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public void clear() {
        timeSum += (System.nanoTime() - startTimeNanos);
        countSum++;

        if (log.isDebugEnabled()) {
            log.debug("message chunk process time [{}]", ((double) timeSum) / countSum);
        }

        startTimeMills = 0;
        startTimeNanos = 0;
        totalMemSize = 0;
        messageEvents.clear();
    }
}
