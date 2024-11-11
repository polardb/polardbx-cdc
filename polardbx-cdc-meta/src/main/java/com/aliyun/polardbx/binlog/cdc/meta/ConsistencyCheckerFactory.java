/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;

public class ConsistencyCheckerFactory {
    public static ConsistencyChecker create(TopologyManager topologyManager,
                                            PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                                            PolarDbXTableMetaManager polarDbXTableMetaManager, String storageInstId) {
        return new ConsistencyChecker(topologyManager, polarDbXLogicTableMeta, polarDbXTableMetaManager, storageInstId);
    }
}
