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

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Builder;
import lombok.Data;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_USE_RATIO;

/**
 * Created by ShuGuang
 */
@Builder
@Data
public class Resource {
    //cpu核心个数，在数据链路中，非cpu密集，故这里应该是虚拟cpu，物理cpu*4，即最多分配4*cpu个任务
    private int cpu;
    private int memory_mb; //内存的MB数
    private int used; //内存使用的MB数

    public int getVirCpu() {
        return cpu * 4;
    }

    public void addUse(int use) {
        this.used += use;
    }

    /**
     * cdc进程最多占用90%的内存，daemon最多占用min（10%内存，256Mb）
     */
    public int getFreeMemMb() {
        double mem = memory_mb * DynamicApplicationConfig.getDouble(TOPOLOGY_RESOURCE_USE_RATIO);
        return Double.valueOf(mem).intValue() - used;
    }
}
