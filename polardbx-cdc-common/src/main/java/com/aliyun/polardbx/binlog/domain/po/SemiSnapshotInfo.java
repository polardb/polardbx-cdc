/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class SemiSnapshotInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.tso")
    private String tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.121+08:00", comments="Source field: binlog_semi_snapshot.storage_inst_id")
    private String storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.117+08:00", comments="Source Table: binlog_semi_snapshot")
    public SemiSnapshotInfo(Long id, Date gmtCreated, Date gmtModified, String tso, String storageInstId) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.storageInstId = storageInstId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.119+08:00", comments="Source Table: binlog_semi_snapshot")
    public SemiSnapshotInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.12+08:00", comments="Source field: binlog_semi_snapshot.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.121+08:00", comments="Source field: binlog_semi_snapshot.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.121+08:00", comments="Source field: binlog_semi_snapshot.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.121+08:00", comments="Source field: binlog_semi_snapshot.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.121+08:00", comments="Source field: binlog_semi_snapshot.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId == null ? null : storageInstId.trim();
    }
}