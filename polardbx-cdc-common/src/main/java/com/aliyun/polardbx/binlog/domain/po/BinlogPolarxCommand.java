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

public class BinlogPolarxCommand {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.909+08:00",
        comments = "Source field: binlog_polarx_command.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_id")
    private String cmdId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_type")
    private String cmdType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_status")
    private Long cmdStatus;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_request")
    private String cmdRequest;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_reply")
    private String cmdReply;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.905+08:00",
        comments = "Source Table: binlog_polarx_command")
    public BinlogPolarxCommand(Long id, Date gmtCreated, Date gmtModified, String cmdId, String cmdType, Long cmdStatus,
                               String cmdRequest, String cmdReply) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.cmdId = cmdId;
        this.cmdType = cmdType;
        this.cmdStatus = cmdStatus;
        this.cmdRequest = cmdRequest;
        this.cmdReply = cmdReply;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.908+08:00",
        comments = "Source Table: binlog_polarx_command")
    public BinlogPolarxCommand() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_id")
    public String getCmdId() {
        return cmdId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_id")
    public void setCmdId(String cmdId) {
        this.cmdId = cmdId == null ? null : cmdId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_type")
    public String getCmdType() {
        return cmdType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.91+08:00",
        comments = "Source field: binlog_polarx_command.cmd_type")
    public void setCmdType(String cmdType) {
        this.cmdType = cmdType == null ? null : cmdType.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_status")
    public Long getCmdStatus() {
        return cmdStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_status")
    public void setCmdStatus(Long cmdStatus) {
        this.cmdStatus = cmdStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_request")
    public String getCmdRequest() {
        return cmdRequest;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_request")
    public void setCmdRequest(String cmdRequest) {
        this.cmdRequest = cmdRequest == null ? null : cmdRequest.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_reply")
    public String getCmdReply() {
        return cmdReply;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.911+08:00",
        comments = "Source field: binlog_polarx_command.cmd_reply")
    public void setCmdReply(String cmdReply) {
        this.cmdReply = cmdReply == null ? null : cmdReply.trim();
    }
}