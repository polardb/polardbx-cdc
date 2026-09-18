/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.applier.DmlApplyHelper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private List<Integer> pkColumnsIndex = new ArrayList<>();
    private List<List<Integer>> ukGroupColumnsIndex = new ArrayList<>();
    private List<List<String>> ukGroups = new ArrayList<>();
    private List<ColumnInfo> columns = new ArrayList<>();
    private List<String> keyList;
    private List<String> identifyKeyList;
    private List<ColumnInfo> withTypeKeyList;
    /**
     * Lazily derived logical EXTERNALIZE column names, normalized to lower case by the metadata loader.
     * <p>
     * This set is deliberately independent from {@link #keyList}: externalized columns are protected
     * from blind UPDATE rewrites, but they are not row identities and must never enter DML WHERE clauses,
     * validation keys, DAG keys or shard routing keys.
     */
    private Set<String> externalizedColumnNames;
    private String dbShardKey;
    private String tbShardKey;
    private Map<Integer, String> sqlTemplate = new HashMap<>(4);
    private boolean hasGeneratedUk;
    private int gsiNum;
    private String engine;
    private boolean isUkAsPkTable;
    private String ukAsPkKeyName;
    /**
     * Whether a key used by TransactionParallelApplierV3 has neither an exact representation nor a supported
     * DAG-only normalization. The default is false so legacy metadata objects remain compatible.
     */
    private boolean parallelApplyKeyUnsupported;
    private String parallelApplyKeyIncompatibleReason;

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
                        if (!DmlApplyHelper.isNonComparableType(column)) {
                            keyList.add(column.getName());
                        }
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

    public List<ColumnInfo> getWithTypeKeyList() {
        synchronized (this) {
            if (CollectionUtils.isEmpty(withTypeKeyList)) {
                getKeyList();
                withTypeKeyList = columns.stream().filter(s -> keyList.contains(s.getName()))
                    .collect(Collectors.toList());
            }
            return withTypeKeyList;
        }
    }

    /**
     * Return the logical EXTERNALIZE columns of this table.
     *
     * @return immutable column-name set; empty for ordinary tables
     */
    public Set<String> getExternalizedColumnNames() {
        synchronized (this) {
            if (externalizedColumnNames == null) {
                Set<String> names = new LinkedHashSet<>();
                for (ColumnInfo column : columns) {
                    if (column.isExternalized()) {
                        names.add(column.getName());
                    }
                }
                externalizedColumnNames = Collections.unmodifiableSet(names);
            }
            return externalizedColumnNames;
        }
    }

    void invalidateExternalizedColumnNames() {
        synchronized (this) {
            externalizedColumnNames = null;
        }
    }

    public void extractKey(String rawString) {
        if (StringUtils.isNotBlank(rawString)) {
            // use first key in range hash. e.g. CINEMA_UID,TENANT_ID
            String[] s = rawString.split("[,;]");
            for (String key : s) {
                if (!keyList.contains(key)) {
                    keyList.add(key);
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

    public ColumnInfo getColumnInfo(String columnName) {
        for (ColumnInfo columnInfo : columns) {
            if (StringUtils.equalsIgnoreCase(columnInfo.getName(), columnName)) {
                return columnInfo;
            }
        }
        throw new RuntimeException("column not found:" + columnName);
    }

    public ColumnInfo getColumnInfoOrNull(String columnName) {
        for (ColumnInfo columnInfo : columns) {
            if (StringUtils.equalsIgnoreCase(columnInfo.getName(), columnName)) {
                return columnInfo;
            }
        }
        return null;
    }

    public boolean isNoPkTable() {
        return CollectionUtils.isEmpty(pks);
    }

    public List<Integer> getPkColumnsIndex(DefaultRowChange rowChange) {
        synchronized (this) {
            if (!CollectionUtils.isEmpty(pkColumnsIndex)) {
                return pkColumnsIndex;
            }
            for (String columnName : pks) {
                pkColumnsIndex.add(rowChange.getColumnIndex(columnName));
            }
            return pkColumnsIndex;
        }
    }

    public List<List<Integer>> getUkGroupColumnsIndex(DefaultRowChange rowChange) {
        synchronized (this) {
            if (!CollectionUtils.isEmpty(ukGroupColumnsIndex)) {
                return ukGroupColumnsIndex;
            }
            for (List<String> oneGroup : ukGroups) {
                List<Integer> oneGroupIndex = new ArrayList<>();
                for (String columnName : oneGroup) {
                    oneGroupIndex.add(rowChange.getColumnIndex(columnName));
                }
                ukGroupColumnsIndex.add(oneGroupIndex);
            }
            return ukGroupColumnsIndex;
        }
    }
}
