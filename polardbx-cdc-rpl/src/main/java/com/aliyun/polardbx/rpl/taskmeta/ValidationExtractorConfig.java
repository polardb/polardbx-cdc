/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * Validation extractor configuration
 *
 * @author siyu.yusi
 */
@Data
public class ValidationExtractorConfig extends ExtractorConfig {
    private int validateBatchSize;
    private int parallelCount;
    private boolean convertToByte = false;
}
