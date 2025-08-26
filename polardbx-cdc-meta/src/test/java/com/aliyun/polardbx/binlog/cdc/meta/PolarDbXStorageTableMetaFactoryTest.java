/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class PolarDbXStorageTableMetaFactoryTest extends BaseTest {
    @Test
    public void testCreatePolarDBXStorageTableMeta() throws Exception {
        TopologyManager topologyManager = new TopologyManager();
        PolarDbXLogicTableMeta polarDbXLogicTableMeta = PolarDbXLogicTableMetaFactory.create(topologyManager, "5.7");
        PolarDbXStorageTableMeta polarDbXStorageTableMeta =
            PolarDbXStorageTableMetaFactory.create("", polarDbXLogicTableMeta, topologyManager, "5.7");
        Assert.assertNotNull(polarDbXStorageTableMeta);
    }
}
