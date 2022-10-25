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

import java.util.concurrent.TimeUnit;

/**
 * MYSQL_TYPE_TIMESTAMP2
 */
public class Timestamp2Field extends Field {

    private static final int MAX_DATETIME_WIDTH = 19;           /* YYYY-MM-DD HH:MM:SS */

    private int desc;

    public Timestamp2Field(CreateField createField) {
        super(createField);
        desc = fieldLength > MAX_DATETIME_WIDTH ? (int) (fieldLength - 1 - MAX_DATETIME_WIDTH) : 0;
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        long date = Long.valueOf(data);
        byte[] binary;
        long sec = TimeUnit.MILLISECONDS.toSeconds(date);
        long mill = date - TimeUnit.SECONDS.toMillis(sec);
        switch (desc) {
        case 0:
            byte[] secByte = toBEByte(sec, 4);
            binary = new byte[4];
            System.arraycopy(secByte, 0, binary, 0, 4);
            break;
        case 1:
        case 2:
            binary = new byte[5];
            secByte = toBEByte(sec, 4);
            System.arraycopy(secByte, 0, binary, 0, 4);
            binary[4] = (byte) (mill / 10000);
            break;
        case 3:
        case 4:
            binary = new byte[6];
            secByte = toBEByte(sec, 4);
            System.arraycopy(secByte, 0, binary, 0, 4);
            mill = mill / 100;
            binary[4] = (byte) ((mill >> 8) & 0xFF);
            binary[5] = (byte) (mill & 0xFF);
            break;
        case 5:
        case 6:
            binary = new byte[7];
            secByte = toBEByte(sec, 4);
            System.arraycopy(secByte, 0, binary, 0, 4);
            binary[4] = (byte) ((mill >> 16) & 0xFF);
            binary[5] = (byte) ((mill >> 8) & 0xFF);
            binary[6] = (byte) (mill & 0xFF);
            break;
        default:
            secByte = toBEByte(sec, 4);
            binary = new byte[4];
            System.arraycopy(secByte, 0, binary, 0, 4);
            break;
        }
        return binary;
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) desc};
    }
}
