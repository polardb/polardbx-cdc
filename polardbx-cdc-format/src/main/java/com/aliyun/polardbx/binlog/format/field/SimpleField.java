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

import com.aliyun.polardbx.binlog.format.utils.MySQLType;

public class SimpleField extends Field {

    private byte[] data;
    private int fieldType;
    private int meta;

    public SimpleField(byte[] data, int fieldType, int meta) {
        super(null);
        this.data = data;
        this.fieldType = fieldType;
        this.meta = meta;
    }

    @Override
    public byte[] encode() {
        return data;
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }

    @Override
    public boolean isNullable() {
        return data == null;
    }

    @Override
    public boolean isNull() {
        return data == null;
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.typeOf(fieldType);
    }

    public int getFieldType() {
        return fieldType;
    }

    public void setFieldType(int fieldType) {
        this.fieldType = fieldType;
    }

    public int getMeta() {
        return meta;
    }

    public byte[] getData() {
        return data;
    }
}
