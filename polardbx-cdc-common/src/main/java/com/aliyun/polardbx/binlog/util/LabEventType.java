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
package com.aliyun.polardbx.binlog.util;

public enum LabEventType {
    DN_FOLLOWER_DELAY_ERROR_INJECT("DN延迟异常注入"),
    DETECTED_DN_FOLLOWER_DELAY("检测到DN不可用"),
    DETECTED_DN_FOLLOWER_NORMAL("检测到DN正常"),
    FINAL_TASK_STOP("Final停止"),
    FINAL_TASK_START("Final启动"),
    FLUSH_DN_LOG_WHEN_START_TASK("Final启动后flush dn log"),
    FINAL_TASK_DUMP_SAME_REGION_DN_BINLOG("全局Binlog Slave集群Task就近访问DN"),
    FINAL_TASK_DUMP_DIFFERENT_REGION_DN_BINLOG("全局Binlog Slave集群Task访问异地DN Master"),
    DUMPER_DO_COMPENSATION("Dumper触发了补偿逻辑"),
    DUMPER_DUMP_LOCAL_FILE_IS_DELETED("下游在dump过程中本地文件被删"),
    DUMPER_SYNC_LOCAL_FILE_IS_DELETED("Dumper slave在sync过程中本地文件被删"),
    SCHEDULE_TRIGGER_FLUSH_LOGS("定时flushLog测试"),
    FLUSH_LOGS("执行命令滚动BINLOG"),
    RPL_SEARCH_POSITION("replica搜索位点"),
    TEST_IS_METADB_LEADER("测试是否是leader节点"),
    TASK_TRANSACTION_PERSIST_ERROR("Transaction对象发生持久化异常"),
    EXCEPTION_RE_WRITE_DDL("异常重写DDL");

    private final String desc;

    LabEventType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
