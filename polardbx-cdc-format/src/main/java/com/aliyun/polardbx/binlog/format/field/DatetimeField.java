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
import com.aliyun.polardbx.binlog.format.field.domain.MDate;

public class DatetimeField extends Field {

    public DatetimeField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        MDate date = new MDate();
        date.parse(data);
        long tmp = date.TIME_to_ulonglong_datetime();
        return toByte(tmp, 8);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
