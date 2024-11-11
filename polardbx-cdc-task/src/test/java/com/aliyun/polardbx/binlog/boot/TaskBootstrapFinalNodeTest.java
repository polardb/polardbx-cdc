/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
