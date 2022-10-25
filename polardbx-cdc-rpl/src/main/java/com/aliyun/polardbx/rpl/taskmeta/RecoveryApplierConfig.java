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
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

import java.util.List;

/**
 * @author yudong
 */
@Data
public class RecoveryApplierConfig extends ApplierConfig {

    /**
     * The name of database
     */
    private String schema;

    /**
     * The type of recovery SQL
     */
    private boolean isMirror;

    /**
     * Start time(millsecond) of misoperation
     */
    private long beginTime;

    /**
     * End time(millsecond) of misoperation
     */
    private long endTime;

    /**
     * Types of misoperation SQL (INSERT,UPDATE,DELETE)
     * If there are multiple types, use commas to separate
     */
    private String sqlType;

    /**
     * TraceId of misoperation SQL
     * If traceID is empty, use fuzzy matching; otherwise use exact matching
     */
    private String traceId;

    /**
     * Logic table of misoperation
     */
    private String table;

    /**
     * The binlog file consumed by the task, used to terminate the extractor
     */
    private List<String> binlogList;

    /**
     * 随机串
     */
    private String randomUUID;

    /**
     * The sequence number of this task, used to name the output file
     */
    private int sequence;
    /**
     * 是否注入故障
     */
    private boolean injectTrouble;
}
