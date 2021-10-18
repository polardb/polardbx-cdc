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

package com.aliyun.polardbx.binlog.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

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

    public static void main(String[] args) {
        buildJvmSnapshot();
    }
}
