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
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;
import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;

/**
 * MYSQL_TYPE_TIMESTAMP
 */
@Deprecated
public class TimestampField extends Field {

    public TimestampField(CreateField createField) {
        super(createField);
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP")
            || StringUtils.equalsIgnoreCase(buildDataStr(), "0000-00-00 00:00:00");
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        MDate date = new MDate();
        date.parse(data);
        Calendar calendar = Calendar.getInstance();
        calendar.set(date.getYear(),
            date.getMonth() - 1,
            date.getDay(),
            date.getHours(),
            date.getMinutes(),
            date.getSeconds());
        calendar.set(Calendar.MILLISECOND, date.getMillsecond());
        long sec = calendar.getTimeInMillis() / 1000;
        return toByte(sec, 4);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
