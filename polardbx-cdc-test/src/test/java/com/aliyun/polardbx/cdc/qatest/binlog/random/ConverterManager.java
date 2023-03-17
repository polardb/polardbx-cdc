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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class ConverterManager {

    public static final ConverterManager instance = new ConverterManager();
    private Map<ColumnTypeEnum, List<ColumnTypeEnum>> convertTypeMap = Maps.newHashMap();

    public ConverterManager() {
        addConvertTypeList(ColumnTypeEnum.TYPE_BIGINT, ColumnTypeEnum.TYPE_INT, ColumnTypeEnum.TYPE_MEDIUMINT,
            ColumnTypeEnum.TYPE_TINYINT, ColumnTypeEnum.TYPE_FLOAT, ColumnTypeEnum.TYPE_DOUBLE, ColumnTypeEnum.TYPE_DEC,
            ColumnTypeEnum.TYPE_DECIMAL);
        addConvertTypeList(ColumnTypeEnum.TYPE_CHAR, ColumnTypeEnum.TYPE_VARCAHR, ColumnTypeEnum.TYPE_TEXT,
            ColumnTypeEnum.TYPE_LONGTEXT, ColumnTypeEnum.TYPE_MEDIUMTEXT, ColumnTypeEnum.TYPE_BLOB,
            ColumnTypeEnum.TYPE_MEDIUMBLOB, ColumnTypeEnum.TYPE_LONGBLOB);

        addConvertTypeList(ColumnTypeEnum.TYPE_TIME, ColumnTypeEnum.TYPE_TIMESTAMP, ColumnTypeEnum.TYPE_DATE,
            ColumnTypeEnum.TYPE_DATETIME);
    }

    public static ConverterManager getInstance() {
        return instance;
    }

    public List<ColumnTypeEnum> getColumnTypeChangeList(ColumnTypeEnum typeEnum) {
        return convertTypeMap.get(typeEnum);
    }

    public void addConvertTypeList(ColumnTypeEnum... types) {
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < types.length; j++) {
                if (i == j) {
                    continue;
                }
                ColumnTypeEnum targetType = types[i];
                List<ColumnTypeEnum> typeList = convertTypeMap.computeIfAbsent(targetType, k -> Lists.newArrayList());
                typeList.add(types[j]);
            }
        }
    }
}
