/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.CnInstConfigKeys;
import com.aliyun.polardbx.binlog.CnInstConfigUtil;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_RECOVER_FORCE_USE_SNAPSHOT_EXACTLY_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_RECOVER_ROLLBACK_MODE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_EXACTLY;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_SEMI;

/**
 * created by ziyang.lb
 **/
public class RollbackModeUtil {

    public static RollbackMode getRollbackMode() {
        String rollbackModeStr = DynamicApplicationConfig.getString(META_RECOVER_ROLLBACK_MODE);
        RollbackMode mode = RollbackMode.valueOf(rollbackModeStr);
        boolean enableCdcBuildSnapshot = CnInstConfigUtil.getBoolean(CnInstConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT);
        boolean forceUseSnapshotExactly = getBoolean(META_RECOVER_FORCE_USE_SNAPSHOT_EXACTLY_MODE);

        if (mode == RollbackMode.RANDOM) {
            List<RollbackMode> list = Lists.newArrayList(SNAPSHOT_SEMI, SNAPSHOT_EXACTLY);
            Collections.shuffle(list);
            mode = list.get(0);
            return mode;
        } else {
            if (mode == SNAPSHOT_EXACTLY) {
                if (enableCdcBuildSnapshot || forceUseSnapshotExactly) {
                    return mode;
                } else {
                    return SNAPSHOT_SEMI;
                }
            } else {
                return mode;
            }
        }
    }
}
