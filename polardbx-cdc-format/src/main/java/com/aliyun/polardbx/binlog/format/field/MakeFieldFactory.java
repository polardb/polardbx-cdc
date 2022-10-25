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

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.nio.charset.Charset;

public class MakeFieldFactory {

    private Charset defaultCharset = Charset.forName("utf8");

    public static void main(String[] args) {
        String ddl = "create table tttt(id bigint primary key , name varchar(20))";
        SQLCreateTableStatement cts = (SQLCreateTableStatement) SQLUtils.parseSingleMysqlStatement(ddl);
        SQLColumnDefinition definition = cts.getColumn("name");
        System.out.println(definition.getNameAsString());
    }

    public static Field makeField(String typefunc, String data, String charset, boolean nullable) {
        CreateField createField = CreateField.parse(typefunc, data, charset, nullable);
        MySQLType fieldType = MySQLType.valueOf(createField.getDataType());

        switch (fieldType) {
        case MYSQL_TYPE_VAR_STRING:
        case MYSQL_TYPE_STRING:
            return new StringField(createField);
        case MYSQL_TYPE_VARCHAR:
            return new VarCharField(createField);
        case MYSQL_TYPE_BLOB:
        case MYSQL_TYPE_MEDIUM_BLOB:
        case MYSQL_TYPE_TINY_BLOB:
        case MYSQL_TYPE_LONG_BLOB:
            createField.setDefaultValue(null);
            return new BlobField(createField);
        case MYSQL_TYPE_GEOMETRY:
            createField.setDefaultValue(null);
            return new GeometryField(createField);
        case MYSQL_TYPE_JSON: {
            createField.setDefaultValue(null);
            return new JsonField(createField);
        }
        case MYSQL_TYPE_ENUM:
            return new EnumField(createField);
        case MYSQL_TYPE_SET:
            return new SetField(createField);
        case MYSQL_TYPE_DECIMAL:
            //
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
