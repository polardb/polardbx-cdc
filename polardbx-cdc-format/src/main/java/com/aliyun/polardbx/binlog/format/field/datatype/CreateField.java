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
package com.aliyun.polardbx.binlog.format.field.datatype;

import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import lombok.Data;

import java.nio.charset.Charset;

@Data
public class CreateField {

    private String dataType;
    private boolean explicitWidth;
    private int codepoint;
    private String[] parameters;

    private String defaultValue;

    private Charset charset = CollationCharset.defaultJavaCharset;

    private String mysqlCharset;

    private boolean nullable;

    private SqlTypeConvert convertType;

    public static CreateField parse(String dataType, String defaultValue, String charset, boolean nullable) {
        CreateField type = new CreateField();
        int k = dataType.indexOf("(");
        String convertType;
        if (k > 0) {
            String sqlType = dataType.substring(0, k);
            int e = dataType.indexOf(")");
            String parametsrStr = dataType.substring(k + 1, e);
            convertType = sqlType.toUpperCase();
            String[] pps = parametsrStr.split(",");
            type.parameters = new String[pps.length];
            for (int i = 0; i < pps.length; i++) {
                type.parameters[i] = pps[i].trim();
            }
            if (type.parameters.length > 0) {
                type.explicitWidth = true;
                if (type.parameters.length == 1) {
                    type.codepoint = Integer.parseInt(type.parameters[0]);
                }
            }
        } else {
            convertType = dataType.trim().toUpperCase();
        }
        SqlTypeConvert needConvert = SqlTypeConvert.findConverter(convertType);
        if (needConvert != null) {
            convertType = needConvert.innerType;
            type.convertType = needConvert;
        }
        type.dataType = convertType;
        type.dataType = "MYSQL_TYPE_" + type.dataType;
        if (defaultValue == null || defaultValue.equalsIgnoreCase("null")) {
            type.defaultValue = null;
        } else {
            type.defaultValue = defaultValue;
        }
        String javaCharset = CharsetConversion.getJavaCharset(charset);
        type.mysqlCharset = charset;
        type.charset = Charset.forName(javaCharset);
        type.nullable = nullable;

        return type;
    }

    public static enum SqlTypeConvert {
        TINYINT("TINY"), SMALLINT("SHORT"), MEDIUM("INT24"), MEDIUMINT("INT24"), INT("LONG"), INTEGER("LONG"), BIGINT(
            "LONGLONG"), TEXT("BLOB"), LONGTEXT("BLOB"), MEDIUMTEXT("BLOB"), TINYTEXT("BLOB"), CHAR("STRING"),
        BINARY("STRING"),
        VARBINARY("VARCHAR"), DATE("NEWDATE"), POINT(
            "GEOMETRY"), CURVE("GEOMETRY"), LINESTRING("GEOMETRY"), LINE("GEOMETRY"), LINEARRING("GEOMETRY"), SURFACE(
            "GEOMETRY"), POLYGON("GEOMETRY"), GEOMETRYCOLLECTION("GEOMETRY"), MULTIPOINT("GEOMETRY"), MULTICURVE(
            "GEOMETRY"), MULTILINESTRING("GEOMETRY"), MULTISURFACE("GEOMETRY"), MULTIPOLYGON("GEOMETRY"), NUMERIC(
            "NEWDECIMAL"), DEC("NEWDECIMAL"), BOOLEAN("TINY"), BOOL("TINY"),
        ;
        private String innerType;

        SqlTypeConvert(String innerType) {
            this.innerType = innerType;
        }

        public static SqlTypeConvert findConverter(String name) {
            for (SqlTypeConvert convert : values()) {
                if (convert.name().equalsIgnoreCase(name)) {
                    return convert;
                }
            }
            return null;
        }
    }
}
