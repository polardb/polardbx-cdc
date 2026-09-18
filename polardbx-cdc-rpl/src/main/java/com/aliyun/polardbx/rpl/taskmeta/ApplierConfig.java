/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * @author shicai.xsc 2020/12/1 11:06
 * @since 5.0.0.0
 */
@Data
public class ApplierConfig {
    protected int mergeBatchSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_BATCH_SIZE);
    // 单次批量DML（INSERT/DELETE/UPDATE）合并的最大行数
    protected int dmlBatchSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_DML_BATCH_SIZE);
    // 不再支持多语句！transactionEventBatchSize用于transaction写入时的事务合并的size上限
    protected int transactionEventBatchSize = 100;
    protected int logCommitLevel = RplConstants.LOG_NO_COMMIT;
    protected boolean enableDdl = true;
    protected int maxPoolSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_MAX_POOL_SIZE);
    protected int minPoolSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_INC_MIN_POOL_SIZE);
    protected int statisticIntervalSec = 5;
    protected ApplierType applierType;
    protected HostInfo hostInfo;
    protected boolean insertOnUpdateMiss;
    protected ConflictStrategy conflictStrategy;
    protected long fullCopyFinishTimeStamp = -1;
    protected Map<String, Set<String>> filterColumns;
    protected Map<String, String> customizedUsingUkAsPkTables;
    private boolean ddlOnlyAddColumn = false;
    // 仅用于 MERGE（UPDATE 已转换为 DELETE+INSERT）和 FULL_COPY（只有 INSERT）。
    // 其他 applier 不允许开启，避免 UPDATE SQL 对缺失列产生歧义。
    private boolean skipMismatchedColumns = false;
}
