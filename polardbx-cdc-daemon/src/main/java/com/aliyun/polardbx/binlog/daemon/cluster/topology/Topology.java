/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Topology {
    private Long serverID;
    private List<BinlogTaskConfig> configList = new ArrayList<>();
    private Map<String, String> streamStorageMap = new HashMap<>();
}
