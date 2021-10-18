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

package com.aliyun.polardbx.binlog.canal.core.ddl;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
public class TableMeta {

    private String schema;
    private String table;
    private List<FieldMeta> fields = new ArrayList<FieldMeta>();
    private String ddl;                                // 表结构的DDL语句
    private String charset;
    private boolean         implicitPk = false;

    public TableMeta() {

    }

    public TableMeta(String schema, String table, List<FieldMeta> fields) {
        this.schema = schema;
        this.table = table;
        this.fields = fields;
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

    public void setFields(List<FieldMeta> fileds) {
        this.fields = fileds;
    }

    public FieldMeta getFieldMetaByName(String name) {
        for (FieldMeta meta : fields) {
            if (meta.getColumnName().equalsIgnoreCase(name)) {
                return meta;
            }
        }

        throw new RuntimeException("unknow column : " + name);
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
        this.fields.add(fieldMeta);
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setImplicitPk(boolean implicitPk) {
        this.implicitPk = implicitPk;
    }

    public boolean getImplicitPk() {
        return this.implicitPk;
    }

    @Override
    public String toString() {
        StringBuilder data = new StringBuilder();
        data.append("TableMeta [schema=" + schema + ", table=" + table + ", charset=" + charset + ", fileds=");
        for (FieldMeta field : fields) {
            data.append("\n\t").append(field.toString());
        }
        data.append("\n]");
        return data.toString();
    }

    public static class FieldMeta {

        private String columnName;
        private String columnType;
        private boolean nullable;
        private boolean key;
        private String defaultValue;
        private boolean unique;
        private String charset;

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
        }

        public FieldMeta(String columnName, String columnType, boolean nullable, boolean key, String defaultValue,
                         boolean unique, String charset) {
            this.columnName = columnName;
            this.columnType = columnType;
            this.nullable = nullable;
            this.key = key;
            this.defaultValue = defaultValue;
            this.unique = unique;
            this.charset = charset;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnType() {
            return columnType;
        }

        public void setColumnType(String columnType) {
            this.columnType = columnType;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isUnsigned() {
            return StringUtils.containsIgnoreCase(columnType, "unsigned");
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
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        @Override
        public String toString() {
            return "FieldMeta [columnName=" + columnName + ", columnType=" + columnType + ", charset=" + charset
                + ", nullable=" + nullable
                + ", key=" + key + ", defaultValue=" + defaultValue + ", unique=" + unique + "]";
        }

    }

}
