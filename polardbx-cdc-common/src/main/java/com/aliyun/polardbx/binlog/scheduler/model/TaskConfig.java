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

package com.aliyun.polardbx.binlog.scheduler.model;

import lombok.Data;

import java.util.List;

/**
 * Created by ziyang
 */
@Data
public class TaskConfig {

    public static final String ORIGIN_TSO = "000000000000000000000000000000000000000000000000000000";
    /**
     * source type @see com.aliyun.polardbx.binlog.domain.TaskType
     */
    private String type;
    /**
     * 上游sources
     */
    private List<String> sources;
    /**
     * 当前配置的对应的tso
     */
    private String tso;
}
