/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import lombok.Data;

/**
 * @author shicai.xsc 2021/2/20 16:06
 * @since 5.0.0.0
 */
@Data
public class StatisticUnit {

    private Long totalConsumeMessageCount;
    private Long avgMergeBatchSize;
    private Long applyRt;
    private Long messageRps;
    private Long applyQps;
    private long skipCounter = 0;
    private long skipExceptionCounter = 0;
    private long persistentMessageCounter = 0;
}
