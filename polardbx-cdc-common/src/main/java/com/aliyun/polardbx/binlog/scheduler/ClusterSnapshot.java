/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.ToString;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Set;

/**
 * Created by ziyang.lb
 **/
@ToString
public class ClusterSnapshot {
    private long version;
    private Long timestamp;
    private Set<String> containers;
    private Set<String> storages;
    private String dumperMaster;
    private String dumperMasterNode;
    private String storageHistoryTso;
    private Long serverId;

    public ClusterSnapshot() {
    }

    public ClusterSnapshot(long version, Long timestamp, Set<String> containers, Set<String> storages,
                           String dumperMasterNode, String dumperMaster, String storageHistoryTso,
                           String clusterType, Long serverId) {
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

        this.version = version;
        this.timestamp = timestamp;
        this.containers = containers;
        this.storages = storages;
        this.dumperMasterNode = dumperMasterNode;
        this.dumperMaster = dumperMaster;
        this.storageHistoryTso = storageHistoryTso;
        this.serverId = serverId;
    }

    /**
     * todo isNew 代表什么含义?
     */
    public boolean isNew() {
        return version == 1L;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Set<String> getContainers() {
        return containers;
    }

    public void setContainers(Set<String> containers) {
        this.containers = containers;
    }

    public Set<String> getStorages() {
        return storages;
    }

    public void setStorages(Set<String> storages) {
        this.storages = storages;
    }

    public String getDumperMaster() {
        return dumperMaster;
    }

    public void setDumperMaster(String dumperMaster) {
        this.dumperMaster = dumperMaster;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDumperMasterNode() {
        return dumperMasterNode;
    }

    public void setDumperMasterNode(String dumperMasterNode) {
        this.dumperMasterNode = dumperMasterNode;
    }

    public String getStorageHistoryTso() {
        return storageHistoryTso;
    }

    public void setStorageHistoryTso(String storageHistoryTso) {
        this.storageHistoryTso = storageHistoryTso;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }
}
