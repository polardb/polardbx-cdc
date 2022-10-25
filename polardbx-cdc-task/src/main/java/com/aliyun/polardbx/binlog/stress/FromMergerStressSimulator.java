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
package com.aliyun.polardbx.binlog.stress;

import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskInfoProvider;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.MockParameter;
import com.aliyun.polardbx.binlog.domain.TaskInfo;
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

    // sh stress.sh  MergerSimulator "taskName=Final stress.merge.sourceCount=4 task.engine.autoStart=true stress.merge.eventSize=1024 stress.merger.txnType=2"
    // sh stress.sh  MergerSimulator "taskName=Final task.merger.dryRun=true task.merger.dryRun.mode=0 stress.merge.sourceCount=4 task.engine.autoStart=true stress.merge.eventSize=1024 stress.merge.useBuffer=false stress.merger.txnType=2"
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
        bootStrap.setTaskInfoProvider(new TaskInfoProvider("Final") {

            @Override
            public TaskInfo get() {
                TaskInfo info = new TaskInfo();
                info.setId(3L);
                info.setName("Final");
                info.setType(TaskType.Final);
                info.setStartTSO("");
                info.setServerPort(9999);
                info.setMergeSourceInfos(buildMergeSources());
                return info;
            }

            private List<MergeSourceInfo> buildMergeSources() {
                List<MergeSourceInfo> list = new ArrayList<>();
                int partitionSeed = 178094002;
                for (int i = 0; i < sourceCount; i++) {
                    MergeSourceInfo info = new MergeSourceInfo();
                    info.setId(String.valueOf(i));
                    info.setTaskName("source-" + i);
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
