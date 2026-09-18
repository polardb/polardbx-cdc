/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

/**
 * @author shicai.xsc 2021/5/24 11:42
 * @since 5.0.0.0
 */
@Slf4j
public class SplitApplier extends SplitTransactionApplier {

    public SplitApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }

        List<SplitStage> allStages = split(dbmsEvents);
        for (SplitStage stage : allStages) {
            executeOneStage(stage);
        }
    }

    protected void executeOneStage(SplitStage stage) {
        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = stage.getAllSplitRowChanges();
        Map<String, List<DefaultRowChange>> allSerialRowChanges = stage.getAllSerialRowChanges();

        boolean tbTranExec = getBoolean(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED);
        boolean groupByAction = getBoolean(RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED);

        // parallel for all tables
        if (groupByAction) {
            executeInGroupByActionMode(allSplitRowChanges, tbTranExec);
        } else {
            executeMixedRowChanges(allSplitRowChanges, stage.getParallelRowCount(), tbTranExec);
        }

        // serial for each table
        logSerialExecuteInfo(allSerialRowChanges);
        List<RowQueue> serialRowQueues = allSerialRowChanges.values().stream()
            .map(RowQueue::new).collect(Collectors.toList());
        parallelExecSqlContexts(serialRowQueues, true);
    }

    protected void executeInGroupByActionMode(Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges,
                                              boolean tbTranExec) {
        StagingRowQueue stagingRowQueue = buildStagingRowQueue(allSplitRowChanges);

        Map<String, List<DefaultRowChange>> pureInsertRowChanges = stagingRowQueue.getPureInsertRowChanges();
        int pureInsertRowChangeSize = stagingRowQueue.getPureInsertRowChangeSize();
        executePureInsertOrDeleteRowChanges(pureInsertRowChanges, pureInsertRowChangeSize, tbTranExec);
        stagingRowQueue.clearPureInsertRowChanges();

        Map<String, List<DefaultRowChange>> pureDeleteRowChanges = stagingRowQueue.getPureDeleteRowChanges();
        int pureDeleteRowChangeSize = stagingRowQueue.getPureDeleteRowChangeSize();
        executePureInsertOrDeleteRowChanges(pureDeleteRowChanges, pureDeleteRowChangeSize, tbTranExec);
        stagingRowQueue.clearPureDeleteRowChanges();

        Map<String, Map<RowKey, List<DefaultRowChange>>> holdingRowChanges =
            stagingRowQueue.getHoldingRowChanges();
        int holdingRowChangeSize = stagingRowQueue.getHoldingRowChangeSize();
        executeMixedRowChanges(holdingRowChanges, holdingRowChangeSize, tbTranExec);
        stagingRowQueue.clearHoldingRowChanges();
    }

    protected StagingRowQueue buildStagingRowQueue(
        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges) {

        StagingRowQueue stagingRowQueue = new StagingRowQueue();
        for (String fullTbName : allSplitRowChanges.keySet()) {
            Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = allSplitRowChanges.get(fullTbName);
            for (Map.Entry<RowKey, List<DefaultRowChange>> entry : tbSplitRowChanges.entrySet()) {
                stagingRowQueue.addAll(fullTbName, entry.getKey(), entry.getValue());
            }
        }
        return stagingRowQueue;
    }

    protected void executePureInsertOrDeleteRowChanges(Map<String, List<DefaultRowChange>> stagedRowChanges,
                                                       int size,
                                                       boolean tbTranExec) {
        if (stagedRowChanges.isEmpty()) {
            return;
        }

        int currentIndex = 0;
        int avgQueueSize = size / applierConfig.getMaxPoolSize();

        Map<Integer, RowQueue> allQueues = new HashMap<>();
        for (String fullTbName : stagedRowChanges.keySet()) {
            for (DefaultRowChange rowChange : stagedRowChanges.get(fullTbName)) {
                int index = currentIndex % applierConfig.getMaxPoolSize();
                RowQueue targetQueue = allQueues.computeIfAbsent(index, k -> new RowQueue());
                targetQueue.add(rowChange);
                if (targetQueue.size() >= avgQueueSize) {
                    currentIndex++;
                }
            }
        }

        parallelExecSqlContexts(allQueues.values(), tbTranExec);
    }

    protected void executeMixedRowChanges(Map<String, Map<RowKey, List<DefaultRowChange>>> stagedRowChanges,
                                          int size,
                                          boolean tbTranExec) {
        if (stagedRowChanges.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int currentIndex = 0;
        int avgQueueSize = size / applierConfig.getMaxPoolSize();

        Map<Integer, RowQueue> allQueues = new HashMap<>();
        for (String fullTbName : stagedRowChanges.keySet()) {
            Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = stagedRowChanges.get(fullTbName);
            // 保证同一个表 a.a 的同一个 identify key 的 rowChanges 在同一个 queue 内，且按照顺序执行
            // 同一个表 a.a 的不同 identify key 的 rowChanges 不必在同一个 queue 内
            for (Map.Entry<RowKey, List<DefaultRowChange>> entry : tbSplitRowChanges.entrySet()) {
                // 使用轮询方式将相同key的数据添加到队列中，实现更好的负载均衡
                int index = currentIndex % applierConfig.getMaxPoolSize();
                RowQueue targetQueue = allQueues.computeIfAbsent(index, k -> new RowQueue());
                targetQueue.addAll(entry.getValue());
                if (targetQueue.size() >= avgQueueSize) {
                    currentIndex++;
                }
            }
        }

        if (log.isDebugEnabled()) {
            Map<Integer, Long> sizeCountMap = new TreeMap<>(allQueues.values().stream()
                .collect(Collectors.groupingBy(RowQueue::size, Collectors.counting())));
            log.debug("prepare data cost: {}ms, count : {}, size count: {}",
                (System.currentTimeMillis() - startTime), size, sizeCountMap);
        }

        parallelExecSqlContexts(allQueues.values(), tbTranExec);
    }

    protected List<SplitStage> split(List<DBMSEvent> dbmsEvents) throws Exception {
        boolean isMultiStage = getBoolean(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED);

        Map<Integer, SplitStage> allStages = new TreeMap<>();
        Map<String, AtomicInteger> tableStageIndex = new HashMap<>();

        Map<String, List<Integer>> allTbIdentifyColumns = new HashMap<>();
        Map<String, List<Integer>> allTbPkColumns = new HashMap<>();

        Map<String, Boolean> prepareToSerialExecTables = new HashMap<>();
        Set<String> changedIdentifyColumnTables = new HashSet<>();

        for (DBMSEvent event : dbmsEvents) {
            // prepare basic info
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();
            SplitStage currentTableStage = getCurrentStage(fullTbName, isMultiStage, tableStageIndex, allStages);

            // get identify columns
            List<Integer> identifyColumns =
                DmlApplyHelper.getIdentifyColumnsIndex(allTbIdentifyColumns, fullTbName, rowChange);
            List<Integer> pkColumns =
                DmlApplyHelper.getPkColumnsIndex(allTbPkColumns, fullTbName, rowChange);

            // find out events which changed identify columns of a table
            if (!changedIdentifyColumnTables.contains(fullTbName)) {
                // no pk table view as changedIdentifyColumnTables to make its dml serial execute
                // has uk table view as changedIdentifyColumnTables when delete event
                if (DmlApplyHelper.shouldSerialExecute(rowChange, prepareToSerialExecTables)) {
                    changedIdentifyColumnTables.add(fullTbName);
                } else if (rowChange.getAction() == DBMSAction.UPDATE) {
                    for (Integer column : identifyColumns) {
                        if (rowChange.hasChangeColumn(column)) {
                            changedIdentifyColumnTables.add(fullTbName);
                            log.warn("row change executing mode goes to serial for key-update, table {}.", fullTbName);
                            break;
                        }
                    }
                }
            }

            // 发现某表 a.a 某条记录 a.a.1 修改了 identify columns，则 a.a.1 之后所有记录都改为串行
            if (changedIdentifyColumnTables.contains(fullTbName)) {
                currentTableStage.addRowChange4SerialApply(fullTbName, rowChange);
                if (isMultiStage) {
                    tableStageIndex.get(fullTbName).incrementAndGet();
                    prepareToSerialExecTables.remove(fullTbName);
                    changedIdentifyColumnTables.remove(fullTbName);
                }

                continue;
            }

            currentTableStage.addRowChange4ParallelApply(fullTbName, rowChange, pkColumns);
        }

        return new ArrayList<>(allStages.values());
    }

    SplitStage getCurrentStage(String fullTbName, boolean isMultiStage,
                               Map<String, AtomicInteger> tableStageIndex,
                               Map<Integer, SplitStage> allStages) {
        AtomicInteger index = tableStageIndex.computeIfAbsent(fullTbName, k -> new AtomicInteger(0));
        return allStages.computeIfAbsent(index.get(), k -> new SplitStage(isMultiStage));
    }

    boolean logSerialExecuteInfo(Map<String, List<DefaultRowChange>> allSerialRowChanges) {
        // allSerialRowChanges 并行执行，每个队列内需要事务
        for (String fullTbName : allSerialRowChanges.keySet()) {
            if (log.isDebugEnabled()) {
                log.debug("{} serial row changes will be executed by SplitApplier, rowChanges: {}",
                    fullTbName, allSerialRowChanges.get(fullTbName).size());
            }
        }
        return log.isDebugEnabled();
    }

    Logger getLogger() {
        return log;
    }
}
