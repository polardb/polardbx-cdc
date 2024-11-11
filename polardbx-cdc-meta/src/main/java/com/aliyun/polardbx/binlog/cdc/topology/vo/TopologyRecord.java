/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology.vo;

import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicTableMetaTopology;
import lombok.Data;

/**
 * created by ziyang.lb
 */
@Data
public class TopologyRecord {
    boolean lowerCased;
    LogicDbTopology logicDbMeta;
    LogicTableMetaTopology logicTableMeta;

    public TopologyRecord copy() {
        TopologyRecord obj = new TopologyRecord();
        obj.lowerCased = this.lowerCased;
        if (this.logicDbMeta != null) {
            obj.logicDbMeta = this.logicDbMeta.copy();
        }
        if (this.logicTableMeta != null) {
            obj.logicTableMeta = this.logicTableMeta.copy();
        }
        return obj;
    }
}
