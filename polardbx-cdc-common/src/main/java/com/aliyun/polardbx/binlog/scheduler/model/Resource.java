/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Builder;
import lombok.Data;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_ADAPTIVE_USE_RATIO_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_USE_RATIO;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getDouble;

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

    public int getBaseAvailableMemMb() {
        double mem = memory_mb * getAvailableRatio();
        return Double.valueOf(mem).intValue();
    }

    /**
     * cdc进程最多占用90%的内存，daemon最多占用min（10%内存，256Mb）
     */
    public int getFreeMemMb() {
        double mem = memory_mb * getAvailableRatio();
        return Double.valueOf(mem).intValue() - used;
    }

    public int getReservedMemMb() {
        return Double.valueOf(memory_mb * (1 - getAvailableRatio())).intValue() - 256;
    }

    public int getFreeMemMbWithoutRatio() {
        return memory_mb;
    }

    //如果节点的内存比较小，ratio则不能太大，需要给daemon/rocksdb/grpc预留一部分空间
    private double getAvailableRatio() {
        boolean adaptiveRatio = getBoolean(TOPOLOGY_RESOURCE_ADAPTIVE_USE_RATIO_ENABLED);
        if (adaptiveRatio) {
            if (memory_mb <= 1024) {
                return 0.6;
            } else if (memory_mb <= 2048) {
                return 0.65;
            } else if (memory_mb <= 4096) {
                return 0.7;
            } else if (memory_mb <= 8192) {
                return 0.75;
            } else if (memory_mb <= 16384) {
                return 0.8;
            } else if (memory_mb <= 32768) {
                return 0.85;
            } else if (memory_mb <= 65536) {
                return 0.9;
            } else {
                return 0.95;
            }
        } else {
            return getDouble(TOPOLOGY_RESOURCE_USE_RATIO);
        }
    }
}
