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
package com.aliyun.polardbx.binlog.format.utils;

public enum MySQLType {

    /**
     * enum_field_types
     */
    MYSQL_TYPE_DECIMAL(0, 2), MYSQL_TYPE_TINY(1, 0),

    MYSQL_TYPE_SHORT(2, 0), MYSQL_TYPE_LONG(3, 0),

    MYSQL_TYPE_FLOAT(4, 1), MYSQL_TYPE_DOUBLE(5, 1),

    MYSQL_TYPE_NULL(6, 0), MYSQL_TYPE_TIMESTAMP(7, 0),

    MYSQL_TYPE_LONGLONG(8, 0), MYSQL_TYPE_INT24(9, 0),

    MYSQL_TYPE_DATE(10, 0), MYSQL_TYPE_TIME(11, 0),

    MYSQL_TYPE_DATETIME(12, 0), MYSQL_TYPE_YEAR(13, 0),

    MYSQL_TYPE_NEWDATE(14, 0), MYSQL_TYPE_VARCHAR(15, 2),

    MYSQL_TYPE_BIT(16, 2), MYSQL_TYPE_TIMESTAMP2(17, 1),

    MYSQL_TYPE_DATETIME2(18, 1), MYSQL_TYPE_TIME2(19, 1),

    MYSQL_TYPE_JSON(245, 1), MYSQL_TYPE_NEWDECIMAL(246, 2),

    MYSQL_TYPE_ENUM(247, 2), MYSQL_TYPE_SET(248, 2),

    MYSQL_TYPE_TINYBLOB(249, 1), MYSQL_TYPE_MEDIUMBLOB(250, 1),

    MYSQL_TYPE_LONGBLOB(251, 1), MYSQL_TYPE_BLOB(252, 1),

    MYSQL_TYPE_VARSTRING(253, 2), MYSQL_TYPE_STRING(254, 2),

    MYSQL_TYPE_GEOMETRY(255, 1), MYSQL_TYPE_BOOL(244, 1)/*< Currently just a placeholder */,

    MYSQL_TYPE_TYPED_ARRAY(20, 0),  /*< Used for replication only */MYSQL_TYPE_INVALID(243, 0);

    private final int type;
    private final int metaLen;

    MySQLType(int type, int metaLen) {
        this.type = type;
        this.metaLen = metaLen;
    }

    public static MySQLType typeOf(int type) {
        for (MySQLType t : values()) {
            if (t.type == type) {
                return t;
            }
        }
        return null;
    }

    public static MySQLType nameOf(String name) {
        String mType = "MYSQL_TYPE_" + name.toUpperCase();
        for (MySQLType t : values()) {
            if (t.name().equalsIgnoreCase(mType)) {
                return t;
            }
        }
        return null;
    }

    public int getType() {
        return type;
    }

    public int getMetaLen() {
        return metaLen;
    }
}
