/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.jvm;

import org.apache.commons.lang3.StringUtils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Objects;

/**
 * Created by ziyang.lb on 2021/01/21.
 */
public class JvmUtils {

    public static JvmSnapshot buildJvmSnapshot() {
        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();

        JvmSnapshot jvmSnapshot = new JvmSnapshot();
        jvmSnapshot.setStartTime(startTime);
        //计算新生代和老年代的内存使用情况
        List<MemoryPoolMXBean> mps = ManagementFactory.getMemoryPoolMXBeans();
        long edenUsed = 0, survivorUsed = 0, edenMax = 0, survivorMax = 0;
        for (MemoryPoolMXBean mp : mps) {
            MemoryType type = mp.getType();
            String name = mp.getName();
            if (type == MemoryType.HEAP) {
                switch (name) {
                case "Par Eden Space":
                case "PS Eden Space": {
                    MemoryUsage memoryUsage = mp.getUsage();
                    edenUsed = memoryUsage.getUsed();
                    edenMax = memoryUsage.getMax();
                    break;
                }
                case "Par Survivor Space":
                case "PS Survivor Space": {
                    MemoryUsage memoryUsage = mp.getUsage();
                    survivorUsed = memoryUsage.getUsed();
                    survivorMax = memoryUsage.getMax();
                    break;
                }
                case "CMS Old Gen":
                case "PS Old Gen": {
                    MemoryUsage memoryUsage = mp.getUsage();
                    jvmSnapshot.setOldUsed(memoryUsage.getUsed());
                    jvmSnapshot.setOldMax(memoryUsage.getMax());
                    break;
                }
                }
            }
            if (StringUtils.equalsIgnoreCase("Metaspace", name)) {
                MemoryUsage usage = mp.getUsage();
                // 当-XX:MaxMetaspaceSize没有配置时，max=-1，无限制
                if (usage.getMax() < 0) {
                    continue;
                }
                jvmSnapshot.setMetaUsed(usage.getUsed());
                jvmSnapshot.setMetaMax(usage.getMax());
            }
        }
        jvmSnapshot.setYoungUsed(edenUsed + survivorUsed);
        jvmSnapshot.setYoungMax(edenMax + survivorMax);
        //计算新生代和老年代的GC次数和时间
        List<GarbageCollectorMXBean> gc = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gc) {
            String name = gcBean.getName();
            switch (name) {
            case "ParNew":
            case "PS Scavenge": {
                jvmSnapshot.setYoungCollectionCount(gcBean.getCollectionCount());
                jvmSnapshot.setYoungCollectionTime(gcBean.getCollectionTime());
                break;
            }
            case "ConcurrentMarkSweep":
            case "PS MarkSweep": {
                jvmSnapshot.setOldCollectionCount(gcBean.getCollectionCount());
                jvmSnapshot.setOldCollectionTime(gcBean.getCollectionTime());
                break;
            }
            }
        }
        //计算当前线程数
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        jvmSnapshot.setCurrentThreadCount(threadMXBean.getThreadCount());

        return jvmSnapshot;
    }

    public static double getOldUsedRatio() {
        MemoryUsage oldMemoryUsage = null;
        List<MemoryPoolMXBean> mps = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean mp : mps) {
            MemoryType type = mp.getType();
            String name = mp.getName();
            if (type == MemoryType.HEAP) {
                switch (name) {
                case "CMS Old Gen":
                case "PS Old Gen":
                case "G1 Old Gen": {
                    oldMemoryUsage = mp.getUsage();
                    break;
                }
                }
            }
        }
        long oldMaxMemorySize = Objects.requireNonNull(oldMemoryUsage).getMax();
        long oldUsedMemorySize = oldMemoryUsage.getUsed();
        return (double) oldUsedMemorySize / (double) oldMaxMemorySize;
    }

    public static double getTotalUsedRatio() {
        MemoryMXBean totalMemoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage totalMemoryUsage = totalMemoryMXBean.getHeapMemoryUsage();
        long totalMaxMemorySize = totalMemoryUsage.getMax(); //最大可用内存
        long totalUsedMemorySize = totalMemoryUsage.getUsed(); //已使用的内存
        return (double) totalUsedMemorySize / (double) totalMaxMemorySize;
    }
}
