/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class BinlogScheduleHistory {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.294+08:00",
        comments = "Source field: binlog_schedule_history.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.version")
    private Long version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.content")
    private String content;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.291+08:00",
        comments = "Source Table: binlog_schedule_history")
    public BinlogScheduleHistory(Long id, Date gmtCreated, Date gmtModified, Long version, String content) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.version = version;
        this.content = content;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.294+08:00",
        comments = "Source Table: binlog_schedule_history")
    public BinlogScheduleHistory() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.295+08:00",
        comments = "Source field: binlog_schedule_history.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.content")
    public String getContent() {
        return content;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T23:25:47.296+08:00",
        comments = "Source field: binlog_schedule_history.content")
    public void setContent(String content) {
        this.content = content == null ? null : content.trim();
    }
}