/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogOssRecord {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.486+08:00", comments="Source field: binlog_oss_record.id")
    private Integer id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.binlog_file")
    private String binlogFile;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.upload_status")
    private Integer uploadStatus;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.purge_status")
    private Integer purgeStatus;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.upload_host")
    private String uploadHost;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_begin")
    private Date logBegin;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_end")
    private Date logEnd;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_size")
    private Long logSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.483+08:00", comments="Source Table: binlog_oss_record")
    public BinlogOssRecord(Integer id, Date gmtCreated, Date gmtModified, String binlogFile, Integer uploadStatus, Integer purgeStatus, String uploadHost, Date logBegin, Date logEnd, Long logSize) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.binlogFile = binlogFile;
        this.uploadStatus = uploadStatus;
        this.purgeStatus = purgeStatus;
        this.uploadHost = uploadHost;
        this.logBegin = logBegin;
        this.logEnd = logEnd;
        this.logSize = logSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.485+08:00", comments="Source Table: binlog_oss_record")
    public BinlogOssRecord() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.486+08:00", comments="Source field: binlog_oss_record.id")
    public Integer getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.binlog_file")
    public String getBinlogFile() {
        return binlogFile;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.binlog_file")
    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile == null ? null : binlogFile.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.upload_status")
    public Integer getUploadStatus() {
        return uploadStatus;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.upload_status")
    public void setUploadStatus(Integer uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.purge_status")
    public Integer getPurgeStatus() {
        return purgeStatus;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.purge_status")
    public void setPurgeStatus(Integer purgeStatus) {
        this.purgeStatus = purgeStatus;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.487+08:00", comments="Source field: binlog_oss_record.upload_host")
    public String getUploadHost() {
        return uploadHost;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.upload_host")
    public void setUploadHost(String uploadHost) {
        this.uploadHost = uploadHost == null ? null : uploadHost.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_begin")
    public Date getLogBegin() {
        return logBegin;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_begin")
    public void setLogBegin(Date logBegin) {
        this.logBegin = logBegin;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_end")
    public Date getLogEnd() {
        return logEnd;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_end")
    public void setLogEnd(Date logEnd) {
        this.logEnd = logEnd;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_size")
    public Long getLogSize() {
        return logSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-29T17:02:08.488+08:00", comments="Source field: binlog_oss_record.log_size")
    public void setLogSize(Long logSize) {
        this.logSize = logSize;
    }
}