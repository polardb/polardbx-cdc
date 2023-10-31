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
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.dao.RplStatMetricsDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplStatMetricsMapper;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.CommonUtils;
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
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author shicai.xsc 2021/2/20 15:15
 * @since 5.0.0.0
 */
@Slf4j
public class StatisticalProxy implements FlowLimiter {

    private final static int MAX_RETRY = 4;
    private static final StatisticalProxy instance = new StatisticalProxy();

    private ScheduledExecutorService executorService;
    private ApplierConfig applierConfig;
    private final Logger positionLogger = LogUtil.getPositionLogger();
    private final Logger statisticLogger = LogUtil.getStatisticLogger();
    private String position;
    private long lastEventTimestamp;

    private BaseApplier applier;
    private boolean skipAllException = false;
    private int tpsLimit;
    private volatile FlowLimiter limiter;
    private Retryer<Void> retryer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private StatisticalProxy() {
    }

    public static StatisticalProxy getInstance() {
        return instance;
    }

    public void init() {
        BasePipeline pipeline = TaskContext.getInstance().getPipeline();
        applierConfig = pipeline.getApplier().getApplierConfig();
        executorService = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("StatisticalProxy"));
        lastEventTimestamp = System.currentTimeMillis();

        applier = pipeline.getApplier();
        skipAllException = pipeline.getPipeLineConfig().isSkipException();
        applier.setSkipAllException(skipAllException);
        applier.setSafeMode(pipeline.getPipeLineConfig().isSafeMode());
        tpsLimit = pipeline.getPipeLineConfig().getFixedTpsLimit();
        initFlowLimiter();
        retryer = RetryerBuilder.<Void>newBuilder()
            .retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(pipeline.getPipeLineConfig().getRetryIntervalMs(),
                TimeUnit.MILLISECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(pipeline.getPipeLineConfig().getApplyRetryMaxTime()))
            .build();
        start();
    }

    public void start() {
        running.compareAndSet(false, true);
        executorService.scheduleAtFixedRate(
            this::flushStatistic, 0, applierConfig.getStatisticIntervalSec(), TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            this::flushPosition, 0, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(
            this::checkRunningLock, 0, 1, TimeUnit.SECONDS);
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

    public void stop() {
        running.compareAndSet(true, false);
    }

    public void apply(List<DBMSEvent> events) throws Exception {
        limiter.runTask(events);
    }

    public void tranApply(List<Transaction> transactions) throws Exception {
        limiter.runTranTask(transactions);
    }

    public void applyDdlSql(String schema, String sql) throws Exception {
        log.info("directly apply ddl sql:{}, schema:{}", sql, schema);
        applier.applyDdlSql(schema, sql);
    }

    @Override
    public void runTask(List<DBMSEvent> events) throws ExecutionException, RetryException {
        retryer.call(() -> {
            innerApply(events);
            return null;
        });
    }

    @Override
    public void runTranTask(List<Transaction> transactions) throws ExecutionException, RetryException {
        retryer.call(() -> {
            innerTranApply(transactions);
            return null;
        });
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

    public void innerApply(List<DBMSEvent> events) throws Exception {
        try {
            applier.apply(events);
        } catch (Exception e1) {
            log.warn("batch apply events failure, exception: ", e1);
            for (DBMSEvent event : events) {
                try {
                    applier.apply(Collections.singletonList(event));
                } catch (Exception e2) {
                    log.error("stop because of the msg, " + event.toString(), e2);
                    throw e2;
                }
            }
        }
        StatMetrics.getInstance().doStatOut(events);
    }

    public void innerTranApply(List<Transaction> transactions) throws Exception {
        try {
            applier.tranApply(transactions);
        } catch (Exception e1) {
            log.warn("batch apply transactions failure, exception: ", e1);
            for (Transaction transaction : transactions) {
                try {
                    applier.tranApply(Collections.singletonList(transaction));
                } catch (Exception e2) {
                    log.error("stop because of the msg, " + transaction, e2);
                    Transaction.RangeIterator it = transaction.rangeIterator();
                    while (it.hasNext()) {
                        Transaction.Range range = it.next();
                        List<DBMSEvent> events = range.getEvents();
                        for (DBMSEvent event : events) {
                            log.error("stop because of the msg, " + event);
                        }
                    }
                    throw e2;
                }
            }
        }

        for (Transaction transaction : transactions) {
            Transaction.RangeIterator it = transaction.rangeIterator();
            while (it.hasNext()) {
                Transaction.Range range = it.next();
                List<DBMSEvent> events = range.getEvents();
                StatMetrics.getInstance().doStatOut(events);
            }
        }
    }

    @Override
    public void acquire() {
        if (limiter instanceof TPSLimiter) {
            limiter.acquire();
        }
    }

    public void recordPosition(String position) {
        if (StringUtils.isBlank(position)) {
            return;
        }
        this.position = position;
        positionLogger.debug(LogUtil.generatePositionLog(position));
    }

    public void recordLastError(String error) {
        if (StringUtils.isBlank(error)) {
            return;
        }
        // 只记录关闭状态前的error
        // 开始关闭后由于连接池关闭等会导致新增报错，会干扰错误判断
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task.getStatus() != TaskStatus.RUNNING.getValue()) {
            return;
        }
        if (!running.get()) {
            return;
        }
        DbTaskMetaManager.updateTaskLastError(TaskContext.getInstance().getTaskId(), error);
    }

    public void triggerAlarmSync(MonitorType monitorType, Object... args) {
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task.getStatus() == TaskStatus.RUNNING.getValue()) {
            MonitorManager.getInstance().triggerAlarmSync(monitorType, args);
        } else {
            log.info("receive an invalid alarm trigger sync, monitorType is {}, args is \r\n {}", monitorType, args);
        }

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

    public void checkRunningLock() {
        if (!RuntimeLeaderElector.isLeader(RplConstants.RPL_TASK_LEADER_LOCK_PREFIX +
            TaskContext.getInstance().getTaskId())) {
            log.error("another process is already running, exit");
            TaskContext.getInstance().getPipeline().stop();
        }
    }

    public void flushStatistic() {
        try {
            RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
            if (task == null) {
                log.error("task has been deleted from db");
                TaskContext.getInstance().getPipeline().stop();
            }
            if (task.getStatus() != TaskStatus.RUNNING.getValue()) {
                log.info("task id: {}, task status: {}, exit", task.getId(), TaskStatus.from(task.getStatus()).name());
                TaskContext.getInstance().getPipeline().stop();
            }

            int retry = 0;
            while (retry < MAX_RETRY) {
                try {
                    flushInternal();
                    break;
                } catch (Throwable e) {
                    log.error("StatisticProxy flush failed", e);
                    retry++;
                }
            }
            if (retry >= MAX_RETRY) {
                log.error("StatisticProxy flush failed, retry: {}", retry);
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                    TaskContext.getInstance().getTaskId(), "StatisticProxy flush failed");
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
                TaskContext.getInstance().getPipeline().stop();
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
                TaskContext.getInstance().getPipeline().stop();
            }
        } catch (Throwable e) {
            log.error("flush position exception: ", e);
        }
    }

    public void fill(RplStatMetrics rplStatMetrics) {
        StatMetrics statMetrics = StatMetrics.getInstance();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        int userRatio = (int) (JvmUtils.getTotalUsedRatio() * 100);
        long applyTotalCount = statMetrics.getApplyCount().getTotalCount();
        if (applyTotalCount == 0) {
            applyTotalCount = 1;
        }
        rplStatMetrics.setGmtModified(null);
        rplStatMetrics.setApplyCount(statMetrics.getApplyCount().getSpeed());
        rplStatMetrics.setOutDeleteRps(statMetrics.getDeleteMessageCount().getSpeed());
        rplStatMetrics.setOutUpdateRps(statMetrics.getUpdateMessageCount().getSpeed());
        rplStatMetrics.setOutInsertRps(statMetrics.getInsertMessageCount().getSpeed());
        rplStatMetrics.setInBps(statMetrics.getInBytesCount().getSpeed());
        rplStatMetrics.setInEps(statMetrics.getInMessageCount().getSpeed());
        rplStatMetrics.setOutBps(statMetrics.getOutBytesCount().getSpeed());
        rplStatMetrics.setOutRps(statMetrics.getOutMessageCount().getSpeed());
        rplStatMetrics.setMergeBatchSize(
            statMetrics.getMergeBatchSize().getTotalCount() / applyTotalCount);
        rplStatMetrics.setMsgCacheSize(statMetrics.getTotalInCache().get());
        rplStatMetrics.setPersistMsgCounter(statMetrics.getPersistentMessageCounter().get());
        rplStatMetrics.setProcessDelay(statMetrics.getProcessDelay().get());
        rplStatMetrics.setReceiveDelay(statMetrics.getReceiveDelay().get());
        rplStatMetrics.setRt(statMetrics.getRt().getTotalCount() / applyTotalCount);
        rplStatMetrics.setSkipCounter(statMetrics.getSkipCounter().get());
        rplStatMetrics.setSkipExceptionCounter(statMetrics.getSkipExceptionCounter().get());
        rplStatMetrics.setTaskId(TaskContext.getInstance().getTaskId());
        rplStatMetrics.setFsmId(TaskContext.getInstance().getStateMachineId());
        rplStatMetrics.setWorkerIp(CommonUtils.getHostIp());
        rplStatMetrics.setCpuUseRatio((int) (statMetrics.getCpuRatio() * 100));
        rplStatMetrics.setMemUseRatio(userRatio);
        rplStatMetrics.setFullGcCount(jvmSnapshot.getOldCollectionCount());
        statisticLogger.info(LogUtil.generateStatisticLogV2(rplStatMetrics));
    }

    private void flushInternal() {
        if (position != null) {
            positionLogger.info(LogUtil.generatePositionLog(position));
        }
        // 统计信息会写db
        RplStatMetricsMapper mapper = SpringContextHolder.getObject(RplStatMetricsMapper.class);
        long taskId = TaskContext.getInstance().getTaskId();
        Optional<RplStatMetrics> rplStatMetricsOptional =
            mapper.selectOne(s -> s.where(RplStatMetricsDynamicSqlSupport.taskId,
                SqlBuilder.isEqualTo(taskId)));
        RplStatMetrics rplStatMetrics;
        if (rplStatMetricsOptional.isPresent()) {
            rplStatMetrics = rplStatMetricsOptional.get();
            fill(rplStatMetrics);
            mapper.updateByPrimaryKey(rplStatMetrics);
        } else {
            rplStatMetrics = new RplStatMetrics();
            fill(rplStatMetrics);
            mapper.insert(rplStatMetrics);
        }

        // task may be set to STOPPED but the still running
        Date gmtHeartBeat = null;
        if (lastEventTimestamp > 0) {
            gmtHeartBeat = new Date(lastEventTimestamp);
        }

        // get the latest status before update
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (task == null) {
            log.error("task has been deleted from db");
            TaskContext.getInstance().getPipeline().stop();
        }
        if (task.getStatus() == TaskStatus.RUNNING.getValue()) {
            DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                null, null, null, JSON.toJSONString(rplStatMetrics),
                gmtHeartBeat);
        } else {
            log.error("task is not in running status");
            TaskContext.getInstance().getPipeline().stop();
        }
    }
}
