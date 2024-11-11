/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class BinlogMetaSnapshot {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.368+08:00",
        comments = "Source field: binlog_meta_snapshot.id")
    private Integer id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.tso")
    private String tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.db_name")
    private String dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.table_name")
    private String tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.table_schema")
    private String tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.365+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    public BinlogMetaSnapshot(Integer id, Date gmtCreated, Date gmtModified, String tso, String dbName,
                              String tableName, String tableSchema) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.dbName = dbName;
        this.tableName = tableName;
        this.tableSchema = tableSchema;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.368+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    public BinlogMetaSnapshot() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.id")
    public Integer getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.369+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName == null ? null : dbName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.37+08:00",
        comments = "Source field: binlog_meta_snapshot.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.371+08:00",
        comments = "Source field: binlog_meta_snapshot.table_schema")
    public String getTableSchema() {
        return tableSchema;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.371+08:00",
        comments = "Source field: binlog_meta_snapshot.table_schema")
    public void setTableSchema(String tableSchema) {
        this.tableSchema = tableSchema == null ? null : tableSchema.trim();
    }
}