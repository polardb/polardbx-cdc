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

import javax.annotation.Generated;
import java.util.Date;

public class BinlogTaskSchedule {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.task_name")
    private String taskName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.status")
    private String status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.version")
    private Integer version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.op")
    private String op;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source Table: binlog_task_schedule")
    public BinlogTaskSchedule(Long id, Date gmtCreated, Date gmtModified, String clusterId, String taskName,
                              String status, Integer version, String op) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.clusterId = clusterId;
        this.taskName = taskName;
        this.status = status;
        this.version = version;
        this.op = op;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source Table: binlog_task_schedule")
    public BinlogTaskSchedule() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.task_name")
    public String getTaskName() {
        return taskName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.task_name")
    public void setTaskName(String taskName) {
        this.taskName = taskName == null ? null : taskName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.902+08:00",
        comments = "Source field: binlog_task_schedule.status")
    public String getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.status")
    public void setStatus(String status) {
        this.status = status == null ? null : status.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.version")
    public Integer getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.op")
    public String getOp() {
        return op;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.op")
    public void setOp(String op) {
        this.op = op == null ? null : op.trim();
    }
}