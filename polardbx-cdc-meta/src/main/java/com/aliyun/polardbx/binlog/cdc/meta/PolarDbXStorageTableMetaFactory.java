/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;

public class PolarDbXStorageTableMetaFactory {
    public static PolarDbXStorageTableMeta create(String storageInstId, PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                                                  TopologyManager topologyManager, String dnVersion) {
        PolarDbXStorageTableMeta storageTableMeta =
            new PolarDbXStorageTableMeta(storageInstId,
                polarDbXLogicTableMeta, topologyManager, dnVersion);
        storageTableMeta.init(null);
        return storageTableMeta;
    }
}
