/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import java.util.List;

import com.aliyun.polardbx.rpl.common.RplConstants;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/8 14:32
 * @since 5.0.0.0
 */
@Data
public class FullExtractorConfig extends ExtractorConfig {
    private int fetchBatchSize = RplConstants.DEFAULT_FETCH_BATCH_SIZE;
    private int parallelCount = RplConstants.PRODUCER_DEFAULT_PARALLEL_COUNT;

    public void mergeFrom(FullExtractorConfig other) {
        this.fetchBatchSize = other.fetchBatchSize;
        this.parallelCount = other.parallelCount;
    }
}
