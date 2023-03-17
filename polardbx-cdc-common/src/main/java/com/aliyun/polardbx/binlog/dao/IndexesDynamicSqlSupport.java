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
package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;

public final class IndexesDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source Table: indexes")
    public static final Indexes indexes = new Indexes();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source field: indexes.id")
    public static final SqlColumn<Long> id = indexes.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.table_catalog")
    public static final SqlColumn<String> tableCatalog = indexes.tableCatalog;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.table_schema")
    public static final SqlColumn<String> tableSchema = indexes.tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.table_name")
    public static final SqlColumn<String> tableName = indexes.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.non_unique")
    public static final SqlColumn<Long> nonUnique = indexes.nonUnique;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.index_schema")
    public static final SqlColumn<String> indexSchema = indexes.indexSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.index_name")
    public static final SqlColumn<String> indexName = indexes.indexName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.seq_in_index")
    public static final SqlColumn<Long> seqInIndex = indexes.seqInIndex;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.column_name")
    public static final SqlColumn<String> columnName = indexes.columnName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.collation")
    public static final SqlColumn<String> collation = indexes.collation;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.cardinality")
    public static final SqlColumn<Long> cardinality = indexes.cardinality;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.sub_part")
    public static final SqlColumn<Long> subPart = indexes.subPart;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.packed")
    public static final SqlColumn<String> packed = indexes.packed;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.nullable")
    public static final SqlColumn<String> nullable = indexes.nullable;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.index_type")
    public static final SqlColumn<String> indexType = indexes.indexType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.465+08:00",
        comments = "Source field: indexes.comment")
    public static final SqlColumn<String> comment = indexes.comment;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.index_comment")
    public static final SqlColumn<String> indexComment = indexes.indexComment;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.index_column_type")
    public static final SqlColumn<Long> indexColumnType = indexes.indexColumnType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.index_location")
    public static final SqlColumn<Long> indexLocation = indexes.indexLocation;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.index_table_name")
    public static final SqlColumn<String> indexTableName = indexes.indexTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.index_status")
    public static final SqlColumn<Long> indexStatus = indexes.indexStatus;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.version")
    public static final SqlColumn<Long> version = indexes.version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source field: indexes.flag")
    public static final SqlColumn<Long> flag = indexes.flag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.464+08:00",
        comments = "Source Table: indexes")
    public static final class Indexes extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> tableCatalog = column("table_catalog", JDBCType.VARCHAR);

        public final SqlColumn<String> tableSchema = column("table_schema", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> nonUnique = column("non_unique", JDBCType.BIGINT);

        public final SqlColumn<String> indexSchema = column("index_schema", JDBCType.VARCHAR);

        public final SqlColumn<String> indexName = column("index_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> seqInIndex = column("seq_in_index", JDBCType.BIGINT);

        public final SqlColumn<String> columnName = column("column_name", JDBCType.VARCHAR);

        public final SqlColumn<String> collation = column("collation", JDBCType.VARCHAR);

        public final SqlColumn<Long> cardinality = column("cardinality", JDBCType.BIGINT);

        public final SqlColumn<Long> subPart = column("sub_part", JDBCType.BIGINT);

        public final SqlColumn<String> packed = column("packed", JDBCType.VARCHAR);

        public final SqlColumn<String> nullable = column("nullable", JDBCType.VARCHAR);

        public final SqlColumn<String> indexType = column("index_type", JDBCType.VARCHAR);

        public final SqlColumn<String> comment = column("comment", JDBCType.VARCHAR);

        public final SqlColumn<String> indexComment = column("index_comment", JDBCType.VARCHAR);

        public final SqlColumn<Long> indexColumnType = column("index_column_type", JDBCType.BIGINT);

        public final SqlColumn<Long> indexLocation = column("index_location", JDBCType.BIGINT);

        public final SqlColumn<String> indexTableName = column("index_table_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> indexStatus = column("index_status", JDBCType.BIGINT);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<Long> flag = column("flag", JDBCType.BIGINT);

        public Indexes() {
            super("indexes");
        }
    }
}