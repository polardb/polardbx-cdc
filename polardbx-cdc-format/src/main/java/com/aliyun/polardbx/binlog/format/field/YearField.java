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
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

public class YearField extends Field {

    private static final int MIN_YEAR = 1901;
    private static final int MAX_YEAR = 2155;
    private static final int YY_PART_YEAR = 70;

    public YearField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        long nr = Long.valueOf(data);
        if (nr < 0 || (nr >= 100 && nr < MIN_YEAR) || nr > MAX_YEAR) {
            throw new RuntimeException("out of range");
        }
        if (nr != 0)  // 0000 -> 0
        {
            if (nr < YY_PART_YEAR) {
                nr += 100;  // 2000 - 2069
            } else if (nr > 1900) {
                nr -= 1900;
            }
        }
        return new byte[] {(byte) nr};
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
