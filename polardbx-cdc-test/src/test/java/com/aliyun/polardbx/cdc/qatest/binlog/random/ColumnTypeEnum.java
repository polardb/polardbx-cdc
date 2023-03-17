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
package com.aliyun.polardbx.cdc.qatest.binlog.random;

import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.AbstractValueGenerator;
import com.aliyun.polardbx.cdc.qatest.binlog.random.generator.ValueGenerator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

public enum ColumnTypeEnum {

    TYPE_VARCAHR("varchar(20)", "default null"),

    TYPE_CHAR("char(20)", "default null"),

    TYPE_TINYTEXT("tinytext", "default null"),

    TYPE_TEXT("text", "default null"),

    TYPE_MEDIUMTEXT("mediumtext", "default null"),

    TYPE_LONGTEXT("longtext", "default null"),

    TYPE_SMALLINT("smallint", "default null"),

    TYPE_INT("int", "default null"),

    TYPE_TINYINT("tinyint", "default null"),

    TYPE_MEDIUMINT("mediumint", "default null"),

    TYPE_BIGINT("bigint", "default null"),

    TYPE_BOOLEAN("boolean", "default null"),

    TYPE_DECIMAL("decimal(10,3)", "default null"),

    TYPE_DEC("dec(10,3)", "default null"),

    TYPE_FLOAT("float", "default null"),

    TYPE_DOUBLE("double", "default null"),

    TYPE_SET("set('A','B','C')", "default null"),

    TYPE_ENUM("enum('RED','YELLOW','BLUE')", "default null"),

    TYPE_JSON("json", "default null"),

    TYPE_GEO("geometry", "default null"),

    TYPE_BIT("bit(8)", "default null"),

    TYPE_TINYBLOB("tinyblob", "default null"),

    TYPE_BLOB("blob", "default null"),

    TYPE_MEDIUMBLOB("mediumblob", "default null"),

    TYPE_LONGBLOB("longblob", "default null"),
    TYPE_BINARY("BINARY(50)", "default null"),
    TYPE_VARBINARY("VARBINARY(50)", "default null"),

    TYPE_TIME("time", "default null"),

    TYPE_TIMESTAMP("timestamp", "default CURRENT_TIMESTAMP"),

    TYPE_DATETIME("datetime", "default null"),

    TYPE_DATE("date", "default null"),

    TYPE_YEAR("year", "default null");

    private static Map<ColumnTypeEnum, ValueGenerator> generatorMap = Maps.newHashMap();
    private String define;
    private String defaultValue;

    ColumnTypeEnum(String define, String defaultValue) {
        this.define = define;
        this.defaultValue = defaultValue;
    }

    public static void registerGenerator(AbstractValueGenerator generator) {
        generatorMap.put(generator.getType(), generator);
    }

    public static Set<ColumnTypeEnum> allType() {
        return ImmutableSet.copyOf(generatorMap.keySet());
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDefine() {
        return define;
    }

    public Object generateValue() {
        return generatorMap.get(this).generator();
    }
}
