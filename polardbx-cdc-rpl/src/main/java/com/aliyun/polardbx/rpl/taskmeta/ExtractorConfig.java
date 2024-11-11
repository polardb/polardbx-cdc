/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/8 14:30
 * @since 5.0.0.0
 */
@Data
public class ExtractorConfig {
    protected ExtractorType extractorType;
    protected FilterType filterType;
    protected HostInfo hostInfo;
    protected String privateMeta;
    protected int eventBufferSize = 2048;
    protected boolean enableSrcLogicalMetaSnapshot = false;
}
