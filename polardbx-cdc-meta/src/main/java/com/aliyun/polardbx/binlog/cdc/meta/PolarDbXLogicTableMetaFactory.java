/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;

public class PolarDbXLogicTableMetaFactory {
    public static PolarDbXLogicTableMeta create(TopologyManager topologyManager, String dnVersion) {
        PolarDbXLogicTableMeta polarDbXLogicTableMeta = new PolarDbXLogicTableMeta(topologyManager, dnVersion);
        polarDbXLogicTableMeta.init(null);
        return polarDbXLogicTableMeta;
    }
}
