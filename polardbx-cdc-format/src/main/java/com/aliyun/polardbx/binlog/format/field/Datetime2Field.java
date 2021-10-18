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

package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

/**
 * yyyy-mm-dd HH:mm:ss.sss
 * MYSQL_TYPE_DATETIME2
 */
public class Datetime2Field extends Field {

    public static final long DATETIMEF_INT_OFS = 0x8000000000L;
    public static final long TIMEF_INT_OFS = 0x800000L;
    public static final long TIMEF_OFS = 0x800000000000L;

    private static final int MAX_DATETIME_WIDTH = 19;            /* YYYY-MM-DD HH:MM:SS */

    private int dec;

    public Datetime2Field(CreateField createField) {
        super(createField);
        dec = (fieldLength > MAX_DATETIME_WIDTH) ? (int) (fieldLength - 1 - MAX_DATETIME_WIDTH) : 0;
    }

    static long myPackedTimeMake(long i, long f) {
        return ((i) << 24) + f;
    }

    @Override
    public byte[] encode() {
        return null;

    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
