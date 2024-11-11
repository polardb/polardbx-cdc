/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import lombok.Builder;

import javax.annotation.Generated;
import java.util.Date;

@Builder
public class BinlogPhyDdlHistory {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.97+08:00",
        comments = "Source field: binlog_phy_ddl_history.id")
    private Integer id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.tso")
    private String tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.binlog_file")
    private String binlogFile;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.pos")
    private Integer pos;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.storage_inst_id")
    private String storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.db_name")
    private String dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.extra")
    private String extra;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.ddl")
    private String ddl;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.97+08:00",
        comments = "Source Table: binlog_phy_ddl_history")
    public BinlogPhyDdlHistory(Integer id, Date gmtCreated, Date gmtModified, String tso, String binlogFile,
                               Integer pos, String storageInstId, String dbName, String extra, String clusterId,
                               String ddl) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.binlogFile = binlogFile;
        this.pos = pos;
        this.storageInstId = storageInstId;
        this.dbName = dbName;
        this.extra = extra;
        this.clusterId = clusterId;
        this.ddl = ddl;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.97+08:00",
        comments = "Source Table: binlog_phy_ddl_history")
    public BinlogPhyDdlHistory() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.97+08:00",
        comments = "Source field: binlog_phy_ddl_history.id")
    public Integer getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.tso")
    public void setTso(String tso) {
        this.tso = tso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.binlog_file")
    public String getBinlogFile() {
        return binlogFile;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.binlog_file")
    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.pos")
    public Integer getPos() {
        return pos;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.pos")
    public void setPos(Integer pos) {
        this.pos = pos;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.extra")
    public String getExtra() {
        return extra;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.extra")
    public void setExtra(String extra) {
        this.extra = extra;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.ddl")
    public String getDdl() {
        return ddl;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-08-12T17:30:43.971+08:00",
        comments = "Source field: binlog_phy_ddl_history.ddl")
    public void setDdl(String ddl) {
        this.ddl = ddl;
    }
}
