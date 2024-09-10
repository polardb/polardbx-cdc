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
