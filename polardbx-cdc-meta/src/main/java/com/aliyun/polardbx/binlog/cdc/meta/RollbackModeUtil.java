/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;
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
        boolean enableCdcBuildSnapshot = getBoolean(ENABLE_CDC_META_BUILD_SNAPSHOT);
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
