/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.scheduler.StreamEntitySet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class TopologyEntity {
    private long serverId;
    private String finalTaskNode;
    private List<BinlogTaskConfig> taskConfigs = new ArrayList<>();
    private Map<String, String> streamStorageMap = new HashMap<>();
    private Map<String, StreamEntitySet> containerStreamMap = new HashMap<>();
}
