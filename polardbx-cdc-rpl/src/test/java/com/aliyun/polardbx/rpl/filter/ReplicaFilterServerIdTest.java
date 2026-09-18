/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class ReplicaFilterServerIdTest {

    @Test
    public void serverIdFilter_BaseNeverMatchesAndReplicaUsesConfiguredIds() {
        Assert.assertFalse(new BaseFilter().isFilteredByServerId(7L));

        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setIgnoreServerIds("7, 9");
        ReplicaFilter filter = new ReplicaFilter(replicaMeta);
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            filter.init();
        }

        Assert.assertTrue(filter.isFilteredByServerId(7L));
        Assert.assertTrue(filter.isFilteredByServerId(9L));
        Assert.assertFalse(filter.isFilteredByServerId(8L));
    }
}
