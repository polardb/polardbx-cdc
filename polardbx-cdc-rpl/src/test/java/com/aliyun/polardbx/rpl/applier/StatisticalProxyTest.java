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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import org.hyperic.sigar.SigarException;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author shicai.xsc 2021/5/13 13:38
 * @since 5.0.0.0
 */
public class StatisticalProxyTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testFill() {
        int i = 1;
        int avgSeconds = DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND);
        while (i++ <= avgSeconds) {
            StatMetrics.getInstance().addSkipCount(2);
            StatMetrics.getInstance().addMergeBatchSize(300);
            StatMetrics.getInstance().addApplyCount(1);
            StatMetrics.getInstance().addRt(10);
            StatMetrics.getInstance().addInMessageCount(600);
            StatMetrics.getInstance().addOutMessageCount(300);
        }
        RplStatMetrics rplStatMetrics = new RplStatMetrics();
        StatisticalProxy.getInstance().fill(rplStatMetrics, StatMetrics.getInstance(), null,
            null);
        Assert.assertEquals(rplStatMetrics.getApplyCount().intValue(), 1);
        Assert.assertEquals(rplStatMetrics.getApplyCount().intValue(), 1);
        Assert.assertEquals(rplStatMetrics.getOutRps().intValue(), 300);
        Assert.assertEquals(rplStatMetrics.getInEps().intValue(), 600);
        Assert.assertEquals(rplStatMetrics.getRt().intValue(), 10);
    }

}
