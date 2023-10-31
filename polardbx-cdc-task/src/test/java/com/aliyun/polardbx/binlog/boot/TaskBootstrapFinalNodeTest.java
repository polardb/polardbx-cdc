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
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.RpcParameter;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;

import java.util.ArrayList;
import java.util.List;

public class TaskBootstrapFinalNodeTest {

    // 根据自己的测试需求进行调整
    private static final String START_TSO = "";

    public static void main(String args[]) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider("final-task") {

            @Override
            public TaskRuntimeConfig getTaskRuntimeConfig() {
                TaskRuntimeConfig taskRuntimeConfig = new TaskRuntimeConfig();
                taskRuntimeConfig.setId(1L);
                taskRuntimeConfig.setName("task final test");
                taskRuntimeConfig.setType(TaskType.Final);
                taskRuntimeConfig.setStartTSO(START_TSO);
                taskRuntimeConfig.setServerPort(8914);
                taskRuntimeConfig.setMergeSourceInfos(buildMergeSources());
                return taskRuntimeConfig;
            }

            private List<MergeSourceInfo> buildMergeSources() {
                List<MergeSourceInfo> list = new ArrayList<>();
                for (int i = 0; i < 1; i++) {
                    MergeSourceInfo info = new MergeSourceInfo();
                    info.setId(String.valueOf(i));
                    info.setType(MergeSourceType.RPC);
                    info.setQueueSize(1024);
                    info.setRpcParameter(buildRpcParameter());
                    list.add(info);
                }
                return list;
            }

            private RpcParameter buildRpcParameter() {
                RpcParameter parameter = new RpcParameter();
                parameter.setAddress("127.0.0.1");
                parameter.setPort(8913);
                return parameter;
            }
        });
        bootStrap.boot(new String[] {"taskName=final-task"});
    }
}
