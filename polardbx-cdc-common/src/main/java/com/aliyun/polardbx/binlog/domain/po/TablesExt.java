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

public class TablesExt {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_catalog")
    private String tableCatalog;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_schema")
    private String tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_name")
    private String tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.new_table_name")
    private String newTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.table_type")
    private Integer tableType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_key")
    private String dbPartitionKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_policy")
    private String dbPartitionPolicy;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_count")
    private Integer dbPartitionCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_name_pattern")
    private String dbNamePattern;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_rule")
    private String dbRule;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_key")
    private String tbPartitionKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_policy")
    private String tbPartitionPolicy;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_count")
    private Integer tbPartitionCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_name_pattern")
    private String tbNamePattern;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_rule")
    private String tbRule;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.full_table_scan")
    private Integer fullTableScan;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.broadcast")
    private Integer broadcast;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.version")
    private Long version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.flag")
    private Long flag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.db_meta_map")
    private String dbMetaMap;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.tb_meta_map")
    private String tbMetaMap;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.ext_partitions")
    private String extPartitions;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.397+08:00",
        comments = "Source Table: tables_ext")
    public TablesExt(Long id, String tableCatalog, String tableSchema, String tableName, String newTableName,
                     Integer tableType, String dbPartitionKey, String dbPartitionPolicy, Integer dbPartitionCount,
                     String dbNamePattern, String dbRule, String tbPartitionKey, String tbPartitionPolicy,
                     Integer tbPartitionCount, String tbNamePattern, String tbRule, Integer fullTableScan,
                     Integer broadcast, Long version, Integer status, Long flag, String dbMetaMap, String tbMetaMap,
                     String extPartitions) {
        this.id = id;
        this.tableCatalog = tableCatalog;
        this.tableSchema = tableSchema;
        this.tableName = tableName;
        this.newTableName = newTableName;
        this.tableType = tableType;
        this.dbPartitionKey = dbPartitionKey;
        this.dbPartitionPolicy = dbPartitionPolicy;
        this.dbPartitionCount = dbPartitionCount;
        this.dbNamePattern = dbNamePattern;
        this.dbRule = dbRule;
        this.tbPartitionKey = tbPartitionKey;
        this.tbPartitionPolicy = tbPartitionPolicy;
        this.tbPartitionCount = tbPartitionCount;
        this.tbNamePattern = tbNamePattern;
        this.tbRule = tbRule;
        this.fullTableScan = fullTableScan;
        this.broadcast = broadcast;
        this.version = version;
        this.status = status;
        this.flag = flag;
        this.dbMetaMap = dbMetaMap;
        this.tbMetaMap = tbMetaMap;
        this.extPartitions = extPartitions;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source Table: tables_ext")
    public TablesExt() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_catalog")
    public String getTableCatalog() {
        return tableCatalog;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_catalog")
    public void setTableCatalog(String tableCatalog) {
        this.tableCatalog = tableCatalog == null ? null : tableCatalog.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_schema")
    public String getTableSchema() {
        return tableSchema;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_schema")
    public void setTableSchema(String tableSchema) {
        this.tableSchema = tableSchema == null ? null : tableSchema.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.398+08:00",
        comments = "Source field: tables_ext.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.new_table_name")
    public String getNewTableName() {
        return newTableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.new_table_name")
    public void setNewTableName(String newTableName) {
        this.newTableName = newTableName == null ? null : newTableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.table_type")
    public Integer getTableType() {
        return tableType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.table_type")
    public void setTableType(Integer tableType) {
        this.tableType = tableType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_key")
    public String getDbPartitionKey() {
        return dbPartitionKey;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_key")
    public void setDbPartitionKey(String dbPartitionKey) {
        this.dbPartitionKey = dbPartitionKey == null ? null : dbPartitionKey.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_policy")
    public String getDbPartitionPolicy() {
        return dbPartitionPolicy;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_policy")
    public void setDbPartitionPolicy(String dbPartitionPolicy) {
        this.dbPartitionPolicy = dbPartitionPolicy == null ? null : dbPartitionPolicy.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_count")
    public Integer getDbPartitionCount() {
        return dbPartitionCount;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.399+08:00",
        comments = "Source field: tables_ext.db_partition_count")
    public void setDbPartitionCount(Integer dbPartitionCount) {
        this.dbPartitionCount = dbPartitionCount;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_name_pattern")
    public String getDbNamePattern() {
        return dbNamePattern;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_name_pattern")
    public void setDbNamePattern(String dbNamePattern) {
        this.dbNamePattern = dbNamePattern == null ? null : dbNamePattern.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_rule")
    public String getDbRule() {
        return dbRule;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.db_rule")
    public void setDbRule(String dbRule) {
        this.dbRule = dbRule == null ? null : dbRule.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_key")
    public String getTbPartitionKey() {
        return tbPartitionKey;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_key")
    public void setTbPartitionKey(String tbPartitionKey) {
        this.tbPartitionKey = tbPartitionKey == null ? null : tbPartitionKey.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_policy")
    public String getTbPartitionPolicy() {
        return tbPartitionPolicy;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_policy")
    public void setTbPartitionPolicy(String tbPartitionPolicy) {
        this.tbPartitionPolicy = tbPartitionPolicy == null ? null : tbPartitionPolicy.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_count")
    public Integer getTbPartitionCount() {
        return tbPartitionCount;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.4+08:00",
        comments = "Source field: tables_ext.tb_partition_count")
    public void setTbPartitionCount(Integer tbPartitionCount) {
        this.tbPartitionCount = tbPartitionCount;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_name_pattern")
    public String getTbNamePattern() {
        return tbNamePattern;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_name_pattern")
    public void setTbNamePattern(String tbNamePattern) {
        this.tbNamePattern = tbNamePattern == null ? null : tbNamePattern.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_rule")
    public String getTbRule() {
        return tbRule;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.tb_rule")
    public void setTbRule(String tbRule) {
        this.tbRule = tbRule == null ? null : tbRule.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.full_table_scan")
    public Integer getFullTableScan() {
        return fullTableScan;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.full_table_scan")
    public void setFullTableScan(Integer fullTableScan) {
        this.fullTableScan = fullTableScan;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.broadcast")
    public Integer getBroadcast() {
        return broadcast;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.broadcast")
    public void setBroadcast(Integer broadcast) {
        this.broadcast = broadcast;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.401+08:00",
        comments = "Source field: tables_ext.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.flag")
    public Long getFlag() {
        return flag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.flag")
    public void setFlag(Long flag) {
        this.flag = flag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.db_meta_map")
    public String getDbMetaMap() {
        return dbMetaMap;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.db_meta_map")
    public void setDbMetaMap(String dbMetaMap) {
        this.dbMetaMap = dbMetaMap == null ? null : dbMetaMap.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.tb_meta_map")
    public String getTbMetaMap() {
        return tbMetaMap;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.tb_meta_map")
    public void setTbMetaMap(String tbMetaMap) {
        this.tbMetaMap = tbMetaMap == null ? null : tbMetaMap.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.402+08:00",
        comments = "Source field: tables_ext.ext_partitions")
    public String getExtPartitions() {
        return extPartitions;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.403+08:00",
        comments = "Source field: tables_ext.ext_partitions")
    public void setExtPartitions(String extPartitions) {
        this.extPartitions = extPartitions == null ? null : extPartitions.trim();
    }
}