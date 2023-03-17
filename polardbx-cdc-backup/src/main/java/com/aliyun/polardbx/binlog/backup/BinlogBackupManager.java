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
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * @author yudong
 * @since 2023/1/11
 *
 * 负责将本地的binlog上传到远端存储
 */
@Slf4j
public class BinlogBackupManager {
    private BinlogUploadManager binlogUploadManager;
    private final String binlogRootPath;
    private final String taskName;
    private final TaskType taskType;
    private final String group;
    private final List<String> streamList;
    private final Map<String, MetricsObserver> metricsObserverMap;

    public BinlogBackupManager(String binlogRootPath, String taskName,
                               TaskType taskType, String group, List<String> streamList,
                               Map<String, MetricsObserver> metricsObserverMap) {
        this.binlogRootPath = binlogRootPath;
        this.taskName = taskName;
        this.taskType = taskType;
        this.group = group;
        this.streamList = streamList;
        this.metricsObserverMap = metricsObserverMap;
    }

    public void start() {
        log.info("binlog backup manager start");
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            if (taskType == TaskType.Dumper || taskType == TaskType.DumperX) {
                binlogUploadManager =
                    new BinlogUploadManager(binlogRootPath, taskName, group, streamList, metricsObserverMap);
                binlogUploadManager.start();
            } else {
                throw new PolardbxException("invalid task type " + taskType);
            }
        }

    }

    public void stop() {
        log.info("binlog backup manager stop");
        if (binlogUploadManager != null) {
            binlogUploadManager.shutdown();
        }
    }
}
