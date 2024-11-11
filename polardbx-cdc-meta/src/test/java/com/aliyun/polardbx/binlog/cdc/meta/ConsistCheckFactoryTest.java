/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ConsistCheckFactoryTest {
    @Test
    public void testCreateConsistCheck() {
        TopologyManager topologyManager = Mockito.mock(TopologyManager.class);
        PolarDbXLogicTableMeta polarDbXLogicTableMeta = Mockito.mock(PolarDbXLogicTableMeta.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(PolarDbXTableMetaManager.class);
        ConsistencyChecker consistencyChecker =
            ConsistencyCheckerFactory.create(topologyManager, polarDbXLogicTableMeta, polarDbXTableMetaManager, "");
        Assert.assertNotNull(consistencyChecker);
    }
}
