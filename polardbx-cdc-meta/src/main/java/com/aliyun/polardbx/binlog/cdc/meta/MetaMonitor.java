/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MetaMonitor {
    private static final MetaMonitor INSTANCE = new MetaMonitor();

    private final ScheduledExecutorService cleaner;
    private final AtomicBoolean startFlag;
    private final Map<String, PolarDbXTableMetaManager> registerMap;
    private int storageCount;

    public static MetaMonitor getInstance() {
        return INSTANCE;
    }

    private MetaMonitor() {
        this.cleaner = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "semi-snapshot-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.startFlag = new AtomicBoolean(false);
        this.registerMap = new HashMap<>();
    }

    public void register(String storageInstId, PolarDbXTableMetaManager tableMetaManager) {
        tryStart();
        registerMap.put(storageInstId, tableMetaManager);
    }

    public void unregister(String storageInstId) {
        registerMap.remove(storageInstId);
    }

    private void tryStart() {
        if (startFlag.compareAndSet(false, true)) {
            cleaner.scheduleAtFixedRate(() -> {
                try {
                    calculateTableCount();
                    tryCleanTopologyRecord();
                    collectMetrics();
                } catch (Throwable t) {
                    log.error("something goes wrong in meta monitor.", t);
                }
            }, 10, 5, TimeUnit.SECONDS);
            log.info("semi snapshot cleaner started.");
        }
    }

    private void calculateTableCount() {
        try {
            Optional<PolarDbXTableMetaManager> optional =
                registerMap.values().stream().filter(m -> m.getRollbackCostTime() != -1L).findAny();
            if (optional.isPresent()) {
                LogicMetaTopology topology = optional.get().getTopology();
                if (topology != null && topology.getLogicDbMetas() != null) {
                    AtomicLong logicDbCount = new AtomicLong(0);
                    AtomicLong logicTableCount = new AtomicLong(0);
                    AtomicLong phyDbCount = new AtomicLong(0);
                    AtomicLong phyTableCount = new AtomicLong(0);

                    topology.getLogicDbMetas().forEach(t -> {
                        logicDbCount.incrementAndGet();
                        phyDbCount.addAndGet(t.getPhySchemas().size());
                        if (t.getLogicTableMetas() != null) {
                            logicTableCount.getAndAdd(t.getLogicTableMetas().size());
                            t.getLogicTableMetas().forEach(m -> {
                                if (m.getPhySchemas() != null) {
                                    m.getPhySchemas().forEach(p -> {
                                        int phyCount = p.getPhyTables() == null ? 0 : p.getPhyTables().size();
                                        phyTableCount.getAndAdd(phyCount);
                                    });
                                }
                            });
                        }
                    });
                    MetaMetrics.get().setLogicDbCount(logicDbCount.get());
                    MetaMetrics.get().setLogicTableCount(logicTableCount.get());
                    MetaMetrics.get().setPhyDbCount(phyDbCount.get());
                    MetaMetrics.get().setPhyTableCount(phyTableCount.get());
                }
            }
        } catch (Throwable t) {
            log.error("calculate meta metrics error", t);
        }
    }

    private void collectMetrics() {
        try {
            checkStorageCount();
            List<PolarDbXTableMetaManager> list = registerMap.values().stream()
                .filter(m -> m.getRollbackCostTime() != -1L).collect(Collectors.toList());
            if (!list.isEmpty()) {
                Double avgRollbackCostTime =
                    list.stream().mapToLong(PolarDbXTableMetaManager::getRollbackCostTime).average().getAsDouble();
                long maxRollbackCostTime =
                    list.stream().mapToLong(PolarDbXTableMetaManager::getRollbackCostTime).max().getAsLong();
                long minRollbackCostTime =
                    list.stream().mapToLong(PolarDbXTableMetaManager::getRollbackCostTime).min().getAsLong();

                Double avgLogicApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplySnapshotCostTime()).average().getAsDouble();
                long maxLogicApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplySnapshotCostTime()).max().getAsLong();
                long minLogicApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplySnapshotCostTime()).min().getAsLong();

                Double avgLogicApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplyHistoryCostTime()).average().getAsDouble();
                long maxLogicApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplyHistoryCostTime()).max().getAsLong();
                long minLogicApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getApplyHistoryCostTime()).min().getAsLong();

                Double avgLogicQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getQueryDdlHistoryCostTime()).average().getAsDouble();
                long maxLogicQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getQueryDdlHistoryCostTime()).max().getAsLong();
                long minLogicQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getQueryDdlHistoryCostTime()).min().getAsLong();
                Double avgLogicQueryDdlHistoryCount = list.stream().mapToLong(m -> m.getPolarDbXLogicTableMeta()
                    .getQueryDdlHistoryCount()).average().getAsDouble();
                Double avgLogicQuerySnapshotCostTime = list.stream()
                    .filter(m -> m.getPolarDbXLogicTableMeta().getQuerySnapshotCostTime() != -1)
                    .mapToLong(m -> m.getPolarDbXLogicTableMeta().getQuerySnapshotCostTime()).average().getAsDouble();

                Double avgPhyApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplySnapshotCostTime()).average().getAsDouble();
                long maxPhyApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplySnapshotCostTime()).max().getAsLong();
                long minPhyApplySnapshotCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplySnapshotCostTime()).min().getAsLong();

                Double avgPhyApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplyHistoryCostTime()).average().getAsDouble();
                long maxPhyApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplyHistoryCostTime()).max().getAsLong();
                long minPhyApplyHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getApplyHistoryCostTime()).min().getAsLong();

                Double avgPhyQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getQueryDdlHistoryCostTime()).average().getAsDouble();
                long maxPhyQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getQueryDdlHistoryCostTime()).max().getAsLong();
                long minPhyQueryDdlHistoryCostTime = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getQueryDdlHistoryCostTime()).min().getAsLong();
                Double avgPhyQueryDdlHistoryCount = list.stream().mapToLong(m -> m.getPolarDbXStorageTableMeta()
                    .getQueryDdlHistoryCount()).average().getAsDouble();

                MetaMetrics.get().setRollbackFinishCount(list.size());
                MetaMetrics.get().setRollbackAvgTime(avgRollbackCostTime.longValue());
                MetaMetrics.get().setRollbackMaxTime(maxRollbackCostTime);
                MetaMetrics.get().setRollbackMinTime(minRollbackCostTime);

                MetaMetrics.get().setLogicApplySnapshotAvgTime(avgLogicApplySnapshotCostTime.longValue());
                MetaMetrics.get().setLogicApplySnapshotMaxTime(maxLogicApplySnapshotCostTime);
                MetaMetrics.get().setLogicApplySnapshotMinTime(minLogicApplySnapshotCostTime);

                MetaMetrics.get().setLogicApplyHistoryAvgTime(avgLogicApplyHistoryCostTime.longValue());
                MetaMetrics.get().setLogicApplyHistoryMaxTime(maxLogicApplyHistoryCostTime);
                MetaMetrics.get().setLogicApplyHistoryMinTime(minLogicApplyHistoryCostTime);

                MetaMetrics.get().setLogicQueryDdlHistoryAvgTime(avgLogicQueryDdlHistoryCostTime.longValue());
                MetaMetrics.get().setLogicQueryDdlHistoryMaxTime(maxLogicQueryDdlHistoryCostTime);
                MetaMetrics.get().setLogicQueryDdlHistoryMinTime(minLogicQueryDdlHistoryCostTime);
                MetaMetrics.get().setAvgLogicQueryDdlHistoryCount(avgLogicQueryDdlHistoryCount.longValue());
                MetaMetrics.get().setAvgLogicQuerySnapshotCostTime(avgLogicQuerySnapshotCostTime.longValue());

                MetaMetrics.get().setPhyApplySnapshotAvgTime(avgPhyApplySnapshotCostTime.longValue());
                MetaMetrics.get().setPhyApplySnapshotMaxTime(maxPhyApplySnapshotCostTime);
                MetaMetrics.get().setPhyApplySnapshotMinTime(minPhyApplySnapshotCostTime);

                MetaMetrics.get().setPhyApplyHistoryAvgTime(avgPhyApplyHistoryCostTime.longValue());
                MetaMetrics.get().setPhyApplyHistoryMaxTime(maxPhyApplyHistoryCostTime);
                MetaMetrics.get().setPhyApplyHistoryMinTime(minPhyApplyHistoryCostTime);

                MetaMetrics.get().setPhyQueryDdlHistoryAvgTime(avgPhyQueryDdlHistoryCostTime.longValue());
                MetaMetrics.get().setPhyQueryDdlHistoryMaxTime(maxPhyQueryDdlHistoryCostTime);
                MetaMetrics.get().setPhyQueryDdlHistoryMinTime(minPhyQueryDdlHistoryCostTime);
                MetaMetrics.get().setAvgPhyQueryDdlHistoryCount(avgPhyQueryDdlHistoryCount.longValue());
            }

        } catch (Throwable t) {
            log.error("collect meta metrics error", t);
        }
    }

    private void tryCleanTopologyRecord() {
        try {
            checkStorageCount();
            Set<String> keys = registerMap.keySet();
            if (storageCount == keys.size()) {
                String minTso = null;
                for (String k : keys) {
                    PolarDbXLogicTableMeta logicTableMeta = registerMap.get(k).getPolarDbXLogicTableMeta();
                    String latestTso = logicTableMeta.getLatestAppliedTopologyTso();
                    if (minTso == null) {
                        minTso = latestTso;
                        continue;
                    }
                    if (latestTso.compareTo(minTso) < 0) {
                        minTso = latestTso;
                    }
                }

                if (StringUtils.isNotBlank(minTso)) {
                    Set<String> recordKeys = TopologyManager.TOPOLOGY_RECORD_CACHE.keySet();
                    String finalMinTso = minTso;
                    recordKeys.forEach(k -> {
                        if (k.compareTo(finalMinTso) < 0) {
                            TopologyManager.TOPOLOGY_RECORD_CACHE.remove(k);
                            log.info("topology record is removed from cache for tso " + k);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            log.error("try clean topology record failed.", t);
        }
    }

    private void checkStorageCount() {
        if (storageCount == 0) {
            throw new PolardbxException("storage count can`t be zero, please set a valid value.");
        }
    }

    public void setStorageCount(int storageCount) {
        this.storageCount = storageCount;
    }
}
