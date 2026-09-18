/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicTableMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.cdc.topology.LowerCaseUtil.toLowerCase;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.needShareTopology;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.trySharedRecord;

/**
 * server内核打标时，物理库表名按照Mysql的lower_case_table_names参数进行了大小写处理
 * server内核并不支持该参数，默认是大小写不敏感的，且不可更改，但和mysql的表现形式并不太一样，比如：
 * MySQL  不敏感：create database Abc 之后执行show databases查询出来的库名是abc, use abc/use Abc都可以
 * PolarX 不敏感：create database Abc 之后执行show database查询出来的库名是Abc, use abc/use Abc都可以
 * <p>
 * 一些历史原因，CDC在server内核打标时，对逻辑库表名称并没有进行转小写处理，TopologyManager再次需要进行统一toLowerCase处理
 * <p>
 * Created by ziyang.lb
 */
@Slf4j
public class TopologyManager {
    private final Map<Pair<String, String>, LogicBasicInfo> cache = Maps.newHashMap();
    /**
     * dbName.tbName -> tableId
     */
    private final Map<Pair<String, String>, Long> tableIdMap = Maps.newHashMap();
    private LogicMetaTopology topology;
    // apply History 最大的tso, 在apply History后自动设置
    @Getter
    @Setter
    private String rollBackTso;

    public TopologyManager() {
    }

    public TopologyManager(LogicMetaTopology topology) {
        this.checkTopology(topology);
        this.topology = topology;
    }

    public void applyHistory(final String tso, String schema, String table, TopologyRecord record) {
        this.rollBackTso = null;
        apply(tso, schema, table, record);
    }

    public void apply(final String tso, String schema, String table, TopologyRecord record) {
        if (record == null) {
            //2do 特殊处理
            return;
        }
        schema = toLowerCase(schema);
        table = toLowerCase(table);
        trySharedRecord(record);

        Preconditions.checkNotNull(schema);
        Preconditions.checkArgument(StringUtils.isEmpty(table) == (record.getLogicTableMeta() == null),
            "table name [%s] and logicTableMeta [%s] should both exist or both not exist", table,
            record.getLogicTableMeta());
        if (record.getLogicDbMeta() != null) {
            LogicDbTopology origin = getTopology(schema);
            try {
                //insert or update db, logic table is unknown
                if (origin == null) {
                    //insert
                    topology.add(record.getLogicDbMeta());
                } else {
                    //update all groups
                    //以group为准，更新phyDbName, storageInstId
                    Map<String, PhyDbTopology> groupDetail = record.getLogicDbMeta().getPhySchemas().stream()
                        .collect(Collectors.toMap(PhyDbTopology::getGroup, Function.identity()));
                    origin.setPhySchemas(record.getLogicDbMeta().getPhySchemas());
                    origin.getLogicTableMetas().stream().flatMap(logicSchema -> logicSchema.getPhySchemas().stream())
                        .forEach(phySchema -> {
                            PhyDbTopology phyDbs = groupDetail.get(phySchema.getGroup());
                            phySchema.setSchema(phyDbs.getSchema());
                            phySchema.setStorageInstId(phyDbs.getStorageInstId());
                        });
                }
            } catch (Exception e) {
                log.error("update logic db meta fail {} {} {}", tso, JSONObject.toJSONString(origin),
                    JSONObject.toJSONString(record));
                throw new RuntimeException(e);
            }
        } else if (record.getLogicTableMeta() != null) {
            Preconditions.checkNotNull(table);
            //create or update table meta
            LogicTableMetaTopology meta = record.getLogicTableMeta();
            Pair<LogicDbTopology, LogicTableMetaTopology> topology = getTopology(schema, table);
            final LogicTableMetaTopology origin = topology.getRight();
            if (rollBackTso == null || tso.compareTo(rollBackTso) > 0) {
                // rollBackTso是apply History 结束时的最大tso
                // 如果tso小于该值，证明其已经update过table id，不能重复update
                // rollBackTso == null 说明还没有snapshot或者是applyHistory过来的，必须分配table id
                updateTableId(schema, meta.getTableName());
            } else {
                log.warn("ignore update table id record {} tso {} less than rollBackTso {}", record, tso, rollBackTso);
            }
            if (origin == null) {
                // create table
                topology.getLeft().getLogicTableMetas().add(meta);
            } else {
                invalidCache(tso, schema, table);
                // update table meta
                //origin.setPhySchemas(phySchemas);
                origin.setTableName(meta.getTableName());
                origin.setTableType(meta.getTableType());
                origin.setPhySchemas(meta.getPhySchemas());
            }
        }

    }

    public LogicDbTopology getTopology(String schema) {
        Preconditions.checkNotNull(schema);
        schema = toLowerCase(schema);
        for (LogicDbTopology logicSchema : topology.getLogicDbMetas()) {
            if (schema.equals(logicSchema.getSchema())) {
                return logicSchema;
            }
        }
        return null;
    }

    public Pair<LogicDbTopology, LogicTableMetaTopology> getTopology(String schema, String table) {
        Preconditions.checkNotNull(schema);
        Preconditions.checkNotNull(table);
        schema = toLowerCase(schema);
        table = toLowerCase(table);

        LogicDbTopology topology = getTopology(schema);
        Preconditions.checkNotNull(topology, "database " + schema + " is not found in the topology");
        for (LogicTableMetaTopology logicTableMeta : topology.getLogicTableMetas()) {
            if (logicTableMeta.getTableName().equals(table)) {
                return Pair.of(topology, logicTableMeta);
            }
        }
        return Pair.of(topology, null);
    }

    public String getLogicSchema(String phySchema) {
        Preconditions.checkNotNull(phySchema);
        for (LogicDbTopology logicSchema : topology.getLogicDbMetas()) {
            for (PhyDbTopology schemaPhy : logicSchema.getPhySchemas()) {
                if (phySchema.equals(schemaPhy.getSchema())) {
                    return logicSchema.getSchema();
                }
            }
        }
        return null;
    }

    public String getStorageInstIdByPhySchema(String phySchema) {
        Preconditions.checkNotNull(phySchema);
        toLowerCase(phySchema);
        for (LogicDbTopology logicSchema : topology.getLogicDbMetas()) {
            for (PhyDbTopology phyDbTopology : logicSchema.getPhySchemas()) {
                if (phySchema.equals(phyDbTopology.getSchema())) {
                    return phyDbTopology.getStorageInstId();
                }
            }
        }
        return "";
    }

    public LogicBasicInfo getLogicBasicInfo(String phySchema, String phyTable) {
        Preconditions.checkNotNull(phySchema);
        Preconditions.checkNotNull(phyTable);

        LogicBasicInfo logicBasicInfo = cache.get(Pair.of(phySchema, phyTable));
        if (logicBasicInfo != null) {
            return logicBasicInfo;
        }

        for (LogicDbTopology logicSchema : topology.getLogicDbMetas()) {
            for (LogicTableMetaTopology logicTableMetaTopology : logicSchema.getLogicTableMetas()) {
                for (PhyTableTopology phyTableTopology : logicTableMetaTopology.getPhySchemas()) {
                    if (phyTableTopology.getSchema().equals(phySchema) && phyTableTopology.getPhyTables().contains(
                        phyTable)) {
                        LogicBasicInfo basicInfo = new LogicBasicInfo();
                        basicInfo.setSchemaName(logicSchema.getSchema());
                        basicInfo.setTableName(logicTableMetaTopology.getTableName());
                        cache.put(Pair.of(phySchema, phyTable), basicInfo);
                        return basicInfo;
                    }
                }
            }
        }
        return null;
    }

    public List<PhyTableTopology> getPhyTables(String storageInstId,
                                               Set<String> excludeLogicDbs,
                                               Set<String> excludeLogicTables) {
        Preconditions.checkNotNull(storageInstId);
        return topology.getLogicDbMetas().stream()
            .flatMap(l -> l.getLogicTableMetas()
                .stream()
                .filter(d -> !excludeLogicDbs.contains(l.getSchema()))
                .filter(t -> !excludeLogicTables.contains(l.getSchema() + "." + t.getTableName()))
                .flatMap(p -> p.getPhySchemas().stream())
                .filter(s -> s.getStorageInstId().equals(storageInstId)))
            .collect(Collectors.toList());
    }

    public void initPhyLogicMapping(String storageInstId) {
        topology.getLogicDbMetas().forEach(d -> d.getLogicTableMetas().forEach(t -> {
            t.getPhySchemas().forEach(phy -> {
                if (phy.getStorageInstId().equals(storageInstId)) {
                    phy.getPhyTables().forEach(pt -> cache.putIfAbsent(Pair.of(phy.getSchema(), pt),
                        new LogicBasicInfo(d.getSchema(), t.getTableName())));
                }
            });
        }));
    }

    public LogicMetaTopology getTopology() {
        return topology;
    }

    public void setTopology(LogicMetaTopology topology) {
        this.checkTopology(topology);
        this.topology = topology;
    }

    private void invalidCache(String tso, String schema, String table) {
        Iterator<Entry<Pair<String, String>, LogicBasicInfo>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Pair<String, String>, LogicBasicInfo> c = iterator.next();
            LogicBasicInfo t = c.getValue();
            if (t.getSchemaName().equals(schema) && (StringUtils.isEmpty(table) || t.getTableName().equals(table))) {
                if (log.isDebugEnabled()) {
                    log.debug("TSO {}: remove topology of {}.{}", tso, schema, table);
                }
                iterator.remove();
            }
        }
    }

    public void removeTopology(String tso, String schema, String table) {
        schema = toLowerCase(schema);
        table = toLowerCase(table);

        invalidCache(tso, schema, table);
        if (StringUtils.isNotEmpty(schema)) {
            if (StringUtils.isEmpty(table)) {
                topology.removeSchema(schema);
            } else {
                topology.removeTable(schema, table);
            }
        }
    }

    private void checkTopology(LogicMetaTopology topology) {
        if (topology == null) {
            return;
        }
        if (needShareTopology() && !topology.isShared()) {
            throw new PolardbxException("topology should be shared, but is not!");
        }
        if (needShareTopology() && !topology.isInterned()) {
            throw new PolardbxException("topology should be interned, but is not!");
        }
        if (!topology.isLowerCased()) {
            throw new PolardbxException("topology should be lowerCased, but is not!");
        }
    }

    public Long getTableId(String dbName, String tbName) {
        Pair<String, String> key = Pair.of(dbName, tbName);
        Long value = tableIdMap.get(key);
        if (value == null) {
            throw new RuntimeException(String.format("Table id for %s.%s not found.", dbName, tbName));
        }
        return value;
    }

    public void updateTableId(String dbName, String tbName) {
        Pair<String, String> key = Pair.of(dbName, tbName);
        tableIdMap.put(key, topology.incrementAndGetTableId());
        log.info("{}.{} table id: {}", dbName, tbName, topology.getMaxTableId());
    }

    /**
     * 仅对virtual table主动设置table id
     */
    public void setTableIdForVirtualTable(Pair<String, String> key, long tableId) {
        Long value = tableIdMap.get(key);
        if (value == null || value < tableId) {
            tableIdMap.put(key, tableId);
            log.info("set table id for virtual table! {}.{} table id: {}",
                key.getKey(), key.getValue(), tableId);
        }
    }

    public void reAllocateAllTableId() {
        reAllocateAllTableId(topology);
    }

    public void reAllocateAllTableId(LogicMetaTopology topology) {
        log.info("start reallocate all table id.");
        tableIdMap.clear();
        topology.getSortedLogicDbMetas().forEach(s -> {
            String dbName = s.getSchema();
            s.getSortedLogicTableMetas().forEach(t -> {
                String tbName = t.getTableName();
                updateTableId(dbName, tbName);
            });
        });
        log.info("reallocate all table id finished.");
    }

}
