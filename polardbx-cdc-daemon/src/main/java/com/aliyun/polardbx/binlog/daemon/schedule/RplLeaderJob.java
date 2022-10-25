/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import org.slf4j.Logger;

/**
 * @author shicai.xsc 2021/1/15 14:19
 * @since 5.0.0.0
 */
public class RplLeaderJob extends AbstractBinlogTimerTask {

    private static Logger metaLogger = LogUtil.getMetaLogger();

    public RplLeaderJob(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        try {
            metaLogger.info("try to elect leader");
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                metaLogger.info("NOT a leader");
                return;
            }

            FSMManager.update();
            FSMMetaManager.distributeTasks();

        } catch (Throwable th) {
            metaLogger.error("RplLeaderJob fail {} {} {}", clusterId, name, interval, th);
            throw new PolardbxException("RplLeaderJob fail", th);
        }
    }
}
