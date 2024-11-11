/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.lang.management.ManagementFactory;

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
