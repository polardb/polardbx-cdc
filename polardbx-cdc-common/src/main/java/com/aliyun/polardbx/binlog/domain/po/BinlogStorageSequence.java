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

public class BinlogStorageSequence {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.659+08:00", comments="Source field: binlog_storage_sequence.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.storage_inst_id")
    private String storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.662+08:00", comments="Source field: binlog_storage_sequence.storage_seq")
    private Long storageSeq;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.657+08:00", comments="Source Table: binlog_storage_sequence")
    public BinlogStorageSequence(Long id, Date gmtCreated, Date gmtModified, String storageInstId, Long storageSeq) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.storageInstId = storageInstId;
        this.storageSeq = storageSeq;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.659+08:00", comments="Source Table: binlog_storage_sequence")
    public BinlogStorageSequence() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.661+08:00", comments="Source field: binlog_storage_sequence.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.662+08:00", comments="Source field: binlog_storage_sequence.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.662+08:00", comments="Source field: binlog_storage_sequence.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId == null ? null : storageInstId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.662+08:00", comments="Source field: binlog_storage_sequence.storage_seq")
    public Long getStorageSeq() {
        return storageSeq;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.662+08:00", comments="Source field: binlog_storage_sequence.storage_seq")
    public void setStorageSeq(Long storageSeq) {
        this.storageSeq = storageSeq;
    }
}