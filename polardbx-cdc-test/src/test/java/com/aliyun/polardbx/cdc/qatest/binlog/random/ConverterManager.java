/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
