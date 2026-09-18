/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Set;

/**
 * Created by ziyang.lb
 **/
@Setter
@Getter
@ToString
public class ClusterSnapshot {
    private long version;
    private Long subVersion;
    private Long timestamp;
    private Set<String> containers;
    private Set<String> containersBeforeRandomRemove;
    private Set<String> storages;
    private String dumperMaster;
    private String dumperMasterNode;
    private String storageHistoryTso;
    private Long serverId;
    private Map<String, String> streamStorageMap;
    private Map<String, StreamEntitySet> containerStreamMap;
    private String finalTaskNode;

    public ClusterSnapshot() {
        this.version = 1L;
        this.subVersion = 1L;
    }

    public ClusterSnapshot(long version, Long timestamp, Set<String> containers, Set<String> storages,
                           String dumperMasterNode, String dumperMaster, String storageHistoryTso,
                           String clusterType, Long serverId, long subVersion, String finalTaskNode) {
        this(version, timestamp, containers, storages, dumperMasterNode, dumperMaster,
            storageHistoryTso, clusterType, serverId, null, subVersion, finalTaskNode, null);
    }

    public ClusterSnapshot(long version, Long timestamp, Set<String> containers, Set<String> storages,
                           String dumperMasterNode, String dumperMaster, String storageHistoryTso,
                           String clusterType, Long serverId, Map<String, String> streamStorageMap,
                           Map<String, StreamEntitySet> containerStreamMap, long subVersion) {
        this(version, timestamp, containers, storages, dumperMasterNode, dumperMaster,
            storageHistoryTso, clusterType, serverId, streamStorageMap, subVersion, null, containerStreamMap);
    }

    public ClusterSnapshot(long version, Long timestamp, Set<String> containers, Set<String> storages,
                           String dumperMasterNode, String dumperMaster, String storageHistoryTso,
                           String clusterType, Long serverId, Map<String, String> streamStorageMap,
                           long subVersion, String finalTaskNode, Map<String, StreamEntitySet> containerStreamMap) {
        if (version != 1L && timestamp == null) {
            throw new PolardbxException("timestamp can not be null.");
        }
        if (version != 1L && CollectionUtils.isEmpty(containers)) {
            throw new PolardbxException("containers can not be null or empty.");
        }
        if (version != 1L && CollectionUtils.isEmpty(storages)) {
            throw new PolardbxException("storages can not be null or empty.");
        }
        if (version != 1L && StringUtils.isBlank(dumperMaster) && StringUtils
            .equals(clusterType, ClusterType.BINLOG.name())) {
            throw new PolardbxException("dumperMaster can not be null or empty.");
        }
        if (version != 1L && StringUtils.isBlank(dumperMasterNode) && StringUtils
            .equals(clusterType, ClusterType.BINLOG.name())) {
            throw new PolardbxException("dumperNode can not be null or empty.");
        }
        if (version != 1L && StringUtils.isBlank(storageHistoryTso)) {
            throw new PolardbxException("storageHistoryTso can not be null or empty.");
        }
        if (version != 1L && serverId == null) {
            throw new PolardbxException("server_id can not be null.");
        }
        if (version != 1L && StringUtils
            .equals(clusterType, ClusterType.BINLOG.name()) && StringUtils.isBlank(finalTaskNode)) {
            throw new PolardbxException("finalTaskNode can not be null or empty.");
        }

        this.version = version;
        this.timestamp = timestamp;
        this.containers = containers;
        this.storages = storages;
        this.dumperMasterNode = dumperMasterNode;
        this.dumperMaster = dumperMaster;
        this.storageHistoryTso = storageHistoryTso;
        this.serverId = serverId;
        this.streamStorageMap = streamStorageMap;
        this.containerStreamMap = containerStreamMap;
        this.subVersion = subVersion;
        this.finalTaskNode = finalTaskNode;
    }

    public boolean isOrigin() {
        return version == 1L;
    }
}
