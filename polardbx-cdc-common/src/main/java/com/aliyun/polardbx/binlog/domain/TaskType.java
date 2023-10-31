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
    DumperX;

    public static boolean isTask(String taskType) {
        return Relay.name().equals(taskType) || Final.name().equals(taskType) || Dispatcher.name().equals(taskType);
    }

    public static boolean isDumper(String taskType) {
        return Dumper.name().equals(taskType) || DumperX.name().equals(taskType);

    }

}
