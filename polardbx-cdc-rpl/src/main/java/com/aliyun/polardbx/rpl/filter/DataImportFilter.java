/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author jiyue 2021/8/17 17:57
 * @since 5.0.0.0
 */
@Data
@Slf4j
public class DataImportFilter extends BaseFilter {

    private DataImportMeta.PhysicalMeta importMeta;
    private Set<String> doDbs;
    private Map<String, Set<String>> doTables;
    private Map<String, String> dstDbMapping;
    private Map<String, Map<String, String>> tableNameMapping;
    private Set<Long> ignoreServerIds;
    private Map<String, Boolean> filterCache;
    private Set<String> logicalFilterTables;

    public DataImportFilter(DataImportMeta.PhysicalMeta importMeta) {
        this.importMeta = importMeta;
    }

    @Override
    public void init() {
        doTables = importMeta.getPhysicalDoTableList();
        doDbs = importMeta.getSrcDbList();
        dstDbMapping = importMeta.getDstDbMapping();
        tableNameMapping = importMeta.getRewriteTableMapping();
        ignoreServerIds = initIgnoreServerIds(importMeta.getIgnoreServerIds());
        log.warn("ignore server ids: {}", ignoreServerIds);
        filterCache = new HashMap<>(128);
        Set<Pair<String, String>> logicalFilterTablesPair = initFilterPairSet(DynamicApplicationConfig
            .getString(ConfigKeys.RPL_INC_BLACK_TABLE_LIST));
        logicalFilterTables = new HashSet<>();
        for (Pair<String, String> pair : logicalFilterTablesPair) {
            logicalFilterTables.add(pair.getLeft() + "." + pair.getRight());
        }
        log.warn("filter tables: {}", logicalFilterTables);
    }

    @Override
    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        if (ignoreServerIds.contains(serverId)) {
            return true;
        }
        String key = schema + "." + tbName;
        if (filterCache.containsKey(key)) {
            return filterCache.get(key);
        }

        String logicalKey = getRewriteDb(schema, action) + "." + getRewriteTable(schema, tbName);
        boolean skip = !dbOk(schema) || !tableOk(schema, tbName) || logicalFilterTables.contains(logicalKey);
        filterCache.put(key, skip);

        return skip;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::tables_ok(const char *db, TABLE_LIST *tables)
     */
    private boolean tableOk(String db, String tb) {
        if (!doTables.containsKey(db)) {
            return false;
        }
        return doTables.get(db).contains(tb);
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::db_ok(const char *db, bool need_increase_counter)
     */
    private boolean dbOk(String db) {
        if (!doDbs.isEmpty()) {
            return doDbs.contains(db);
        }
        return true;
    }

    @Override
    public String getRewriteTable(String db, String table) {
        return Optional.ofNullable(tableNameMapping)
            .map(map -> map.get(db))
            .map(innerMap -> innerMap.get(table))
            .orElse(table);
    }

    @Override
    public String getRewriteDb(String schema, DBMSAction action) {
        return (dstDbMapping != null && dstDbMapping.containsKey(schema)) ?
            dstDbMapping.get(schema) : schema;
    }

    @Override
    protected Set<Long> initIgnoreServerIds(String filterStr) {
        Set<String> tmpIgnoreServerIds = initFilterNums(filterStr);
        Set<Long> ignoreServerIds = new HashSet<>();
        for (String serverId : tmpIgnoreServerIds) {
            ignoreServerIds.add((long) Math.abs(Long.valueOf(serverId).intValue()));
        }
        return ignoreServerIds;
    }

}

