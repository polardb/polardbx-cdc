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
package com.aliyun.polardbx.binlog.daemon.rest.resources.request;

import lombok.Data;

/**
 * @author fanfei
 */
@Data
public class FlashBackTaskParameter {
    /**
     * [必填] 逻辑库名
     */
    private String logicDbName;
    /**
     * [必填] 开始时间
     */
    private long startTimestamp;
    /**
     * [必填] 结束时间
     */
    private long endTimestamp;
    /**
     * [必填] 原始SQL or 回滚SQL
     */
    private boolean isMirror;
    /**
     * [选填] traceID
     */
    private String traceId;
    /**
     * [选填] 逻辑表名
     */
    private String logicTableName;
    /**
     * [选填] 误操作SQL类型
     */
    private String sqlType;
    /**
     * [选填] 是否注入故障
     */
    private boolean injectTrouble;
}
