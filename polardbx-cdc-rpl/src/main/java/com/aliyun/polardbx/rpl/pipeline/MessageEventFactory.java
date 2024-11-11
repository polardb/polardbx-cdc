/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.lmax.disruptor.EventFactory;

/**
 * @author shicai.xsc 2020/11/30 16:03
 * @since 5.0.0.0
 */
public class MessageEventFactory implements EventFactory<MessageEvent> {

    @Override
    public MessageEvent newInstance() {
        return new MessageEvent();
    }
}
