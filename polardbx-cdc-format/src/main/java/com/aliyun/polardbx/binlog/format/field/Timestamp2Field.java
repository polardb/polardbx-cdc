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
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * MYSQL_TYPE_TIMESTAMP2
 */
public class Timestamp2Field extends Field {

    private final int desc;

    public Timestamp2Field(CreateField createField) {
        super(createField);
        desc = createField.getCodepoint();
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP");
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        MDate mDate = new MDate();
        mDate.parse(data);
        Calendar calendar = Calendar.getInstance();
        calendar.set(mDate.getYear(), mDate.getMonth() - 1, mDate.getDay(), mDate.getHours(),
            mDate.getMinutes(), mDate.getSeconds());

        ByteBuffer byteBuffer;
        long sec = TimeUnit.MILLISECONDS.toSeconds(calendar.getTimeInMillis());
        long mill = mDate.getMillsecond();
        switch (desc) {
        case 1:
        case 2:
            byteBuffer = ByteBuffer.allocate(5);
            toBEByte(byteBuffer, sec, 4);
            byteBuffer.put((byte) (mill / 10000));
            break;
        case 3:
        case 4:
            byteBuffer = ByteBuffer.allocate(6);
            toBEByte(byteBuffer, sec, 4);
            toBEByte(byteBuffer, mill / 100, 2);
            break;
        case 5:
        case 6:
            byteBuffer = ByteBuffer.allocate(7);
            toBEByte(byteBuffer, sec, 4);
            toBEByte(byteBuffer, mill, 3);
            break;
        default:
            byteBuffer = ByteBuffer.allocate(4);
            toBEByte(byteBuffer, sec, 4);
            break;
        }

        return byteBuffer.array();
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) desc};
    }
}
