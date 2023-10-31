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
package com.aliyun.polardbx.binlog.format.utils;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SqlModeUtil {

    /* Bits for different SQL modes modes (including ANSI mode) */
    public static long MODE_REAL_AS_FLOAT = 0x00000001;

    public static long MODE_PIPES_AS_CONCAT = 0x00000002;

    public static long MODE_ANSI_QUOTES = 0x00000004;

    public static long MODE_IGNORE_SPACE = 0x00000008;

    public static long MODE_NOT_USED = 0x00000010;

    public static long MODE_ONLY_FULL_GROUP_BY = 0x00000020;

    public static long MODE_NO_UNSIGNED_SUBTRACTION = 0x00000040;

    public static long MODE_NO_DIR_IN_CREATE = 0x00000080;

    public static long MODE_POSTGRESQL = 0x00000100;

    public static long MODE_ORACLE = 0x00000200;

    public static long MODE_MSSQL = 0x00000400;

    public static long MODE_DB2 = 0x00000800;

    public static long MODE_MAXDB = 0x00001000;

    public static long MODE_NO_KEY_OPTIONS = 0x00002000;

    public static long MODE_NO_TABLE_OPTIONS = 0x00004000;

    public static long MODE_NO_FIELD_OPTIONS = 0x00008000;

    public static long MODE_MYSQL323 = 0x00010000;

    public static long MODE_MYSQL40 = 0x00020000;

    public static long MODE_ANSI = 0x00040000;

    public static long MODE_NO_AUTO_VALUE_ON_ZERO = 0x00080000;

    public static long MODE_NO_BACKSLASH_ESCAPES = 0x00100000;

    public static long MODE_STRICT_TRANS_TABLES = 0x00200000;

    public static long MODE_STRICT_ALL_TABLES = 0x00400000;

    public static long MODE_NO_ZERO_IN_DATE = 0x00800000;

    public static long MODE_NO_ZERO_DATE = 0x01000000;

    public static long MODE_INVALID_DATES = 0x02000000;

    public static long MODE_ERROR_FOR_DIVISION_BY_ZERO = 0x04000000;

    public static long MODE_TRADITIONAL = 0x08000000;

    public static long MODE_NO_AUTO_CREATE_USER = 0x10000000;

    public static long MODE_HIGH_NOT_PRECEDENCE = 0x20000000;

    public static long MODE_NO_ENGINE_SUBSTITUTION = 0x40000000;

    public static long MODE_PAD_CHAR_TO_FULL_LENGTH = 0x80000000;

    private static final Map<Long, String> SQL_MODE_MAP = new HashMap<>(64);

    // 理论上来说sql mode在同一个binlog中不可能太多，因此不对该map的size做限制
    private static final Map<Long, String> SQL_MODE_MAPPING_CACHE = new HashMap<>(64);

    static {
        SQL_MODE_MAPPING_CACHE.put(0L, "");
        SQL_MODE_MAP.put(MODE_REAL_AS_FLOAT, "REAL_AS_FLOAT");
        SQL_MODE_MAP.put(MODE_PIPES_AS_CONCAT, "PIPES_AS_CONCAT");
        SQL_MODE_MAP.put(MODE_ANSI_QUOTES, "ANSI_QUOTES");
        SQL_MODE_MAP.put(MODE_IGNORE_SPACE, "IGNORE_SPACE");
        SQL_MODE_MAP.put(MODE_NOT_USED, "NOT_USED");
        SQL_MODE_MAP.put(MODE_ONLY_FULL_GROUP_BY, "ONLY_FULL_GROUP_BY");
        SQL_MODE_MAP.put(MODE_NO_UNSIGNED_SUBTRACTION, "NO_UNSIGNED_SUBTRACTION");
        SQL_MODE_MAP.put(MODE_NO_DIR_IN_CREATE, "NO_DIR_IN_CREATE");
        SQL_MODE_MAP.put(MODE_POSTGRESQL, "POSTGRESQL");
        SQL_MODE_MAP.put(MODE_ORACLE, "ORACLE");
        SQL_MODE_MAP.put(MODE_MSSQL, "MSSQL");
        SQL_MODE_MAP.put(MODE_DB2, "DB2");
        SQL_MODE_MAP.put(MODE_MAXDB, "MAXDB");
        SQL_MODE_MAP.put(MODE_NO_KEY_OPTIONS, "NO_KEY_OPTIONS");
        SQL_MODE_MAP.put(MODE_NO_TABLE_OPTIONS, "NO_TABLE_OPTIONS");
        SQL_MODE_MAP.put(MODE_NO_FIELD_OPTIONS, "NO_FIELD_OPTIONS");
        SQL_MODE_MAP.put(MODE_MYSQL323, "MYSQL323");
        SQL_MODE_MAP.put(MODE_MYSQL40, "MYSQL40");
        SQL_MODE_MAP.put(MODE_ANSI, "ANSI");
        SQL_MODE_MAP.put(MODE_NO_AUTO_VALUE_ON_ZERO, "NO_AUTO_VALUE_ON_ZERO");
        SQL_MODE_MAP.put(MODE_NO_BACKSLASH_ESCAPES, "NO_BACKSLASH_ESCAPES");
        SQL_MODE_MAP.put(MODE_STRICT_TRANS_TABLES, "STRICT_TRANS_TABLES");
        SQL_MODE_MAP.put(MODE_STRICT_ALL_TABLES, "STRICT_ALL_TABLES");
        SQL_MODE_MAP.put(MODE_NO_ZERO_IN_DATE, "NO_ZERO_IN_DATE");
        SQL_MODE_MAP.put(MODE_NO_ZERO_DATE, "NO_ZERO_DATE");
        SQL_MODE_MAP.put(MODE_INVALID_DATES, "INVALID_DATES");
        SQL_MODE_MAP.put(MODE_ERROR_FOR_DIVISION_BY_ZERO, "ERROR_FOR_DIVISION_BY_ZERO");
        SQL_MODE_MAP.put(MODE_TRADITIONAL, "TRADITIONAL");
        SQL_MODE_MAP.put(MODE_NO_AUTO_CREATE_USER, "NO_AUTO_CREATE_USER");
        SQL_MODE_MAP.put(MODE_HIGH_NOT_PRECEDENCE, "HIGH_NOT_PRECEDENCE");
        SQL_MODE_MAP.put(MODE_NO_ENGINE_SUBSTITUTION, "NO_ENGINE_SUBSTITUTION");
        SQL_MODE_MAP.put(MODE_PAD_CHAR_TO_FULL_LENGTH, "PAD_CHAR_TO_FULL_LENGTH");
    }

    public static String convertSqlMode(long sqlMode) {
        String modeString = SQL_MODE_MAPPING_CACHE.get(sqlMode);
        if (null != modeString) {
            return modeString;
        }
        StringBuilder sb = new StringBuilder();
        SQL_MODE_MAP.forEach((key, value) -> {
            if ((sqlMode & key) > 0) {
                sb.append(value);
                sb.append(',');
            }
        });
        if (sb.length() != 0) {
            sb.setLength(sb.length() - 1);
        }
        String mode = sb.toString();
        SQL_MODE_MAPPING_CACHE.put(sqlMode, mode);
        return mode;
    }


    public static long modesValue(String modes) throws IllegalAccessException {
        if (StringUtils.isBlank(modes)) {
            return 0;
        }
        Set<String> modesSet = new HashSet<>(Arrays.asList(modes.split(",")));
        Field[] fields = SqlModeUtil.class.getDeclaredFields();
        long mode = 0;
        for (Field f : fields) {
            f.setAccessible(true);
            if (modesSet.contains(f.getName().substring(5))) {
                mode |= f.getLong(null);
            }
        }
        return mode;
    }
}
