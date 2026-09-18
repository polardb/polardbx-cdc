/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogScheduleHistory {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880455+08:00", comments="Source field: binlog_schedule_history.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880722+08:00", comments="Source field: binlog_schedule_history.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880791+08:00", comments="Source field: binlog_schedule_history.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880848+08:00", comments="Source field: binlog_schedule_history.version")
    private Long version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880924+08:00", comments="Source field: binlog_schedule_history.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881005+08:00", comments="Source field: binlog_schedule_history.sub_version")
    private Long subVersion;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881077+08:00", comments="Source field: binlog_schedule_history.content")
    private String content;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.87978+08:00", comments="Source Table: binlog_schedule_history")
    public BinlogScheduleHistory(Long id, Date gmtCreated, Date gmtModified, Long version, String clusterId, Long subVersion, String content) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.version = version;
        this.clusterId = clusterId;
        this.subVersion = subVersion;
        this.content = content;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880311+08:00", comments="Source Table: binlog_schedule_history")
    public BinlogScheduleHistory() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880668+08:00", comments="Source field: binlog_schedule_history.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880701+08:00", comments="Source field: binlog_schedule_history.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88075+08:00", comments="Source field: binlog_schedule_history.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880772+08:00", comments="Source field: binlog_schedule_history.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88081+08:00", comments="Source field: binlog_schedule_history.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88083+08:00", comments="Source field: binlog_schedule_history.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88088+08:00", comments="Source field: binlog_schedule_history.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880903+08:00", comments="Source field: binlog_schedule_history.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880948+08:00", comments="Source field: binlog_schedule_history.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.880985+08:00", comments="Source field: binlog_schedule_history.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881027+08:00", comments="Source field: binlog_schedule_history.sub_version")
    public Long getSubVersion() {
        return subVersion;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881057+08:00", comments="Source field: binlog_schedule_history.sub_version")
    public void setSubVersion(Long subVersion) {
        this.subVersion = subVersion;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881099+08:00", comments="Source field: binlog_schedule_history.content")
    public String getContent() {
        return content;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.881122+08:00", comments="Source field: binlog_schedule_history.content")
    public void setContent(String content) {
        this.content = content == null ? null : content.trim();
    }
}