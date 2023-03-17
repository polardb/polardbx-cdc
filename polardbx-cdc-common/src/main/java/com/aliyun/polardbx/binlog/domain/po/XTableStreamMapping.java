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

public class XTableStreamMapping {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.403+08:00", comments="Source field: binlog_x_table_stream_mapping.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.403+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.db_name")
    private String dbName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.table_name")
    private String tableName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.stream_seq")
    private Long streamSeq;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.405+08:00", comments="Source field: binlog_x_table_stream_mapping.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.399+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    public XTableStreamMapping(Long id, Date gmtCreated, Date gmtModified, String dbName, String tableName, Long streamSeq, String clusterId) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.dbName = dbName;
        this.tableName = tableName;
        this.streamSeq = streamSeq;
        this.clusterId = clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.402+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    public XTableStreamMapping() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.403+08:00", comments="Source field: binlog_x_table_stream_mapping.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.403+08:00", comments="Source field: binlog_x_table_stream_mapping.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.403+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName == null ? null : dbName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.stream_seq")
    public Long getStreamSeq() {
        return streamSeq;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.404+08:00", comments="Source field: binlog_x_table_stream_mapping.stream_seq")
    public void setStreamSeq(Long streamSeq) {
        this.streamSeq = streamSeq;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.405+08:00", comments="Source field: binlog_x_table_stream_mapping.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.405+08:00", comments="Source field: binlog_x_table_stream_mapping.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }
}