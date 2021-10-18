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

import javax.annotation.Generated;
import java.util.Date;

public class BinlogPolarxCommand {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.625+08:00",
        comments = "Source field: binlog_polarx_command.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.type")
    private Integer type;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.command")
    private String command;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.ext")
    private String ext;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.reply")
    private String reply;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.622+08:00",
        comments = "Source Table: binlog_polarx_command")
    public BinlogPolarxCommand(Long id, Date gmtCreated, Date gmtModified, Integer type, String command, String ext,
                               String reply) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.type = type;
        this.command = command;
        this.ext = ext;
        this.reply = reply;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.625+08:00",
        comments = "Source Table: binlog_polarx_command")
    public BinlogPolarxCommand() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.626+08:00",
        comments = "Source field: binlog_polarx_command.type")
    public Integer getType() {
        return type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.type")
    public void setType(Integer type) {
        this.type = type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.command")
    public String getCommand() {
        return command;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.command")
    public void setCommand(String command) {
        this.command = command == null ? null : command.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.ext")
    public String getExt() {
        return ext;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.ext")
    public void setExt(String ext) {
        this.ext = ext == null ? null : ext.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.reply")
    public String getReply() {
        return reply;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.627+08:00",
        comments = "Source field: binlog_polarx_command.reply")
    public void setReply(String reply) {
        this.reply = reply == null ? null : reply.trim();
    }
}