/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
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
import com.aliyun.polardbx.binlog.proc.ProcSnapshot;
import com.aliyun.polardbx.binlog.proc.ProcUtils;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.MetricsReporter;
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
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_APPLY_DRY_RUN_ENABLED;

/**
 * @author shicai.xsc 2021/2/20 15:15
 * @since 5.0.0.0
 */
@Slf4j
public class StatisticalProxy implements FlowLimiter {

    private static final int MAX_RETRY = 4;
    private static final StatisticalProxy INSTANCE = new StatisticalProxy();
    private ScheduledExecutorService executorService;
    private ApplierConfig applierConfig;
    private final Logger positionLogger = LogUtil.getPositionLogger();
    private final Logger statisticLogger = LogUtil.getStatisticLogger();
    private String position;
    private long lastEventTimestamp;
    private BaseApplier applier;
    private int tpsLimit;
    private volatile FlowLimiter limiter;
    private Retryer<Void> retryer;
    private final AtomicBoolean lastErrorRemoved = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private StatisticalProxy() {
    }

    public static StatisticalProxy getInstance() {
        return INSTANCE;
    }

    public void init() {
        BasePipeline pipeline = TaskContext.getInstance().getPipeline();
        applierConfig = pipeline.getApplier().getApplierConfig();
        executorService = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("StatisticalProxy"));
        lastEventTimestamp = System.currentTimeMillis();

        applier = pipeline.getApplier();
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
        if (running.compareAndSet(false, true)) {
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
                    log.error("Something goes wrong when logging checkpoint.", e);
                } finally {
                    log.info("Flushed position: {}", position);
                }
            }));
        }
    }

    public void stop() {
        running.compareAndSet(true, false);
    }

    public void apply(List<DBMSEvent> events) throws Exception {
        boolean dryRun = DynamicApplicationConfig.getBoolean(RPL_APPLY_DRY_RUN_ENABLED);
        if (dryRun) {
            return;
        }

        limiter.runTask(events);
    }

    public void tranApply(List<Transaction> transactions) throws Exception {
        boolean dryRun = DynamicApplicationConfig.getBoolean(RPL_APPLY_DRY_RUN_ENABLED);
        if (dryRun) {
            return;
        }

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
            log.warn("batch apply events failure, will change to single apply mode ,exception: ", e1);
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
        StatMetrics.getInstance().addCommitCount(events.size());
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
        if (lastErrorRemoved.compareAndSet(false, true) && !StringUtils.equalsIgnoreCase(this.position, position)) {
            StatisticalProxy.getInstance().removeLastError();
        }
        if (positionLogger.isDebugEnabled()) {
            positionLogger.debug(LogUtil.generatePositionLog(position));
        }

        this.position = position;
        if (positionLogger.isDebugEnabled()) {
            positionLogger.debug(LogUtil.generatePositionLog(position));
        }
    }

    public void recordLastError(String error) {
        if (StringUtils.isBlank(error)) {
            return;
        }
        error = CommonUtils.filterSensitiveInfo(error);

        // 只记录关闭状态前的error
        // 开始关闭后由于连接池关闭等会导致新增报错，会干扰错误判断
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.RUNNING) {
            return;
        }
        if (!running.get()) {
            return;
        }

        lastErrorRemoved.compareAndSet(true, false);
        DbTaskMetaManager.updateTaskLastError(TaskContext.getInstance().getTaskId(), "Time:" +
            new Date(System.currentTimeMillis()) + ", error: " + error);
    }

    private void removeLastError() {
        DbTaskMetaManager.updateTaskLastError(TaskContext.getInstance().getTaskId(), "");
    }

    public void triggerAlarmSync(MonitorType monitorType, Object... args) {
        RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
        if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.RUNNING) {
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
        return BinlogPosition.parseFromString(position);
    }

    public void checkRunningLock() {
        if (!RuntimeLeaderElector.isLeader(RplConstants.RPL_TASK_LEADER_LOCK_PREFIX +
            TaskContext.getInstance().getTaskId())) {
            log.error("another process is already running, will exit");
            TaskContext.getInstance().getPipeline().stop();
        }
    }

    public void flushStatistic() {
        try {
            RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
            if (task == null) {
                log.error("task has been deleted from db");
                TaskContext.getInstance().getPipeline().stop();
                return;
            }
            if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.RUNNING) {
                log.info("task id: {}, task status: {}, exit", task.getId(), task.getStatus());
                TaskContext.getInstance().getPipeline().stop();
                return;
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

    public synchronized void flushPosition() {
        try {
            // get the latest status before update
            RplTask task = DbTaskMetaManager.getTask(TaskContext.getInstance().getTaskId());
            if (task == null) {
                log.error("task has been deleted from db, current runtime will exit!");
                TaskContext.getInstance().getPipeline().stop();
                return;
            }

            // task may be set to STOPPED but the still running
            Date gmtHeartBeat = null;
            if (lastEventTimestamp > 0) {
                gmtHeartBeat = new Date(lastEventTimestamp);
            }
            if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.RUNNING) {
                DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                    null, null, position, null, gmtHeartBeat);
                // compare with stop time
                BinlogPosition binlogPosition = BinlogPosition.parseFromString(position);
                long finishedTimestamp = DynamicApplicationConfig.getLong(ConfigKeys.RPL_INC_STOP_TIME_SECONDS,
                    0L);
                if (binlogPosition != null && finishedTimestamp > 0L &&
                    binlogPosition.getTimestamp() > finishedTimestamp) {
                    DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                        TaskStatus.FINISHED, null, position, null, gmtHeartBeat);
                }
            } else {
                log.error("task is not in running status");
                TaskContext.getInstance().getPipeline().stop();
            }
        } catch (Throwable e) {
            log.error("flush position exception: ", e);
        }
    }

    public void fill(RplStatMetrics rplStatMetrics, StatMetrics statMetrics, JvmSnapshot jvmSnapshot,
                     ProcSnapshot procSnapshot) {
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
        rplStatMetrics.setProcessDelay(statMetrics.getProcessDelay());
        rplStatMetrics.setReceiveDelay(statMetrics.getReceiveDelay());
        rplStatMetrics.setRt(statMetrics.getRt().getTotalCount() / applyTotalCount);
        rplStatMetrics.setSkipCounter(statMetrics.getSkipCounter().get());
        rplStatMetrics.setSkipExceptionCounter(statMetrics.getSkipExceptionCounter().get());
        rplStatMetrics.setTaskId(TaskContext.getInstance().getTaskId());
        rplStatMetrics.setFsmId(TaskContext.getInstance().getStateMachineId());
        rplStatMetrics.setWorkerIp(CommonUtils.getHostIp());
        rplStatMetrics.setCpuUseRatio(procSnapshot == null ? -99 : (int) (procSnapshot.getCpuPercent() * 100));
        rplStatMetrics.setMemUseRatio(userRatio);
        rplStatMetrics.setFullGcCount(jvmSnapshot == null ? -99 : jvmSnapshot.getOldCollectionCount());
        rplStatMetrics.setTotalCommitCount(
            statMetrics.getPeriodCommitCount() + (rplStatMetrics.getTotalCommitCount() == null ? 0 :
                rplStatMetrics.getTotalCommitCount()));
        // receive delay 为 0 说明过去一段时间内未收到event，此时以 pos 与 当前时间的差值作为延迟
        // 否则以 receive delay + process delay 作为延迟
        if (rplStatMetrics.getReceiveDelay() != null && rplStatMetrics.getReceiveDelay() == 0L) {
            BinlogPosition pos = getLatestPosition();
            long timeStamp = pos == null ? 0 : pos.getTimestamp();
            rplStatMetrics.setTrueDelayMills(System.currentTimeMillis() - timeStamp * 1000);
        } else {
            rplStatMetrics.setTrueDelayMills(rplStatMetrics.getReceiveDelay() + rplStatMetrics.getProcessDelay());
        }
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
        RplStatMetrics rplStatMetrics = new RplStatMetrics();
        StatMetrics statMetrics = StatMetrics.getInstance();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        ProcSnapshot procSnapshot = ProcUtils.buildProcSnapshot();
        fill(rplStatMetrics, statMetrics, jvmSnapshot, procSnapshot);
        sendMetrics(rplStatMetrics, jvmSnapshot, procSnapshot);
        if (rplStatMetricsOptional.isPresent()) {
            rplStatMetrics.setId(rplStatMetricsOptional.get().getId());
            mapper.updateByPrimaryKey(rplStatMetrics);
        } else {
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
        if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.RUNNING) {
            DbTaskMetaManager.updateTask(TaskContext.getInstance().getTaskId(),
                null, null, null, JSON.toJSONString(rplStatMetrics),
                gmtHeartBeat);
        } else {
            log.error("task is not in running status");
            TaskContext.getInstance().getPipeline().stop();
        }
    }

    @SneakyThrows
    private void sendMetrics(RplStatMetrics rplSnapshot, JvmSnapshot jvmSnapshot, ProcSnapshot procSnapshot) {
        String prefix = "replica_" + TaskContext.getInstance().getTask().getType() + "_";
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        CommonMetricsHelper.addReplicaMetrics(commonMetrics, rplSnapshot, prefix);
        if (jvmSnapshot != null) {
            CommonMetricsHelper.addJvmMetrics(commonMetrics, jvmSnapshot, prefix);
        }
        if (procSnapshot != null) {
            CommonMetricsHelper.addProcMetrics(commonMetrics, procSnapshot, prefix);
        }
        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }
}
