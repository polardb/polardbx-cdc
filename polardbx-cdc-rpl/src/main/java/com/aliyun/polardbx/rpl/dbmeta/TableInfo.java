/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author shicai.xsc 2020/11/29 21:19
 * @since 5.0.0.0
 */
@Data
public class TableInfo {
    public static final String ENGINE_TYPE_MYISAM = "MyISAM";
    public static final String ENGINE_TYPE_INNODB = "InnoDB";

    private String schema;
    private String name;
    private String createTable;
    private List<String> pks = new ArrayList<>();
    private List<String> uks = new ArrayList<>();
    private List<ColumnInfo> columns = new ArrayList<>();
    private List<String> keyList;
    private List<String> identifyKeyList;
    private List<ColumnInfo> withTypeKeyList;
    private List<ColumnInfo> withTypeCompareAllKeyList;
    private String dbShardKey;
    private String tbShardKey;
    private Map<Integer, String> sqlTemplate = new HashMap<>(4);
    private boolean hasGeneratedUk;
    private int gsiNum;
    private String engine;

    public TableInfo(String schema, String name) {
        this.schema = schema;
        this.name = name;
        this.keyList = new ArrayList<>();
    }

    public List<String> getKeyList() {
        synchronized (this) {
            if (CollectionUtils.isEmpty(keyList)) {
                // 无主键表
                if (CollectionUtils.isEmpty(pks)) {
                    for (ColumnInfo column : columns) {
                        keyList.add(column.getName());
                    }
                    return keyList;
                } else {
                    keyList.addAll(pks);
                }
                extractKey(dbShardKey);
                extractKey(tbShardKey);
            }
            return keyList;
        }
    }

    public List<ColumnInfo> getWithTypeKeyList(boolean compareAll) {
        synchronized (this) {
            do {
                if (compareAll) {
                    if (!CollectionUtils.isEmpty(withTypeCompareAllKeyList)) {
                        break;
                    }
                    // 目前 CN 不支持 json 比较和 bit 比较
                    withTypeCompareAllKeyList = columns.stream()
                        .filter(s -> !StringUtils.containsIgnoreCase(s.getTypeName(), "JSON"))
                        .filter(s -> (s.getType() != Types.BIT && s.getType() != Types.DOUBLE
                            && s.getType() != Types.FLOAT && s.getType() != Types.REAL))
                        .collect(Collectors.toList());
                    break;
                }
                if (!CollectionUtils.isEmpty(withTypeKeyList)) {
                    break;
                }
                getKeyList();
                withTypeKeyList = columns.stream().filter(s -> keyList.contains(s.getName()))
                    .collect(Collectors.toList());
                break;
            } while (true);
            return compareAll ? withTypeCompareAllKeyList : withTypeKeyList;
        }
    }

    public void extractKey(String rawString) {
        if (StringUtils.isNotBlank(rawString)) {
            // use first key in range hash. e.g. CINEMA_UID,TENANT_ID
            String[] s = rawString.split("[,;]");
            if (!keyList.contains(s[0])) {
                keyList.add(s[0]);
            }
            if (s.length >= 2) {
                if (!keyList.contains(s[1])) {
                    keyList.add(s[1]);
                }
            }
        }
    }

    public List<String> getIdentifyKeyList() {
        synchronized (this) {
            if (CollectionUtils.isEmpty(identifyKeyList)) {
                identifyKeyList = new ArrayList<>(getKeyList());
                for (String uk : uks) {
                    if (!identifyKeyList.contains(uk)) {
                        identifyKeyList.add(uk);
                    }
                }
            }
            return identifyKeyList;
        }
    }

    public int getColumnType(String columnName) {
        for (ColumnInfo columnInfo : columns) {
            if (StringUtils.equalsIgnoreCase(columnInfo.getName(), columnName)) {
                return columnInfo.getType();
            }
        }
        return Types.NULL;
    }

    public boolean isNoPkTable() {
        return CollectionUtils.isEmpty(pks);
    }
}
