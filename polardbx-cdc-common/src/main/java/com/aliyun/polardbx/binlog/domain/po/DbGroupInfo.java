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

public class DbGroupInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.354+08:00",
        comments = "Source field: db_group_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.db_name")
    private String dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.358+08:00",
        comments = "Source field: db_group_info.group_name")
    private String groupName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.phy_db_name")
    private String phyDbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.group_type")
    private Integer groupType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.349+08:00",
        comments = "Source Table: db_group_info")
    public DbGroupInfo(Long id, Date gmtCreated, Date gmtModified, String dbName, String groupName, String phyDbName,
                       Integer groupType) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.dbName = dbName;
        this.groupName = groupName;
        this.phyDbName = phyDbName;
        this.groupType = groupType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.353+08:00",
        comments = "Source Table: db_group_info")
    public DbGroupInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.356+08:00",
        comments = "Source field: db_group_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.357+08:00",
        comments = "Source field: db_group_info.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.358+08:00",
        comments = "Source field: db_group_info.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName == null ? null : dbName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.358+08:00",
        comments = "Source field: db_group_info.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.phy_db_name")
    public String getPhyDbName() {
        return phyDbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.phy_db_name")
    public void setPhyDbName(String phyDbName) {
        this.phyDbName = phyDbName == null ? null : phyDbName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.group_type")
    public Integer getGroupType() {
        return groupType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.361+08:00",
        comments = "Source field: db_group_info.group_type")
    public void setGroupType(Integer groupType) {
        this.groupType = groupType;
    }
}