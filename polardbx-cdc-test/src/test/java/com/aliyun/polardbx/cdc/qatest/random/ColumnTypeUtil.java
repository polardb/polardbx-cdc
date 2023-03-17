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
package com.aliyun.polardbx.cdc.qatest.random;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * created by ziyang.lb
 **/
public class ColumnTypeUtil {

    public static boolean isBit(String columnType) {
        return columnType.startsWith("bit");
    }

    public static boolean isJson(String columnType) {
        return columnType.startsWith("json");
    }

    public static boolean isGeometry(String columnType) {
        return columnType.startsWith("geometry");
    }

    // boolean只是个语法糖，mysql底层对应的是tinyint(1)类型
    public static boolean isBoolean(String columnType) {
        return columnType.startsWith("boolean");
    }

    public static boolean isTextOrBlob(String columnType) {
        return columnType.startsWith("text") || columnType.startsWith("tinytext") ||
            columnType.startsWith("mediumtext") || columnType.startsWith("longtext") ||
            columnType.startsWith("tinyblob") || columnType.startsWith("blob") ||
            columnType.startsWith("mediumblob") || columnType.startsWith("longblob");
    }

    public static boolean isTime(String columnType) {
        return columnType.startsWith("year") || columnType.startsWith("time") ||
            columnType.startsWith("datetime") || columnType.startsWith("date") ||
            columnType.startsWith("timestamp");
    }

    public static boolean isVarCharOrBinary(String columnType) {
        return columnType.startsWith("varchar") || columnType.startsWith("char") ||
            columnType.startsWith("binary") || columnType.startsWith("varbinary");
    }

    public static boolean isNumberic(String columnType) {
        return columnType.startsWith("tinyint") || columnType.startsWith("smallint") ||
            columnType.startsWith("mediumint") || columnType.startsWith("int") ||
            columnType.startsWith("bigint") || columnType.startsWith("double") ||
            columnType.startsWith("float") || columnType.startsWith("decimal") ||
            columnType.startsWith("numeric") || columnType.startsWith("dec");
    }

    public static boolean isCompatibleTime(String fromType, String toType) {
        if (fromType.startsWith("year")) {
            return false;
        } else if (fromType.startsWith("time")) {
            return toType.startsWith("time");
        } else if (fromType.startsWith("datetime")) {
            return toType.startsWith("time") || toType.startsWith("timestamp") || isDate(toType);
        } else if (isDate(fromType)) {
            return toType.startsWith("datetime") || toType.startsWith("timestamp");
        } else if (fromType.startsWith("timestamp")) {
            return toType.startsWith("datetime") || toType.startsWith("time") || isDate(toType);
        } else {
            throw new PolardbxException("invalid time type : " + fromType);
        }
    }

    public static Pair<Integer, Integer> parseLength(String columnType) {
        if (!columnType.contains("(")) {
            return Pair.of(0, 0);
        }
        String str1 = StringUtils.substringAfter(columnType, "(");
        String str2 = StringUtils.substringBefore(str1, ")");
        if (str2.contains(",")) {
            String[] array = StringUtils.split(str2, ",");
            int M = Integer.parseInt(array[0]);
            int D = Integer.parseInt(array[1]);
            return Pair.of(M, D);
        } else {
            return Pair.of(Integer.parseInt(str2), 0);
        }
    }

    public static boolean isFloatingType(String columnType) {
        return columnType.startsWith("float") || columnType.startsWith("double") || columnType.startsWith("decimal")
            || columnType.startsWith("dec") || columnType.startsWith("numeric");
    }

    public static boolean isFloatingWithZeroFraction(String columnType) {
        return isFloatingType(columnType) && parseLength(columnType).getValue() == 0;
    }

    private static boolean isDate(String columnType) {
        return columnType.startsWith("date") && !columnType.startsWith("datet");
    }
}
