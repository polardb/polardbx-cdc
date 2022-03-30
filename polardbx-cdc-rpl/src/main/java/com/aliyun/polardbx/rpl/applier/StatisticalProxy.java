/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplTablePosition;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * @author shicai.xsc 2021/2/20 15:15
 * @since 5.0.0.0
 */
@Slf4j
public class StatisticalProxy implements FlowLimiter {

    private final static int MAX_RETRY = 4;
    private final static int COUNTER_OUTDATE_SECONDS = 60;
    private final static int HEARTBEAT_OUTDATE_SECONDS = 120;

    public final static String INTERVAL_1M = "oneMinute";

    private StatisticCounter messageCount = new StatisticCounter(COUNTER_OUTDATE_SECONDS);
    private StatisticCounter mergeBatchSize = new StatisticCounter(COUNTER_OUTDATE_SECONDS);
    private StatisticCounter rt = new StatisticCounter(COUNTER_OUTDATE_SECONDS);
    private StatisticCounter applyCount = new StatisticCounter(COUNTER_OUTDATE_SECONDS);

    private AtomicLong skipCounter = new AtomicLong();
    private AtomicLong skipExceptionCounter = new AtomicLong();
    private AtomicLong persistentMessageCounter = new AtomicLong();

    private AtomicLong totalInCache = new AtomicLong();

    private ScheduledExecutorService executorService;
    private ExtractorConfig extractorConfig;
    private ApplierConfig applierConfig;
    private Logger positionLogger = LogUtil.getPositionLogger();
    private Logger statisticLogger = LogUtil.getStatisticLogger();
    private String position;
    private long lastEventTimestamp;
    private BaseApplier applier;
    private boolean skipAllException = false;
    private int tpsLimit;
    private volatile FlowLimiter limiter;

    private Map<String, String> lastRunTablePositions = new HashMap<>();
    private static StatisticalProxy instance = new StatisticalProxy();

    private StatisticalProxy() {
    }

    public static StatisticalProxy getInstance() {
        return instance;
    }

    public boolean init(BasePipeline pipeline, String position) {
        try {
            this.position = position;
            this.extractorConfig = pipeline.getExtractor().getExtractorConfig();
            this.applierConfig = pipeline.getApplier().getApplierConfig();
            executorService = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("StatisticalProxy"));
            lastEventTimestamp = System.currentTimeMillis();

            // 加载上次任务崩溃前各个表已经执行到的位点
            List<RplTablePosition> rplTablePositions = DbTaskMetaManager
                .listTablePosition(TaskContext.getInstance().getTaskId());
            for (RplTablePosition rplTablePosition : rplTablePositions) {
                lastRunTablePositions.put(rplTablePosition.getFullTableName(), rplTablePosition.getPosition());
            }

            applier = pipeline.getApplier();
            skipAllException = pipeline.getPipeLineConfig().isSkipException();
            applier.setSkipAllException(skipAllException);
            tpsLimit = pipeline.getPipeLineConfig().getFixedTpsLimit();
            initFlowLimiter();

        } catch (Throwable e) {
            log.error("StatisticManager init failed", e);
            return false;
        }
        start();
        return true;
    }

    public void start() {
        executorService.scheduleAtFixedRate(
            () -> flushStatistic(), 0, applierConfig.getStatisticIntervalSec(), TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            () -> flushPosition(), 0, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            () -> checkPipelineConfig(), 0, applierConfig.getStatisticIntervalSec(), TimeUnit.SECONDS);
    }

    public boolean apply(List<DBMSEvent> events) {
        return limiter.runTask(events);
    }

    public boolean tranApply(List<Transaction> transactions) {
        return limiter.runTranTask(transactions);
    }

    @Override
    public boolean runTask(List<DBMSEvent> events) {
        return innerApply(events);
    }

    @Override
    public boolean runTranTask(List<Transaction> transactions) {
        return innerTranApply(transactions);
    }

    private void initFlowLimiter() {
        FlowLimiter newLimitBucket = this;
        if (tpsLimit > 0) {
            newLimitBucket = new TPSLimiter(tpsLimit, newLimitBucket);
        }
        limiter = newLimitBucket;
    }

    private void checkPipelineConfig() {
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        PipelineConfig config = JSON.parseObject(task.getPipelineConfig(), PipelineConfig.class);
        skipAllException = config.isSkipException();
        applier.setSkipAllException(skipAllException);
        if (this.tpsLimit != config.getFixedTpsLimit()) {
            this.tpsLimit = config.getFixedTpsLimit();
            initFlowLimiter();
        }
    }

    public boolean innerApply(List<DBMSEvent> events) {
        boolean res = applier.apply(events);
        if (res) {
            return true;
        } else {
            log.warn("batch apply events failure");
            for (DBMSEvent event : events) {
                boolean result = applier.apply(Arrays.asList(event));
                if (!result) {
                    log.error("stop because of the msg, " + event.toString());
                    Runtime.getRuntime().halt(1);
                }
            }
        }
        return true;
    }

    public boolean innerTranApply(List<Transaction> transactions) {
        boolean res = applier.tranApply(transactions);
        if (res) {
            return true;
        } else {
            log.warn("batch apply transactions failure");
            for (Transaction transaction : transactions) {
                boolean result = applier.tranApply(Arrays.asList(transaction));
                if (!result) {
                    if (skipAllException) {
                        log.warn("SKIP_ALL_EXCEPTION is true, will skip the transaction, " + transaction.toString());
                        addSkipExceptionCount(1);
                    } else {
                        log.warn("SKIP_ALL_EXCEPTION is false, stop because of transaction, " + transaction.toString());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void recordPosition(String position, boolean log) {
        if (StringUtils.isBlank(position)) {
            return;
        }
        this.position = position;
        if (log) {
            positionLogger.info(LogUtil.generatePositionLog(position));
        }
    }

    public void setTotalInCache(long totalInCache) {
        this.totalInCache.set(totalInCache);
    }

    public void addMessageCount(long count) {
        messageCount.add(count);
        persistentMessageCounter.addAndGet(count);
    }

    public void addMergeBatchSize(long count) {
        mergeBatchSize.add(count);
    }

    public void addRt(long count) {
        rt.add(count);
    }

    public void addApplyCount(long count) {
        applyCount.add(count);
    }

    public void addSkipCount(long count) {
        skipCounter.addAndGet(count);
    }

    public void addSkipExceptionCount(long count) {
        skipExceptionCounter.addAndGet(count);
    }

    public void heartbeat() {
        lastEventTimestamp = System.currentTimeMillis();
    }

    public void recordTablePosition(String tbName, DefaultRowChange lastRowChange) {
        String position = (String) (lastRowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
        DbTaskMetaManager
            .updateTablePosition(TaskContext.getInstance().getStateMachineId(),
                TaskContext.getInstance().getServiceId(), TaskContext.getInstance().getTaskId(), tbName, position);
    }

    public void deleteTaskTablePosition() {
        DbTaskMetaManager.deleteTablePositionByTask(TaskContext.getInstance().getTaskId());
    }

    public Map<String, String> getLastRunTablePositions() {
        return lastRunTablePositions;
    }

    public void flushStatistic() {
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task == null) {
            log.error("task has been deleted from db");
            Runtime.getRuntime().halt(1);;
        }
        if (task.getStatus() != TaskStatus.RUNNING.getValue()) {
            log.info("task id: {}, task status: {}, exit", task.getId(), TaskStatus.from(task.getStatus()).name());
            Runtime.getRuntime().halt(1);;
        }

        int retry = 0;
        while (retry < MAX_RETRY) {
            try {
                flushInternal();
                break;
            } catch (Throwable e) {
                log.error("StatisticManager flush failed", e);
                retry++;
            }
        }

        if (retry >= MAX_RETRY) {
            log.error("StatisticManager flush failed, retry: {}, process exit", retry);
            Runtime.getRuntime().halt(1);;
        }

        // In some cases, for example, Polar-x CDC dumper restarts, no events will be dumped to Replica and no error throws,
        // so we need to check the last event timestamp and restart the Replica
        if (extractorConfig.isEnableHeartbeat()
            && lastEventTimestamp > 0
            && System.currentTimeMillis() - lastEventTimestamp > HEARTBEAT_OUTDATE_SECONDS * 1000) {
            log.error("lastEventTimestamp: {} exceeds {} seconds, process exit", new Date(lastEventTimestamp),
                HEARTBEAT_OUTDATE_SECONDS);
            Runtime.getRuntime().halt(1);;
        }
    }

    public void flushPosition() {
        // get the latest status before update
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task == null) {
            log.error("task has been deleted from db");
            Runtime.getRuntime().halt(1);;
        }
        // task may be set to STOPPED but the still running
        Date gmtHeartBeat = null;
        if (lastEventTimestamp > 0) {
            gmtHeartBeat = new Date(lastEventTimestamp);
        }
        if (task.getStatus() == TaskStatus.RUNNING.getValue()) {
            DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                null, null, position, null,
                gmtHeartBeat);
        } else {
            log.error("task is not in running status");
            Runtime.getRuntime().halt(1);;
        }
    }

    private void flushInternal() {
        StatisticUnit unit = new StatisticUnit();
        flushFields(unit, INTERVAL_1M, 1 * 60);
        unit.setSkipCounter(skipCounter.get());
        unit.setSkipExceptionCounter(skipExceptionCounter.get());
        unit.setPersistentMessageCounter(persistentMessageCounter.get());

        statisticLogger.info(LogUtil.generateStatisticLog(unit, INTERVAL_1M, totalInCache.get()));

        // task may be set to STOPPED but the still running
        Date gmtHeartBeat = null;
        if (lastEventTimestamp > 0) {
            gmtHeartBeat = new Date(lastEventTimestamp);
        }

        // get the latest status before update
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task == null) {
            log.error("task has been deleted from db");
            Runtime.getRuntime().halt(1);;
        }
        if (task.getStatus() == TaskStatus.RUNNING.getValue()) {
            DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                null, null, null, JSON.toJSONString(unit),
                gmtHeartBeat);
        } else {
            log.error("task is not in running status");
            Runtime.getRuntime().halt(1);;
        }
    }

    private void flushFields(StatisticUnit unit, String intervalItem, int seconds) {
        long curApplyCount = applyCount.getTotalCount();
        long curMessageCount = messageCount.getTotalCount();
        long curBatchSize = mergeBatchSize.getTotalCount();
        long curRt = rt.getTotalCount();

        unit.getTotalConsumeMessageCount().put(intervalItem, curMessageCount);
        unit.getMessageTps().put(intervalItem, curMessageCount / seconds);
        unit.getApplyTps().put(intervalItem, curApplyCount / seconds);
        if (curApplyCount > 0) {
            unit.getAvgMergeBatchSize().put(intervalItem, curBatchSize / curApplyCount);
            unit.getApplyRt().put(intervalItem, curRt / curApplyCount);
        }
    }
}
