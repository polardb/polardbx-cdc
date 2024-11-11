/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class CdcSyncPointMeta {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.872704+08:00", comments="Source field: cdc_sync_point_meta.id")
    private String id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873127+08:00", comments="Source field: cdc_sync_point_meta.participants")
    private Integer participants;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873203+08:00", comments="Source field: cdc_sync_point_meta.tso")
    private Long tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873271+08:00", comments="Source field: cdc_sync_point_meta.valid")
    private Integer valid;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873337+08:00", comments="Source field: cdc_sync_point_meta.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873423+08:00", comments="Source field: cdc_sync_point_meta.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.871569+08:00", comments="Source Table: cdc_sync_point_meta")
    public CdcSyncPointMeta(String id, Integer participants, Long tso, Integer valid, Date gmtCreated, Date gmtModified) {
        this.id = id;
        this.participants = participants;
        this.tso = tso;
        this.valid = valid;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.872449+08:00", comments="Source Table: cdc_sync_point_meta")
    public CdcSyncPointMeta() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873046+08:00", comments="Source field: cdc_sync_point_meta.id")
    public String getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873101+08:00", comments="Source field: cdc_sync_point_meta.id")
    public void setId(String id) {
        this.id = id == null ? null : id.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873151+08:00", comments="Source field: cdc_sync_point_meta.participants")
    public Integer getParticipants() {
        return participants;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873179+08:00", comments="Source field: cdc_sync_point_meta.participants")
    public void setParticipants(Integer participants) {
        this.participants = participants;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873227+08:00", comments="Source field: cdc_sync_point_meta.tso")
    public Long getTso() {
        return tso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.87325+08:00", comments="Source field: cdc_sync_point_meta.tso")
    public void setTso(Long tso) {
        this.tso = tso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873294+08:00", comments="Source field: cdc_sync_point_meta.valid")
    public Integer getValid() {
        return valid;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873316+08:00", comments="Source field: cdc_sync_point_meta.valid")
    public void setValid(Integer valid) {
        this.valid = valid;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873366+08:00", comments="Source field: cdc_sync_point_meta.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873399+08:00", comments="Source field: cdc_sync_point_meta.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873447+08:00", comments="Source field: cdc_sync_point_meta.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.873471+08:00", comments="Source field: cdc_sync_point_meta.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }
}