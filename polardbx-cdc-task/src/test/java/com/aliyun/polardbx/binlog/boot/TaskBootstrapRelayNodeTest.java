/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.boot;

import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;

import java.util.ArrayList;
import java.util.List;

public class TaskBootstrapRelayNodeTest {

    // 根据自己的测试需求进行调整
    private static final String START_TSO = "";

    public static void main(String args[]) {
        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider("relay-task") {

            @Override
            public TaskRuntimeConfig getTaskRuntimeConfig() {
                TaskRuntimeConfig taskRuntimeConfig = new TaskRuntimeConfig();
                taskRuntimeConfig.setId(2L);
                taskRuntimeConfig.setName("task relay test");
                taskRuntimeConfig.setType(TaskType.Relay);
                taskRuntimeConfig.setStartTSO(START_TSO);
                taskRuntimeConfig.setServerPort(8913);
                taskRuntimeConfig.setMergeSourceInfos(buildMergeSources());
                return taskRuntimeConfig;
            }

            private List<MergeSourceInfo> buildMergeSources() {
                List<MergeSourceInfo> list = new ArrayList<>();
                for (int i = 0; i < 1; i++) {
                    MergeSourceInfo info = new MergeSourceInfo();
                    info.setId(String.valueOf(i));
                    info.setType(MergeSourceType.BINLOG);
                    info.setQueueSize(1024);
                    info.setBinlogParameter(buildBinlogParameter());
                    list.add(info);
                }
                return list;
            }

            private BinlogParameter buildBinlogParameter() {
                BinlogParameter parameter = new BinlogParameter();
                DnHost hostVO = DnHost.getNormalDnHost("polardbx-storage-0-master");
                parameter.setDoDbFilter("tso_test_\\d+");
                parameter.setStorageInstId("polardbx-storage-0-master");
                parameter.setHasTsoHeartbeat(false);
                return parameter;
            }
        });
        bootStrap.boot(new String[] {"taskName=relay-task"});
    }
}
