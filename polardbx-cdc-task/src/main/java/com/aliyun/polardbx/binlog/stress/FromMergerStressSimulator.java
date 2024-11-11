/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.stress;

import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.MockParameter;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class FromMergerStressSimulator extends BaseStressSimulator {

    private static final String DEFAULT_TXN_TYPE = "1";
    private static final String DEFAULT_SOURCE_QUEUE_SIZE = "1024";
    private static final String DEFAULT_SOURCE_COUNT = "4";
    private static final String DEFAULT_DML_COUNT_PER_SOURCE = "1";
    private static final String DEFAULT_EVENT_SIZE = "0";
    private static final String DEFAULT_USE_BUFFER = "true";

    // sh stress.sh  MergerSimulator "taskName=Final stress.merge.sourceCount=4 task_engine_auto_start=true stress.merge.eventSize=1024 stress.merger.txnType=2"
    // sh stress.sh  MergerSimulator "taskName=Final task_merge_dry_run=true task_merge_dry_run_mode=0 stress.merge.sourceCount=4 task_engine_auto_start=true stress.merge.eventSize=1024 stress.merge.useBuffer=false stress.merger.txnType=2"
    public static void main(String[] args) {
        if (args.length > 0 && StringUtils.isNotBlank(args[0])) {
            handleArgs(args[0]);
        }
        int txnType = Integer.parseInt(getValue("stress.merger.txnType", DEFAULT_TXN_TYPE));
        int sourceQueueSize = Integer.parseInt(getValue("stress.merge.sourceQueueSize", DEFAULT_SOURCE_QUEUE_SIZE));
        int sourceCount = Integer.parseInt(getValue("stress.merge.sourceCount", DEFAULT_SOURCE_COUNT));
        int dmlCount = Integer.parseInt(getValue("stress.merge.dmlCountPerSource", DEFAULT_DML_COUNT_PER_SOURCE));
        int eventSize = Integer.parseInt(getValue("stress.merge.eventSize", DEFAULT_EVENT_SIZE));
        boolean useBuffer = Boolean.parseBoolean(getValue("stress.merge.useBuffer", DEFAULT_USE_BUFFER));

        TaskBootStrap bootStrap = new TaskBootStrap();
        bootStrap.setTaskConfigProvider(new TaskConfigProvider("Final") {

            @Override
            public TaskRuntimeConfig getTaskRuntimeConfig() {
                TaskRuntimeConfig taskRuntimeConfig = new TaskRuntimeConfig();
                taskRuntimeConfig.setId(3L);
                taskRuntimeConfig.setName("Final");
                taskRuntimeConfig.setType(TaskType.Final);
                taskRuntimeConfig.setStartTSO("");
                taskRuntimeConfig.setServerPort(9999);
                taskRuntimeConfig.setMergeSourceInfos(buildMergeSources());
                return taskRuntimeConfig;
            }

            private List<MergeSourceInfo> buildMergeSources() {
                List<MergeSourceInfo> list = new ArrayList<>();
                int partitionSeed = 178094002;
                for (int i = 0; i < sourceCount; i++) {
                    MergeSourceInfo info = new MergeSourceInfo();
                    info.setId(String.valueOf(i));
                    info.setType(MergeSourceType.MOCK);
                    info.setQueueSize(sourceQueueSize);
                    MockParameter parameter = new MockParameter();
                    parameter.setPartitionId(String.valueOf(++partitionSeed));
                    parameter.setTxnType(txnType);
                    parameter.setDmlCount(dmlCount);
                    parameter.setEventSiz(eventSize);
                    parameter.setUseBuffer(useBuffer);
                    info.setMockParameter(parameter);
                    list.add(info);
                }
                list.get(0)
                    .getMockParameter()
                    .setAllParties(
                        list.stream().map(m -> m.getMockParameter().getPartitionId()).collect(Collectors.toSet()));
                return list;
            }
        });

        bootStrap.boot(args);
    }
}
