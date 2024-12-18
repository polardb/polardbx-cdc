/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
    EXCEPTION_RE_WRITE_DDL("异常重写DDL"),
    SQL_STATMENT_DB_TYPE_NOT_MYSQL("FastSql.dbType设置为非MySQL"),
    SYNC_POINT_COMMIT_WITHOUT_SEQ("开启xa tso事务策略后检测到无TSO的事务"),
    SYNC_POINT_UNEXPECTED_LOCAL_SEQ("sync point事务后的单机事务tso小于sync point事务tso"),
    HIDDEN_PK_ENABLE_SWITCH("隐藏主键开关记录"),
    UPLOAD_UNLOCK_FAIL("上传文件后解锁失败"),
    UPDATE_QUERY_INFO("从binlog解析的update sql变更列");

    private final String desc;

    LabEventType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
