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

import java.nio.ByteBuffer;

/**
 * yyyy-mm-dd HH:mm:ss.sss
 * MYSQL_TYPE_DATETIME2
 */
public class Datetime2Field extends Field {

    private static final long DATETIMEF_INT_OFS = 0x8000000000L;
    private static final long TIMEF_INT_OFS = 0x800000L;
    private static final long TIMEF_OFS = 0x800000000000L;
    private static final int MAX_DATETIME_WIDTH = 19;            /* YYYY-MM-DD HH:MM:SS */

    private final int dec;

    public Datetime2Field(CreateField createField) {
        super(createField);
        dec = createField.getCodepoint();
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP");
    }

    @Override
    public byte[] encodeInternal() {
        String value = buildDataStr();
        MDate date = new MDate();
        date.parse(value);
        long nr = date.TIME_to_longlong_datetime_packed();
        ByteBuffer bytes = ByteBuffer.allocate(5 + (dec + 1) / 2);
        toBEByte(bytes, my_packed_time_get_int_part(nr) + DATETIMEF_INT_OFS, 5);
        switch (dec) {
        case 0:
        default:
            break;
        case 1:
        case 2:
            bytes.put((byte) (my_packed_time_get_frac_part(nr) / 10000));
            break;
        case 3:
        case 4:
            toBEByte(bytes, my_packed_time_get_frac_part(nr) / 100, 2);
            break;
        case 5:
        case 6:
            toBEByte(bytes, my_packed_time_get_frac_part(nr), 3);
        }
        return bytes.array();

    }

    private long my_packed_time_get_frac_part(long i) {
        return (i % (1L << 24));
    }

    private long my_packed_time_get_int_part(long i) {
        return (i >> 24);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {
            (byte) dec
        };
    }
}
