/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogPhyDdlHistCleanPoint {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.841+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.id")
    private Integer id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.tso")
    private String tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.storage_inst_id")
    private String storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.ext")
    private String ext;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.838+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    public BinlogPhyDdlHistCleanPoint(Integer id, Date gmtCreated, Date gmtModified, String tso, String storageInstId, String ext) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.storageInstId = storageInstId;
        this.ext = ext;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.841+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    public BinlogPhyDdlHistCleanPoint() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.id")
    public Integer getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.842+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId == null ? null : storageInstId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.ext")
    public String getExt() {
        return ext;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.843+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.ext")
    public void setExt(String ext) {
        this.ext = ext == null ? null : ext.trim();
    }
}