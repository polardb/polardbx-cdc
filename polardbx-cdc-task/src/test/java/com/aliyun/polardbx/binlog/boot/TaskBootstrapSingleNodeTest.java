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
package com.aliyun.polardbx.binlog.boot;

import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;

import java.util.ArrayList;
import java.util.List;

/**
 *
 **/
public class TaskBootstrapSingleNodeTest {

    // 根据自己的测试需求进行调整
    private static final String START_TSO = "";

    public static void main(String args[]) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider("Final") {

            @Override
            public TaskRuntimeConfig getTaskRuntimeConfig() {
                TaskRuntimeConfig taskRuntimeConfig = new TaskRuntimeConfig();
                taskRuntimeConfig.setId(3L);
                taskRuntimeConfig.setName("Final");
                taskRuntimeConfig.setType(TaskType.Final);
                taskRuntimeConfig.setStartTSO(START_TSO);
                taskRuntimeConfig.setServerPort(8912);
                taskRuntimeConfig.setMergeSourceInfos(buildMergeSources());
                return taskRuntimeConfig;
            }

            private List<MergeSourceInfo> buildMergeSources() {
                List<MergeSourceInfo> list = new ArrayList<>();
                String storageInstanceList[] = {"polardbx-storage-0-master", "polardbx-storage-1-master"};
                for (int i = 0; i < 2; i++) {
                    MergeSourceInfo info = new MergeSourceInfo();
                    info.setId(String.valueOf(i));
                    info.setType(MergeSourceType.BINLOG);
                    info.setQueueSize(1024);
                    info.setBinlogParameter(buildBinlogParameter(storageInstanceList[i]));
                    list.add(info);
                }
                return list;
            }

            private BinlogParameter buildBinlogParameter(String storageInstId) {
                BinlogParameter parameter = new BinlogParameter();
                parameter.setStorageInstId(storageInstId);
                parameter.setHasTsoHeartbeat(true);
                return parameter;
            }
        });

        bootStrap.boot(new String[] {"taskName=Final"});
    }
}
