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

package com.aliyun.polardbx.binlog;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.lang.management.ManagementFactory;

/**
 *
 */
public class TestResource {
    @Test
    public void t1() {

    }

    @Test
    public void t2() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
            OperatingSystemMXBean.class);
        // What % CPU load this current JVM is taking, from 0.0-1.0
        System.out.println(osBean.getProcessCpuLoad());

        // What % load the overall system is at, from 0.0-1.0
        System.out.println(osBean.getSystemCpuLoad());

        System.out.println(FileUtils.byteCountToDisplaySize(osBean.getFreePhysicalMemorySize()));
        System.out.println(FileUtils.byteCountToDisplaySize(osBean.getCommittedVirtualMemorySize()));
        System.out.println(FileUtils.byteCountToDisplaySize(osBean.getTotalPhysicalMemorySize()));
    }
}
