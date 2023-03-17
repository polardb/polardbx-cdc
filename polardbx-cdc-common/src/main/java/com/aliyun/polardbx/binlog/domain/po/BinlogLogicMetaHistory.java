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

import lombok.Builder;

import javax.annotation.Generated;
import java.util.Date;

@Builder
public class BinlogLogicMetaHistory {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.353+08:00",
        comments = "Source field: binlog_logic_meta_history.id")
    private Integer id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.tso")
    private String tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.db_name")
    private String dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.table_name")
    private String tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.sql_kind")
    private String sqlKind;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.type")
    private Byte type;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_record_id")
    private Long ddlRecordId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_job_id")
    private Long ddlJobId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.instruction_id")
    private String instructionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl")
    private String ddl;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.topology")
    private String topology;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ext_info")
    private String extInfo;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.35+08:00",
        comments = "Source Table: binlog_logic_meta_history")
    public BinlogLogicMetaHistory(Integer id, Date gmtCreated, Date gmtModified, String tso, String dbName,
                                  String tableName, String sqlKind, Byte type, Long ddlRecordId, Long ddlJobId,
                                  String instructionId, String ddl, String topology, String extInfo) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.dbName = dbName;
        this.tableName = tableName;
        this.sqlKind = sqlKind;
        this.type = type;
        this.ddlRecordId = ddlRecordId;
        this.ddlJobId = ddlJobId;
        this.instructionId = instructionId;
        this.ddl = ddl;
        this.topology = topology;
        this.extInfo = extInfo;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.353+08:00",
        comments = "Source Table: binlog_logic_meta_history")
    public BinlogLogicMetaHistory() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.id")
    public Integer getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.354+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName == null ? null : dbName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.sql_kind")
    public String getSqlKind() {
        return sqlKind;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.sql_kind")
    public void setSqlKind(String sqlKind) {
        this.sqlKind = sqlKind == null ? null : sqlKind.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.type")
    public Byte getType() {
        return type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.type")
    public void setType(Byte type) {
        this.type = type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_record_id")
    public Long getDdlRecordId() {
        return ddlRecordId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_record_id")
    public void setDdlRecordId(Long ddlRecordId) {
        this.ddlRecordId = ddlRecordId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_job_id")
    public Long getDdlJobId() {
        return ddlJobId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.355+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl_job_id")
    public void setDdlJobId(Long ddlJobId) {
        this.ddlJobId = ddlJobId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.instruction_id")
    public String getInstructionId() {
        return instructionId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.instruction_id")
    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId == null ? null : instructionId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl")
    public String getDdl() {
        return ddl;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl")
    public void setDdl(String ddl) {
        this.ddl = ddl == null ? null : ddl.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.topology")
    public String getTopology() {
        return topology;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.topology")
    public void setTopology(String topology) {
        this.topology = topology == null ? null : topology.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ext_info")
    public String getExtInfo() {
        return extInfo;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2022-08-01T19:47:23.356+08:00",
        comments = "Source field: binlog_logic_meta_history.ext_info")
    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo == null ? null : extInfo.trim();
    }
}
