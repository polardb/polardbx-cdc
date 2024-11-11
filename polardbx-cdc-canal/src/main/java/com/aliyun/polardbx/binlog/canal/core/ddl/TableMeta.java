/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 描述数据meta对象,mysql
 * binlog中对应的{@linkplain com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent}包含的信息不全
 *
 * <pre>
 * 1. 主键信息
 * 2. column name
 * 3. unsigned字段
 * </pre>
 *
 * @author jianghang 2013-1-18 下午12:24:59
 * @version 1.0.0
 */
public class TableMeta implements Serializable {

    private String schema;
    private String table;
    private List<FieldMeta> fields = new ArrayList<>();
    private String ddl;                                // 表结构的DDL语句
    private String charset;
    private boolean useImplicitPk = false;
    private Map<String, IndexMeta> indexes = new HashMap<>();

    public TableMeta() {

    }

    public TableMeta(String schema, String table, List<FieldMeta> fields) {
        this.schema = schema;
        this.table = table;
        this.fields = fields;
        initParent();
    }

    private void initParent() {
        for (FieldMeta fm : fields) {
            fm.setParent(this);
        }
    }

    public String getFullName() {
        return schema + "." + table;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public List<FieldMeta> getFields() {
        return fields;
    }

    public Map<String, IndexMeta> getIndexes() {
        return indexes;
    }

    public void setIndexes(
        Map<String, IndexMeta> indexes) {
        this.indexes = indexes;
    }

    public void setFields(List<FieldMeta> fields) {
        this.fields = fields;
        initParent();
    }

    public FieldMeta getFieldMetaByName(String name) {
        return getFieldMetaByName(name, false);
    }

    public FieldMeta getFieldMetaByName(String name, boolean returnNullIfNotExist) {
        for (FieldMeta meta : fields) {
            if (meta.getColumnName().equalsIgnoreCase(name)) {
                return meta;
            }
        }

        if (returnNullIfNotExist) {
            return null;
        } else {
            throw new RuntimeException("unknown column : " + name + " for table :" + table);
        }
    }

    public List<FieldMeta> getPrimaryFields() {
        List<FieldMeta> primarys = new ArrayList<FieldMeta>();
        for (FieldMeta meta : fields) {
            if (meta.isKey()) {
                primarys.add(meta);
            }
        }

        return primarys;
    }

    public String getDdl() {
        return ddl;
    }

    public void setDdl(String ddl) {
        this.ddl = ddl;
    }

    public void addFieldMeta(FieldMeta fieldMeta) {
        fieldMeta.setParent(this);
        this.fields.add(fieldMeta);
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = SQLUtils.normalize(charset);
        preProcessCharset();
    }

    public boolean getUseImplicitPk() {
        return this.useImplicitPk;
    }

    public void setUseImplicitPk(boolean implicitPk) {
        this.useImplicitPk = implicitPk;
    }

    //https://dev.mysql.com/doc/refman/8.0/en/charset-unicode-utf8mb3.html
    private void preProcessCharset() {
        if ("utf8mb3".equalsIgnoreCase(charset)) {
            charset = "utf8";
        }
    }

    @Override
    public String toString() {
        StringBuilder data = new StringBuilder();
        data.append("TableMeta [schema=" + schema + ", table=" + table + ", charset=" + charset + ", fileds=");
        for (FieldMeta field : fields) {
            data.append("\n\t").append(field.toString());
        }
        for (IndexMeta indexMeta : indexes.values()) {
            data.append("\n\t").append(indexMeta.toString());
        }
        data.append("\n]");
        return data.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TableMeta tableMeta = (TableMeta) o;
        return useImplicitPk == tableMeta.useImplicitPk && Objects.equals(schema, tableMeta.schema)
            && Objects.equals(table, tableMeta.table) && Objects.equals(fields, tableMeta.fields)
            && Objects.equals(ddl, tableMeta.ddl) && Objects.equals(charset, tableMeta.charset)
            && Objects.equals(indexes, tableMeta.indexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, table, fields, ddl, charset, useImplicitPk, indexes);
    }

    public static class FieldMeta implements Serializable {

        private String columnName;
        private String columnType;
        private boolean nullable;
        private boolean key;
        private String defaultValue;
        private boolean unique;
        private String charset;
        private boolean binary;
        private boolean unsigned;
        private boolean generated;
        private boolean implicitPk;
        private boolean onUpdate;
        private TableMeta parent;

        public FieldMeta() {

        }

        public FieldMeta(String columnName, String columnType, boolean nullable, boolean key, String defaultValue,
                         boolean unique) {
            this.columnName = columnName;
            this.columnType = columnType;
            this.nullable = nullable;
            this.key = key;
            this.defaultValue = defaultValue;
            this.unique = unique;
            preProcessColumnName();
            preProcessColumnType();
        }

        public FieldMeta(String columnName, String columnType, boolean nullable, boolean key, String defaultValue,
                         boolean unique, String charset) {
            this.columnName = columnName;
            this.columnType = columnType;
            this.nullable = nullable;
            this.key = key;
            this.defaultValue = defaultValue;
            this.unique = unique;
            this.charset = SQLUtils.normalize(charset);
            preProcessColumnName();
            preProcessCharset();
            preProcessColumnType();
        }

        public void setParent(TableMeta parent) {
            this.parent = parent;
        }

        private void preProcessColumnType() {
            binary = StringUtils.containsIgnoreCase(columnType, "VARBINARY")
                || StringUtils.containsIgnoreCase(columnType, "BINARY");
            unsigned = StringUtils.containsIgnoreCase(columnType, "unsigned");
        }

        private void preProcessCharset() {
            if ("utf8mb3".equalsIgnoreCase(charset)) {
                charset = "utf8";
            }
        }

        private void preProcessColumnName() {
            if ("__#alibaba_rds_row_id#__".equalsIgnoreCase(columnName)) {
                implicitPk = true;
            }
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
            preProcessColumnName();
        }

        public String getColumnType() {
            return columnType;
        }

        public void setColumnType(String columnType) {
            this.columnType = columnType;
            preProcessColumnType();
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isUnsigned() {
            return unsigned;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }

        public boolean isKey() {
            return key;
        }

        public void setKey(boolean key) {
            this.key = key;
        }

        public boolean isUnique() {
            return unique;
        }

        public void setUnique(boolean unique) {
            this.unique = unique;
        }

        public String getCharset() {
            if (StringUtils.isNotBlank(charset)) {
                return charset;
            }
            if (parent != null) {
                return parent.getCharset();
            }
            return null;
        }

        public void setCharset(String charset) {
            this.charset = SQLUtils.normalize(charset);
            preProcessCharset();
        }

        public void setImplicitPk(boolean implicitPk) {
            this.implicitPk = implicitPk;
        }

        public boolean isBinary() {
            return binary;
        }

        public boolean isGenerated() {
            return generated;
        }

        public boolean isImplicitPk() {
            return implicitPk;
        }

        public void setGenerated(boolean generated) {
            this.generated = generated;
        }

        public boolean isOnUpdate() {
            return onUpdate;
        }

        public void setIsOnUpdate(boolean onUpdate) {
            this.onUpdate = onUpdate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FieldMeta fieldMeta = (FieldMeta) o;
            return nullable == fieldMeta.nullable &&
                key == fieldMeta.key &&
                unique == fieldMeta.unique &&
                binary == fieldMeta.binary &&
                unsigned == fieldMeta.unsigned &&
                generated == fieldMeta.generated &&
                implicitPk == fieldMeta.implicitPk &&
                Objects.equals(columnName, fieldMeta.columnName) &&
                Objects.equals(columnType, fieldMeta.columnType) &&
                Objects.equals(defaultValue, fieldMeta.defaultValue) &&
                Objects.equals(charset, fieldMeta.charset);
        }

        @Override
        public int hashCode() {
            return Objects
                .hash(columnName, columnType, nullable, key, defaultValue, unique, charset, binary, unsigned, generated,
                    implicitPk);
        }

        @Override
        public String toString() {
            return "FieldMeta ["
                + "columnName=" + columnName
                + ", columnType=" + columnType
                + ", charset=" + charset
                + ", nullable=" + nullable
                + ", key=" + key
                + ", defaultValue=" + defaultValue
                + ", unique=" + unique
                + ", binary=" + binary
                + ", unsigned=" + unsigned
                + ", generated=" + generated
                + ", implicitPk=" + implicitPk
                + "]";
        }

    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexMeta implements Serializable {
        private String indexName;
        private String indexType;
        private boolean columnar;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IndexMeta indexMeta = (IndexMeta) o;
            return columnar == indexMeta.columnar && Objects.equals(indexName, indexMeta.indexName)
                && Objects.equals(indexType, indexMeta.indexType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(indexName, indexType, columnar);
        }
    }
}
