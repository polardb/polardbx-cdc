/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class LogFileCopierTest extends BaseTest {

    @Test
    public void testDoubleIntValue() {
        int i = -1;
        double j = i * 0.8;
        Assert.assertEquals(0, Double.valueOf(j).intValue());
    }

    @Test
    public void testCalcFlowControlWindowSize() {
        ExecutionConfig executionConfig = new ExecutionConfig();
        LogFileCopier logFileCopier = new LogFileCopier(null, 100, 100, executionConfig);

        int size = logFileCopier.calcFlowControlWindowSize();
        Assert.assertEquals(512, size);

        executionConfig.setReservedMemMb(101);
        size = logFileCopier.calcFlowControlWindowSize();
        Assert.assertEquals(80, size);

        executionConfig.setReservedMemMb(1201);
        size = logFileCopier.calcFlowControlWindowSize();
        Assert.assertEquals(512, size);
    }
}
