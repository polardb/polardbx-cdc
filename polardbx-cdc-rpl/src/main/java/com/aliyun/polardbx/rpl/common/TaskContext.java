/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/21 10:48
 * @since 5.0.0.0
 */
@Data
public class TaskContext {
    private RplStateMachine stateMachine;
    private RplService service;
    private RplTask task;
    private String worker;
    private String config;
    private static TaskContext instance;
    private int physicalId;

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

    public RplStateMachine getStateMachine() {
        return stateMachine;
    }

    public long getStateMachineId() {
        return stateMachine.getId();
    }

    public String getConfig() {
        return config;
    }
}
