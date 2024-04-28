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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.taskmeta.TaskDistributor;
import org.slf4j.Logger;

/**
 * @author shicai.xsc 2021/1/15 14:20
 * @since 5.0.0.0
 */
public class RplWorkerJob extends AbstractBinlogTimerTask {

    private static Logger metaLogger = LogUtil.getMetaLogger();

    public RplWorkerJob(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        refresh();
    }

    public void refresh() {
        try {
            TaskDistributor.checkAndRunLocalTasks(clusterId);
        } catch (Throwable e) {
            metaLogger.error("RplWorkerJob fail {} {} {}", clusterId, name, interval, e);
            throw new PolardbxException("RplWorkerJob fail", e);
        }
    }
}
