/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ziyang.lb
 **/
public enum TaskType {
    /**
     * 中继类型，只负责局部物理Binlog的合并
     */
    Relay,
    /**
     * Final类型，负责全局物理Binlog的合并
     */
    Final,
    /**
     * 分发类型，将Binlog按照主键ID进行Hash分发
     */
    Dispatcher,
    /**
     * Dumper类型，负责全局Binlog的落盘和对外Dump服务
     */
    Dumper,
    /**
     * DumperX类型，多流模式下的负责逻辑Binlog的罗盘和对外Dump服务
     */
    DumperX,
    /**
     * Columnar类型，列存服务
     */
    Columnar;

    public static boolean isTask(String taskType) {
        return Relay.name().equals(taskType) || Final.name().equals(taskType) || Dispatcher.name().equals(taskType);
    }

    public static boolean isDumper(String taskType) {
        return Dumper.name().equals(taskType) || DumperX.name().equals(taskType);

    }
}
