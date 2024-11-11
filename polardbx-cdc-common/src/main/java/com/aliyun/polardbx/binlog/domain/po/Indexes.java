/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;

public class Indexes {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_catalog")
    private String tableCatalog;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_schema")
    private String tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_name")
    private String tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.non_unique")
    private Long nonUnique;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_schema")
    private String indexSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_name")
    private String indexName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.seq_in_index")
    private Long seqInIndex;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.column_name")
    private String columnName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.collation")
    private String collation;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.cardinality")
    private Long cardinality;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.sub_part")
    private Long subPart;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.packed")
    private String packed;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.nullable")
    private String nullable;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_type")
    private String indexType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.comment")
    private String comment;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_comment")
    private String indexComment;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_column_type")
    private Long indexColumnType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_location")
    private Long indexLocation;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_table_name")
    private String indexTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_status")
    private Long indexStatus;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.version")
    private Long version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.flag")
    private Long flag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source Table: indexes")
    public Indexes(Long id, String tableCatalog, String tableSchema, String tableName, Long nonUnique,
                   String indexSchema, String indexName, Long seqInIndex, String columnName, String collation,
                   Long cardinality, Long subPart, String packed, String nullable, String indexType, String comment,
                   String indexComment, Long indexColumnType, Long indexLocation, String indexTableName,
                   Long indexStatus, Long version, Long flag) {
        this.id = id;
        this.tableCatalog = tableCatalog;
        this.tableSchema = tableSchema;
        this.tableName = tableName;
        this.nonUnique = nonUnique;
        this.indexSchema = indexSchema;
        this.indexName = indexName;
        this.seqInIndex = seqInIndex;
        this.columnName = columnName;
        this.collation = collation;
        this.cardinality = cardinality;
        this.subPart = subPart;
        this.packed = packed;
        this.nullable = nullable;
        this.indexType = indexType;
        this.comment = comment;
        this.indexComment = indexComment;
        this.indexColumnType = indexColumnType;
        this.indexLocation = indexLocation;
        this.indexTableName = indexTableName;
        this.indexStatus = indexStatus;
        this.version = version;
        this.flag = flag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source Table: indexes")
    public Indexes() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_catalog")
    public String getTableCatalog() {
        return tableCatalog;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_catalog")
    public void setTableCatalog(String tableCatalog) {
        this.tableCatalog = tableCatalog == null ? null : tableCatalog.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_schema")
    public String getTableSchema() {
        return tableSchema;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_schema")
    public void setTableSchema(String tableSchema) {
        this.tableSchema = tableSchema == null ? null : tableSchema.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.461+08:00",
        comments = "Source field: indexes.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.non_unique")
    public Long getNonUnique() {
        return nonUnique;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.non_unique")
    public void setNonUnique(Long nonUnique) {
        this.nonUnique = nonUnique;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_schema")
    public String getIndexSchema() {
        return indexSchema;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_schema")
    public void setIndexSchema(String indexSchema) {
        this.indexSchema = indexSchema == null ? null : indexSchema.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_name")
    public String getIndexName() {
        return indexName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.index_name")
    public void setIndexName(String indexName) {
        this.indexName = indexName == null ? null : indexName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.seq_in_index")
    public Long getSeqInIndex() {
        return seqInIndex;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.seq_in_index")
    public void setSeqInIndex(Long seqInIndex) {
        this.seqInIndex = seqInIndex;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.column_name")
    public String getColumnName() {
        return columnName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.column_name")
    public void setColumnName(String columnName) {
        this.columnName = columnName == null ? null : columnName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.collation")
    public String getCollation() {
        return collation;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.collation")
    public void setCollation(String collation) {
        this.collation = collation == null ? null : collation.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.cardinality")
    public Long getCardinality() {
        return cardinality;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.cardinality")
    public void setCardinality(Long cardinality) {
        this.cardinality = cardinality;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.462+08:00",
        comments = "Source field: indexes.sub_part")
    public Long getSubPart() {
        return subPart;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.sub_part")
    public void setSubPart(Long subPart) {
        this.subPart = subPart;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.packed")
    public String getPacked() {
        return packed;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.packed")
    public void setPacked(String packed) {
        this.packed = packed == null ? null : packed.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.nullable")
    public String getNullable() {
        return nullable;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.nullable")
    public void setNullable(String nullable) {
        this.nullable = nullable == null ? null : nullable.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_type")
    public String getIndexType() {
        return indexType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_type")
    public void setIndexType(String indexType) {
        this.indexType = indexType == null ? null : indexType.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.comment")
    public String getComment() {
        return comment;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.comment")
    public void setComment(String comment) {
        this.comment = comment == null ? null : comment.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_comment")
    public String getIndexComment() {
        return indexComment;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_comment")
    public void setIndexComment(String indexComment) {
        this.indexComment = indexComment == null ? null : indexComment.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_column_type")
    public Long getIndexColumnType() {
        return indexColumnType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_column_type")
    public void setIndexColumnType(Long indexColumnType) {
        this.indexColumnType = indexColumnType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.463+08:00",
        comments = "Source field: indexes.index_location")
    public Long getIndexLocation() {
        return indexLocation;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_location")
    public void setIndexLocation(Long indexLocation) {
        this.indexLocation = indexLocation;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_table_name")
    public String getIndexTableName() {
        return indexTableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_table_name")
    public void setIndexTableName(String indexTableName) {
        this.indexTableName = indexTableName == null ? null : indexTableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_status")
    public Long getIndexStatus() {
        return indexStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.index_status")
    public void setIndexStatus(Long indexStatus) {
        this.indexStatus = indexStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.flag")
    public Long getFlag() {
        return flag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.flag")
    public void setFlag(Long flag) {
        this.flag = flag;
    }
}