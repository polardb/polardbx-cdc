/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.message;

import com.lmax.disruptor.EventFactory;

/**
 *
 **/
public class MessageEventFactory implements EventFactory<MessageEvent> {

    @Override
    public MessageEvent newInstance() {
        return new MessageEvent();
    }
}
