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
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.RplTablePosition;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
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
    private ApplierConfig applierConfig;
    private Logger positionLogger = LogUtil.getPositionLogger();
    private Logger statisticLogger = LogUtil.getStatisticLogger();
    private String position;
    private long lastEventTimestamp;
    private BaseApplier applier;
    private boolean skipAllException = false;
    private int tpsLimit;
    private volatile FlowLimiter limiter;
    private Retryer<Boolean> retryer;


    private static StatisticalProxy instance = new StatisticalProxy();

    private StatisticalProxy() {
    }

    public static StatisticalProxy getInstance() {
        return instance;
    }

    public boolean init(BasePipeline pipeline, String position) {
        try {
            this.position = position;
            applierConfig = pipeline.getApplier().getApplierConfig();
            executorService = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("StatisticalProxy"));
            lastEventTimestamp = System.currentTimeMillis();


            applier = pipeline.getApplier();
            skipAllException = pipeline.getPipeLineConfig().isSkipException();
            applier.setSkipAllException(skipAllException);
            applier.setSafeMode(pipeline.getPipeLineConfig().isSafeMode());
            tpsLimit = pipeline.getPipeLineConfig().getFixedTpsLimit();
            initFlowLimiter();
            retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .retryIfResult(input -> !input)
                .withWaitStrategy(WaitStrategies.fixedWait(pipeline.getPipeLineConfig().getRetryIntervalMs(),
                    TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(pipeline.getPipeLineConfig().getApplyRetryMaxTime()))
                .build();

        } catch (Throwable e) {
            log.error("StatisticManager init failed", e);
            return false;
        }
        start();
        return true;
    }

    public void start() {
        executorService.scheduleAtFixedRate(
            this::flushStatistic, 0, applierConfig.getStatisticIntervalSec(), TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            this::flushPosition, 0, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            this::checkPipelineConfig, 0, applierConfig.getStatisticIntervalSec(), TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                flushPosition();
            } catch (Throwable e) {
                log.warn("Something goes wrong when logging checkpoint.", e);
            } finally {
                log.info("Flushed position: {}", position);
            }
        }));
    }

    public boolean apply(List<DBMSEvent> events) {
        return limiter.runTask(events);
    }

    public boolean tranApply(List<Transaction> transactions) {
        return limiter.runTranTask(transactions);
    }

    @Override
    public boolean runTask(List<DBMSEvent> events) {
        try {
            return retryer.call(() -> innerApply(events));
        } catch (Exception e) {
            log.error("apply occurs exception: ", e);
            return false;
        }
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
        try {
            RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(TaskContext.getInstance().getTaskId());
            PipelineConfig config = JSON.parseObject(taskConfig.getPipelineConfig(), PipelineConfig.class);
            skipAllException = config.isSkipException();
            applier.setSkipAllException(skipAllException);
            applier.setSafeMode(config.isSafeMode());
            if (this.tpsLimit != config.getFixedTpsLimit()) {
                this.tpsLimit = config.getFixedTpsLimit();
                initFlowLimiter();
            }
        } catch (Throwable e) {
            log.error("check config exception: ", e);
        }
    }

    public boolean innerApply(List<DBMSEvent> events) {
        boolean res = applier.apply(events);
        if (res) {
            return true;
        } else {
            log.warn("batch apply events failure");
            for (DBMSEvent event : events) {
                boolean result = applier.apply(Collections.singletonList(event));
                if (!result) {
                    log.error("stop because of the msg, " + event.toString());
                    return false;
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

    public boolean applyDdlSql(String schema, String sql) throws Exception {
        log.debug("directly apply ddl sql:{}, schema:{}", sql, schema);
        return applier.applyDdlSql(schema, sql);
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

    public long computeTaskDelay() {
        return FSMMetaManager.computeTaskDelay(DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId()));
    }

    public BinlogPosition getLatestPosition() {
        if (StringUtils.isNotBlank(position)) {
            return BinlogPosition.parseFromString(position);
        }
        return null;
    }

    public void flushStatistic() {
        try {
            RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
            if (task == null) {
                log.error("task has been deleted from db");
                System.exit(-1);;
            }
            if (task.getStatus() != TaskStatus.RUNNING.getValue()) {
                log.info("task id: {}, task status: {}, exit", task.getId(), TaskStatus.from(task.getStatus()).name());
                System.exit(-1);;
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
                System.exit(-1);
            }
        } catch (Throwable e) {
            log.error("flush statistic exception: ", e);
        }
    }

    public void flushPosition() {
        try {
            // get the latest status before update
            RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
            if (task == null) {
                log.error("task has been deleted from db");
                System.exit(-1);;
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
                System.exit(-1);;
            }
        } catch (Throwable e) {
            log.error("flush position exception: ", e);
        }
    }

    private void flushInternal() {
        StatisticUnit unit = new StatisticUnit();
        flushFields(unit, INTERVAL_1M, 60);
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
            System.exit(-1);
        }
        if (task.getStatus() == TaskStatus.RUNNING.getValue()) {
            DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                null, null, null, JSON.toJSONString(unit),
                gmtHeartBeat);
        } else {
            log.error("task is not in running status");
            System.exit(-1);;
        }
    }

    private void flushFields(StatisticUnit unit, String intervalItem, int seconds) {
        long curApplyCount = applyCount.getTotalCount();
        long curMessageCount = messageCount.getTotalCount();
        long curBatchSize = mergeBatchSize.getTotalCount();
        long curRt = rt.getTotalCount();

        unit.getTotalConsumeMessageCount().put(intervalItem, curMessageCount);
        unit.getMessageRps().put(intervalItem, curMessageCount / seconds);
        unit.getApplyQps().put(intervalItem, curApplyCount / seconds);
        if (curApplyCount > 0) {
            unit.getAvgMergeBatchSize().put(intervalItem, curBatchSize / curApplyCount);
            unit.getApplyRt().put(intervalItem, curRt / curApplyCount);
        }
    }
}
