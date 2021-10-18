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
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

/**
 * support MYSQL_TYPE_ENUM
 */
public class EnumField extends Field {

    public EnumField(CreateField createField) {
        super(createField);
        this.packageLength = count < 256 ? 1 : 2;
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        int v = 0;
        for (int i = 0; i < typeNames.length; i++) {
            if (typeNames[i].replaceAll("'", "").equalsIgnoreCase(data)) {
                v = i + 1;
                break;
            }
        }
        return toByte(v, packageLength);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) mysqlType.getType(), (byte) packageLength};
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_STRING;
    }
}
