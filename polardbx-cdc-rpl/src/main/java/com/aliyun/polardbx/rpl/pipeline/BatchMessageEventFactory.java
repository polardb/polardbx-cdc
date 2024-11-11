/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.lmax.disruptor.EventFactory;

public class BatchMessageEventFactory implements EventFactory<BatchMessageEvent> {
    @Override
    public BatchMessageEvent newInstance() {
        return new BatchMessageEvent();
    }
}
