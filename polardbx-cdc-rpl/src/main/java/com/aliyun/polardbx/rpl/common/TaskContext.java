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
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;

import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.rpl.applier.BaseApplier;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta.PhysicalMeta;
import lombok.Data;

/**
 * @author shicai.xsc 2020/12/21 10:48
 * @since 5.0.0.0
 */
@Data
public class TaskContext {
    // note: 不要依赖taskcontext成员中易变的字段
    private RplStateMachine stateMachine;
    private RplService service;
    private RplTask task;
    private RplTaskConfig taskConfig;
    private String worker;
    private String config;
    private static TaskContext instance;
    private int physicalNum;
    private PhysicalMeta physicalMeta;
    private BaseExtractor extractor;
    private BasePipeline pipeline;
    private BaseApplier applier;
    private BaseFilter filter;

    private TaskContext() {
    }

    public static TaskContext getInstance() {
        if (instance == null) {
            synchronized (TaskContext.class) {
                if (instance == null) {
                    instance = new TaskContext();
                }
            }
        }
        return instance;
    }

    public long getTaskId() {
        return task.getId();
    }

    public long getServiceId() {
        return service.getId();
    }

    public long getStateMachineId() {
        return stateMachine.getId();
    }

}
