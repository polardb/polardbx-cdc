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
package com.aliyun.polardbx.binlog.canal.core.ddl;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
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
public class TableMeta {

    private String schema;
    private String table;
    private List<FieldMeta> fields = new ArrayList<>();
    private String ddl;                                // 表结构的DDL语句
    private String charset;
    private boolean useImplicitPk = false;

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

    public void setFields(List<FieldMeta> fields) {
        this.fields = fields;
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
        private boolean binary;
        private boolean unsigned;
        private boolean generated;
        private boolean implicitPk;

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
            return charset;
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

}
