/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class StorageHistoryDetailInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.tso")
    private String tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.instruction_id")
    private String instructionId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.stream_name")
    private String streamName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.57+08:00", comments="Source Table: binlog_storage_history_detail")
    public StorageHistoryDetailInfo(Long id, Date gmtCreated, Date gmtModified, String clusterId, String tso, String instructionId, String streamName, Integer status) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.clusterId = clusterId;
        this.tso = tso;
        this.instructionId = instructionId;
        this.streamName = streamName;
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source Table: binlog_storage_history_detail")
    public StorageHistoryDetailInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.571+08:00", comments="Source field: binlog_storage_history_detail.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.instruction_id")
    public String getInstructionId() {
        return instructionId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.instruction_id")
    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId == null ? null : instructionId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.stream_name")
    public String getStreamName() {
        return streamName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.stream_name")
    public void setStreamName(String streamName) {
        this.streamName = streamName == null ? null : streamName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source field: binlog_storage_history_detail.status")
    public void setStatus(Integer status) {
        this.status = status;
    }
}