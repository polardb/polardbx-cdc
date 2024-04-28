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
package com.aliyun.polardbx.binlog.format.field.datatype;

import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.Charset;

@Data
public class CreateField {

    private static final Logger logger = LoggerFactory.getLogger(CreateField.class);
    private String dataType;
    private boolean explicitWidth;
    private int codepoint;
    private String[] parameters;

    private Serializable defaultValue;

    private Charset charset = CollationCharset.defaultJavaCharset;

    private String mysqlCharset;

    private boolean nullable;

    private SqlTypeConvert convertType;

    private boolean unsigned;

    private String realType;

    public static CreateField parse(String dataType, Serializable defaultValue, String mysqlCharset, boolean nullable,
                                    boolean unsigned) {
        checkValueBefore(dataType, defaultValue, mysqlCharset, nullable, unsigned);
        CreateField type = new CreateField();
        dataType = dataType.trim();
        int k = dataType.indexOf("(");
        String convertType;
        if (k > 0) {
            String sqlType = dataType.substring(0, k);
            int e = dataType.indexOf(")");
            dataType = dataType.substring(0, e + 1);
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
                    String p0 = type.parameters[0];
                    if (NumberUtils.isDigits(p0)) {
                        type.codepoint = Integer.parseInt(type.parameters[0]);
                    }
                }
            }
        } else {
            convertType = dataType.split(" ")[0].toUpperCase();
        }
        type.realType = convertType;
        SqlTypeConvert needConvert = SqlTypeConvert.findConverter(convertType);
        if (needConvert != null) {
            convertType = needConvert.innerType;
            type.convertType = needConvert;
        }
        type.dataType = "MYSQL_TYPE_" + convertType;
        type.defaultValue = defaultValue;
        String javaCharset = CharsetConversion.getJavaCharset(mysqlCharset);
        if (StringUtils.isBlank(javaCharset)) {
            throw new PolardbxException("can not find java charset for charset : " + mysqlCharset);
        }
        type.mysqlCharset = mysqlCharset;
        type.charset = Charset.forName(javaCharset);
        type.nullable = nullable;
        type.unsigned = unsigned;

        return type;
    }

    private static void checkValueBefore(String dataType, Serializable defaultValue, String mysqlCharset,
                                         boolean nullable,
                                         boolean unsigned) {
        if (defaultValue == null && !nullable) {
            logger.error("check nullable flag false , but receive null value , dataType is " + dataType);
        }
    }

    public static enum SqlTypeConvert {
        TINYINT("TINY"),
        SMALLINT("SHORT"),
        MEDIUM("INT24"),
        MEDIUMINT("INT24"),
        INT("LONG"),
        INTEGER("LONG"),
        BIGINT("LONGLONG"),
        TEXT("BLOB"),
        LONGTEXT("LONGBLOB"),
        MEDIUMTEXT("MEDIUMBLOB"),
        TINYTEXT("TINYBLOB"),
        CHAR("STRING"),
        BINARY("STRING"),
        VARBINARY("VARCHAR"),
        DATE("NEWDATE"),
        POINT("GEOMETRY"),
        CURVE("GEOMETRY"),
        LINESTRING("GEOMETRY"),
        LINE("GEOMETRY"),
        LINEARRING("GEOMETRY"),
        SURFACE("GEOMETRY"),
        POLYGON("GEOMETRY"),
        GEOMETRYCOLLECTION("GEOMETRY"),
        MULTIPOINT("GEOMETRY"),
        MULTICURVE("GEOMETRY"),
        MULTILINESTRING("GEOMETRY"),
        MULTISURFACE("GEOMETRY"),
        MULTIPOLYGON("GEOMETRY"),
        NUMERIC("NEWDECIMAL"),
        DEC("NEWDECIMAL"),
        BOOLEAN("TINY"),
        BOOL("TINY"),
        DATETIME("DATETIME2"),
        TIMESTAMP("TIMESTAMP2"),
        TIME("TIME2");

        private final String innerType;

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
