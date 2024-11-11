/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.Data;

/**
 * @author shicai.xsc 2020/12/1 11:06
 * @since 5.0.0.0
 */
@Data
public class ApplierConfig {
    protected int mergeBatchSize = 200;
    // 不再支持多语句！transactionEventBatchSize用于transaction写入时的事务合并的size上限
    protected int transactionEventBatchSize = 100;
    protected int logCommitLevel = RplConstants.LOG_NO_COMMIT;
    protected boolean enableDdl = true;
    protected int maxPoolSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_MAX_POOL_SIZE);
    protected int minPoolSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_MIN_POOL_SIZE);
    protected int statisticIntervalSec = 5;
    protected ApplierType applierType;
    protected HostInfo hostInfo;
    protected boolean compareAll;
    protected boolean insertOnUpdateMiss;
    protected ConflictStrategy conflictStrategy;
    protected long fullCopyFinishTimeStamp = -1;
}
