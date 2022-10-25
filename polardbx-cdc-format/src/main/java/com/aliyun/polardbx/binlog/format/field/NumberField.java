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

/**
 * support integer, byte , long, Float.floatToIntBits()
 */
public class NumberField extends Field {

    public NumberField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        long v;
        switch (mysqlType) {
        case MYSQL_TYPE_LONG: {
            v = Long.valueOf(data);
            return toByte(v, 4);
        }
        case MYSQL_TYPE_FLOAT: {
            v = Float.floatToIntBits(Float.valueOf(data));
            return toByte(v, 4);
        }
        case MYSQL_TYPE_TINY: {
            v = Long.valueOf(data);
            return toByte(v, 1);
        }
        case MYSQL_TYPE_SHORT: {
            v = Long.valueOf(data);
            return toByte(v, 2);
        }
        case MYSQL_TYPE_INT24: {
            v = Long.valueOf(data);
            return toByte(v, 3);
        }
        case MYSQL_TYPE_LONGLONG: {
            v = Long.valueOf(data);
            return toByte(v, 8);
        }
        case MYSQL_TYPE_DOUBLE: {
            v = Double.doubleToLongBits(Double.valueOf(data));
            return toByte(v, 8);
        }
        case MYSQL_TYPE_DECIMAL: {
            // should not be here
            v = Double.doubleToLongBits(Double.valueOf(data));
            return toByte(v, 2);
        }
        default:
            throw new UnsupportedOperationException("unsupport type " + mysqlType + " , value : " + data);
        }
    }

    @Override
    public byte[] doGetTableMeta() {
        switch (mysqlType) {
        case MYSQL_TYPE_FLOAT:
            byte[] metas = new byte[1];
            metas[0] = 4;
            return metas;
        case MYSQL_TYPE_DOUBLE:
            metas = new byte[1];
            metas[0] = 8;
            return metas;
        case MYSQL_TYPE_TINY:
        case MYSQL_TYPE_SHORT:
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_LONG:
        case MYSQL_TYPE_LONGLONG:
        }
        return EMPTY;
    }
}
