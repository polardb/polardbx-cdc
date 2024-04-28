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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import lombok.SneakyThrows;

import java.io.Serializable;

public class MakeFieldFactory {

    @SneakyThrows
    public static Field makField4TypeMisMatch(String typefunc, Serializable data, String mysqlCharset, boolean nullable,
                                              String logicDefault, boolean unsigned) {
        try {
            CreateField createField = CreateField.parse(typefunc, data, mysqlCharset, nullable, unsigned);
            return makeFieldInternal(createField);
        } catch (InvalidInputDataException e) {
            return makeField(typefunc, logicDefault, mysqlCharset, nullable, unsigned);
        }
    }

    @SneakyThrows
    public static Field makeField(String typefunc, Serializable data, String mysqlCharset, boolean nullable,
                                  boolean unsigned) {
        try {
            CreateField createField = CreateField.parse(typefunc, data, mysqlCharset, nullable, unsigned);
            return makeFieldInternal(createField);
        } catch (Throwable t) {
            throw new PolardbxException(
                "make filed error! type:" + typefunc + ", data:" + data + ", charset" + mysqlCharset + ", nullable:"
                    + nullable + ",unsigned:" + unsigned, t);
        }
    }

    private static Field makeFieldInternal(CreateField createField) throws InvalidInputDataException {
        MySQLType fieldType = MySQLType.valueOf(createField.getDataType());
        switch (fieldType) {
        case MYSQL_TYPE_STRING:
            return new StringField(createField);
        case MYSQL_TYPE_VARSTRING:
        case MYSQL_TYPE_VARCHAR:
            return new VarCharField(createField);
        case MYSQL_TYPE_BLOB:
        case MYSQL_TYPE_MEDIUMBLOB:
        case MYSQL_TYPE_TINYBLOB:
        case MYSQL_TYPE_LONGBLOB:
            return new BlobField(createField);
        case MYSQL_TYPE_GEOMETRY:
            return new GeometryField(createField);
        case MYSQL_TYPE_JSON: {
            return new JsonField(createField);
        }
        case MYSQL_TYPE_ENUM:
            return new EnumField(createField);
        case MYSQL_TYPE_SET:
            return new SetField(createField);
        case MYSQL_TYPE_DECIMAL:
        case MYSQL_TYPE_NEWDECIMAL:
            return new DecimalField(createField);

        case MYSQL_TYPE_FLOAT:
        case MYSQL_TYPE_DOUBLE:
        case MYSQL_TYPE_TINY:
        case MYSQL_TYPE_SHORT:
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_LONG:
        case MYSQL_TYPE_LONGLONG:
            return new NumberField(createField);
        case MYSQL_TYPE_TIMESTAMP:
            return new TimestampField(createField);
        case MYSQL_TYPE_TIMESTAMP2:
            return new Timestamp2Field(createField);
        case MYSQL_TYPE_YEAR:
            return new YearField(createField);
        case MYSQL_TYPE_NEWDATE:
            return new NewDateField(createField);
        case MYSQL_TYPE_TIME:
            return new TimeField(createField);
        case MYSQL_TYPE_TIME2:
            return new Time2Field(createField);
        case MYSQL_TYPE_DATETIME:
            return new DatetimeField(createField);
        case MYSQL_TYPE_DATETIME2:
            return new Datetime2Field(createField);
        case MYSQL_TYPE_NULL:
            return NullField.INSTANCE;
        case MYSQL_TYPE_BIT:
            return new BitField(createField);

        case MYSQL_TYPE_INVALID:
        case MYSQL_TYPE_BOOL:
        default:  // Impossible (Wrong version)
            break;
        }
        return null;
    }
}
