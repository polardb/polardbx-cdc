/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.jvm;

import com.sun.management.HotSpotDiagnosticMXBean;
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
        boolean useG1 = false;
        long edenUsed = 0, survivorUsed = 0, edenMax = 0, survivorMax = 0, edenCommitted = 0, survivorCommitted = 0;
        for (MemoryPoolMXBean mp : mps) {
            MemoryType type = mp.getType();
            String name = mp.getName();
            if (type == MemoryType.HEAP) {
                switch (name) {
                case "Par Eden Space":
                case "PS Eden Space":
                case "G1 Eden Space": {
                    if (name.contains("G1")) {
                        useG1 = true;
                    }
                    MemoryUsage memoryUsage = mp.getUsage();
                    edenUsed = memoryUsage.getUsed();
                    edenMax = memoryUsage.getMax();
                    edenCommitted = memoryUsage.getCommitted();
                    break;
                }
                case "Par Survivor Space":
                case "PS Survivor Space":
                case "G1 Survivor Space": {
                    MemoryUsage memoryUsage = mp.getUsage();
                    survivorUsed = memoryUsage.getUsed();
                    survivorMax = memoryUsage.getMax();
                    survivorCommitted = memoryUsage.getCommitted();
                    break;
                }
                case "CMS Old Gen":
                case "PS Old Gen":
                case "G1 Old Gen": {
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
        // G1 edenMax and survivorMax are -1
        // G1 垃圾回收器的年轻代空间是不固定的, 因此在下文会用另一种计算方法计算
        jvmSnapshot.setYoungMax(edenMax + survivorMax);
        jvmSnapshot.setYoungCommitted(edenCommitted + survivorCommitted);

        MemoryMXBean totalMemoryMXBean = ManagementFactory.getMemoryMXBean();
        HotSpotDiagnosticMXBean diag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        MemoryUsage totalMemoryUsage = totalMemoryMXBean.getHeapMemoryUsage();

        double maxNewPct = 60.0;
        try {
            // 没有显式设置该参数就会报错，默认0.6
            String val = diag.getVMOption("G1MaxNewSizePercent").getValue();
            maxNewPct = Double.parseDouble(val);
        } catch (Throwable t) {
            // ignore
        }
        //最大可用内存
        long totalMaxMemorySize = totalMemoryUsage.getMax();
        if (useG1) {
            jvmSnapshot.setYoungMax((long) (totalMaxMemorySize * maxNewPct / 100.0));
        }

        //已使用的内存
        long totalUsedMemorySize = totalMemoryUsage.getUsed();
        jvmSnapshot.setTotalRatio((double) totalUsedMemorySize / (double) totalMaxMemorySize);
        jvmSnapshot.setHeapMax(totalMaxMemorySize);

        //计算新生代和老年代的GC次数和时间
        List<GarbageCollectorMXBean> gc = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gc) {
            String name = gcBean.getName();
            switch (name) {
            case "ParNew":
            case "PS Scavenge":
            case "G1 Young Generation": {
                jvmSnapshot.setYoungCollectionCount(gcBean.getCollectionCount());
                jvmSnapshot.setYoungCollectionTime(gcBean.getCollectionTime());
                break;
            }
            case "ConcurrentMarkSweep":
            case "PS MarkSweep":
            case "G1 Old Generation": {
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
