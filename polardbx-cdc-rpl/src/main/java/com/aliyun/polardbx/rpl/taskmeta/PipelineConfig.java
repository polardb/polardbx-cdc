/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Data;

/**
 * @author shicai.xsc 2020/11/30 15:55
 * @since 5.0.0.0
 */
@Data
public class PipelineConfig {

    private int consumerParallelCount = 32;
    private int bufferSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_RINGBUFFER_SIZE);
    private boolean supportXa = false;
    private int fixedTpsLimit = -1;
    private boolean useIncValidation = false;
    private boolean skipException = false;
    private boolean safeMode = false;
    private PersistConfig persistConfig = new PersistConfig();
    private int applyRetryMaxTime = 5;
    private long retryIntervalMs = 1000;
}
