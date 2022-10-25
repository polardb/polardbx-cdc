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
package com.aliyun.polardbx.binlog.format.utils;

public class SQLModeConsts {

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
}
