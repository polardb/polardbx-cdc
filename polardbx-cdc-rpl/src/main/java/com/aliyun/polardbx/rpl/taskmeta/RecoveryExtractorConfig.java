/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

import java.util.List;

/**
 * @author yudong
 */
@Data
public class RecoveryExtractorConfig extends ExtractorConfig {
    private List<String> binlogList;
}
