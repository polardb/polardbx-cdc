/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.DecompressionStatistics;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.dao.RplStatMetricsDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplStatMetricsMapper;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.error.DdlApplyException;
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
import lombok.Getter;
import lombok.Setter;
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
    /**
     * 延迟未知时的落库哨兵值：true_delay_mills 列为 bigint unsigned NOT NULL 不能存 null，
     * 选择 Long.MAX_VALUE 因其不可能是真实延迟，查表方可明确识别为"未知"而非误读为 0 延迟。
     * 注意：该值也会随 DbTaskMetaManager.updateTask 的 JSON 序列化写入任务元数据，
     * Java/fastjson 侧无精度问题，但若该 JSON 被前端 JS 消费，超出 2^53 会失真。
     */
    private static final long TRUE_DELAY_MILLS_UNKNOWN = Long.MAX_VALUE;
    private static final StatisticalProxy INSTANCE = new StatisticalProxy();
    private ScheduledExecutorService executorService;
    private final Logger positionLogger = LogUtil.getPositionLogger();
    private final Logger statisticLogger = LogUtil.getStatisticLogger();
    private final Logger decompreesionLogger = LogUtil.getDecompreesionLogger();
    @Getter
    private String position;
    private long lastEventTimestamp;
    @Getter
    @Setter
    private BaseApplier applier;
    private int tpsLimit;
    private volatile FlowLimiter limiter;
    protected Retryer<Void> retryer;
    private final AtomicBoolean lastErrorRemoved = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    int flushInterval;

    private StatisticalProxy() {
    }

    public static StatisticalProxy getInstance() {
        return INSTANCE;
    }

    public void init() {
        BasePipeline pipeline = TaskContext.getInstance().getPipeline();
        executorService = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("StatisticalProxy"));
        lastEventTimestamp = System.currentTimeMillis();
        applier = pipeline.getApplier();
        tpsLimit = pipeline.getPipeLineConfig().getFixedTpsLimit();
        initFlowLimiter();
        retryer = buildRetryer(pipeline.getPipeLineConfig().getRetryIntervalMs(),
            pipeline.getPipeLineConfig().getApplyRetryMaxTime());
        flushInterval = DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND);
        position = TaskContext.getInstance().getTask().getPosition();
        start();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executorService.scheduleAtFixedRate(
                this::flushStatistic, 0, flushInterval, TimeUnit.SECONDS);
            executorService.scheduleAtFixedRate(
                this::flushPosition, 0, 1, TimeUnit.SECONDS);
            executorService.scheduleAtFixedRate(
                this::checkRunningLock, 0, 1, TimeUnit.SECONDS);
            executorService.scheduleAtFixedRate(
                this::checkPipelineConfig, 0, flushInterval, TimeUnit.SECONDS);

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

    /*
     * ddl 不需要、也没必要重试，如果重试的话会影响first ddl的判断
     * 参见：com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog.getFirstDdl
     */
    public void innerApply(List<DBMSEvent> events) throws Exception {
        try {
            if (events == null || events.isEmpty()) {
                return;
            }
            StatMetrics.getInstance().addApplyAttemptCount(1);
            if (!DynamicApplicationConfig.getBoolean(RPL_APPLY_DRY_RUN_ENABLED)) {
                applier.apply(events);
            }
        } catch (Exception e) {
            if (events.size() == 1 && DdlApplyHelper.isDdl(events.get(0))) {
                throw new DdlApplyException(e);
            } else {
                log.warn("batch apply events failure, will change to single apply mode , exception: ", e);
                retryApplyEventOneByOne(events);
            }
        }

        StatMetrics.getInstance().doStatOut(events);
        StatMetrics.getInstance().addCommitCount(events);
    }

    private void retryApplyEventOneByOne(List<DBMSEvent> events) throws Exception {
        for (DBMSEvent event : events) {
            try {
                applier.apply(Collections.singletonList(event));
            } catch (Exception e2) {
                log.error("stop because of the msg, " + event.toString(), e2);
                throw e2;
            }
        }
    }

    /*
     * ddl 不需要、也没必要重试，如果重试的话会影响first ddl的判断
     * 参见：com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog.getFirstDdl
     */
    public void innerTranApply(List<Transaction> transactions) throws Exception {
        try {
            applier.tranApply(transactions);
        } catch (Exception e) {
            if (transactions.size() == 1 && transactions.get(0).getEventCount() > 0
                && DdlApplyHelper.isDdl(transactions.get(0).peekFirst())) {
                throw new DdlApplyException(e);
            } else {
                log.warn("batch apply transactions failure, will change to single apply mode, exception: ", e);
                retryApplyTransOneByOne(transactions);
            }
        }
    }

    private void retryApplyTransOneByOne(List<Transaction> transactions) throws Exception {
        for (Transaction transaction : transactions) {
            try {
                applier.tranApply(Collections.singletonList(transaction));
            } catch (Exception e2) {
                log.error("stop because of the msg ", e2);
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
        if (!StringUtils.equalsIgnoreCase(this.position, position) && lastErrorRemoved.compareAndSet(false, true)) {
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
        long periodApplyCount = statMetrics.getApplyCount().getAndSet(0);
        if (periodApplyCount == 0) {
            periodApplyCount = 1;
        }
        long periodInsertCount = statMetrics.getInsertMessageCount().getAndSet(0);
        long periodUpdateCount = statMetrics.getUpdateMessageCount().getAndSet(0);
        long periodDeleteMessageCount = statMetrics.getDeleteMessageCount().getAndSet(0);
        long periodInBytesCount = statMetrics.getInBytesCount().getAndSet(0);
        long periodOutBytesCount = statMetrics.getOutBytesCount().getAndSet(0);
        long periodInMessageCount = statMetrics.getInMessageCount().getAndSet(0);
        long periodOutMessageCount = statMetrics.getOutMessageCount().getAndSet(0);
        long periodMergeBatchSize = statMetrics.getMergeBatchSize().getAndSet(0);
        long periodCommitCount = statMetrics.getPeriodCommitCount().getAndSet(0);
        long periodRt = statMetrics.getRt().getAndSet(0);
        long lastTotalInCache = statMetrics.getTotalInCache().getAndSet(0);
        long lastProcessDelay = statMetrics.getProcessDelay().getAndSet(0);
        long lastReceiveDelay = statMetrics.getReceiveDelay().getAndSet(0);
        long lastTotalApplyDelay = statMetrics.getTotalApplyDelay().getAndSet(0);
        long lastApplyAttemptCount = statMetrics.getApplyAttemptCount().getAndSet(0);
        long lastHeartbeatCount = statMetrics.getHeartbeatCount().getAndSet(0);
        long periodSkipCounter = statMetrics.getSkipCounter().getAndSet(0);
        long periodSkipExceptionCounter = statMetrics.getSkipExceptionCounter().getAndSet(0);
        long persistMsgCounter = statMetrics.getPersistentMessageCounter().get();
        rplStatMetrics.setGmtModified(null);
        rplStatMetrics.setApplyCount(periodApplyCount / flushInterval);
        rplStatMetrics.setOutInsertRps(periodInsertCount / flushInterval);
        rplStatMetrics.setOutUpdateRps(periodUpdateCount / flushInterval);
        rplStatMetrics.setOutDeleteRps(periodDeleteMessageCount / flushInterval);
        rplStatMetrics.setInBps(periodInBytesCount / flushInterval);
        rplStatMetrics.setInEps(periodInMessageCount / flushInterval);
        rplStatMetrics.setOutBps(periodOutBytesCount / flushInterval);
        rplStatMetrics.setOutRps(periodOutMessageCount / flushInterval);
        rplStatMetrics.setMergeBatchSize(periodMergeBatchSize / periodApplyCount);
        rplStatMetrics.setMsgCacheSize(lastTotalInCache);
        rplStatMetrics.setPersistMsgCounter(persistMsgCounter);
        rplStatMetrics.setProcessDelay(lastProcessDelay);
        rplStatMetrics.setReceiveDelay(lastReceiveDelay);
        rplStatMetrics.setRt(periodRt / periodApplyCount);
        rplStatMetrics.setSkipCounter(periodSkipCounter);
        rplStatMetrics.setSkipExceptionCounter(periodSkipExceptionCounter);
        rplStatMetrics.setTaskId(TaskContext.getInstance().getTaskId());
        rplStatMetrics.setFsmId(TaskContext.getInstance().getStateMachineId());
        rplStatMetrics.setWorkerIp(CommonUtils.getHostIp());
        rplStatMetrics.setCpuUseRatio(procSnapshot == null ? -99 : (int) (procSnapshot.getCpuPercent() * 100));
        rplStatMetrics.setMemUseRatio(userRatio);
        rplStatMetrics.setFullGcCount(jvmSnapshot == null ? -99 : jvmSnapshot.getOldCollectionCount());
        rplStatMetrics.setTotalCommitCount(
            periodCommitCount + (rplStatMetrics.getTotalCommitCount() == null ? 0 :
                rplStatMetrics.getTotalCommitCount()));
        // 计算真实延迟
        // 1. 有event被成功apply：使用基于同一event的端到端延迟(extractDelay + processDelay)
        // 2. 尝试过apply但未成功计算出totalApplyDelay（首批失败/仅非数据event）：用position时间差
        // 3. 有event进来但没有尝试apply（系统空闲，仅数据event）：用receiveDelay
        // 4. 仅收到心跳event，无数据event：master空闲，slave已追上，延迟为0
        // 5. 完全没收到event：用position时间差兜底；无有效位点时返回 null，
        //    trueDelayMills 保持 null 表示延迟未知，上报链路不吐出该指标
        if (lastTotalApplyDelay > 0) {
            rplStatMetrics.setTrueDelayMills(lastTotalApplyDelay);
        } else if (lastApplyAttemptCount > 0) {
            Long positionDelay = calculatePositionDelayMillis(
                getLatestPosition(), System.currentTimeMillis());
            if (positionDelay != null) {
                rplStatMetrics.setTrueDelayMills(positionDelay);
            }
        } else if (lastReceiveDelay > 0) {
            rplStatMetrics.setTrueDelayMills(lastReceiveDelay);
        } else if (lastHeartbeatCount > 0) {
            rplStatMetrics.setTrueDelayMills(0L);
        } else {
            Long positionDelay = calculatePositionDelayMillis(
                getLatestPosition(), System.currentTimeMillis());
            if (positionDelay != null) {
                rplStatMetrics.setTrueDelayMills(positionDelay);
            }
        }
        statisticLogger.info(LogUtil.generateStatisticLogV2(rplStatMetrics));
        decompreesionLogger.info(DecompressionStatistics.getDecompressionInfo());
    }

    /**
     * 基于位点时间戳计算延迟毫秒数。
     * <p>
     * 返回值语义：
     * <ul>
     * <li>null：延迟未知（无有效位点，position 为 null 或 timestamp <= 0），该指标不上报，
     * 监控通过指标缺失（no data）识别"未知"状态。</li>
     * <li>0：位点时间戳超前于当前时间（时钟偏斜），视为近似追平，不是未知。</li>
     * <li>正值：正常的位点延迟毫秒数。</li>
     * </ul>
     * 注意：null 仅存在于内存指标链路，写库前会在 flushInternal 中兜底为 TRUE_DELAY_MILLS_UNKNOWN 哨兵值
     * （true_delay_mills 列为 unsigned NOT NULL，极大值可避免查表方误读为 0 延迟）。
     */
    static Long calculatePositionDelayMillis(BinlogPosition position, long currentTimeMillis) {
        if (position == null || position.getTimestamp() <= 0) {
            // 无有效位点，延迟未知，返回 null（上报链路不吐出该指标）
            return null;
        }

        long currentTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis);
        if (position.getTimestamp() > currentTimeSeconds) {
            // 未来位点视为时钟偏斜，近似追平，返回 0 而非未知
            return 0L;
        }

        return currentTimeMillis - TimeUnit.SECONDS.toMillis(position.getTimestamp());
    }

    Retryer<Void> buildRetryer(long retryInterval, int retryAttemptCount) {
        return RetryerBuilder.<Void>newBuilder()
            .retryIfException(t -> !(t instanceof DdlApplyException))
            .withWaitStrategy(
                WaitStrategies.fixedWait(retryInterval, TimeUnit.MILLISECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(retryAttemptCount))
            .build();
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
        // true_delay_mills 列为 bigint unsigned NOT NULL，无位点时 trueDelayMills 为 null（延迟未知），
        // 真实"未知"语义由上面的 sendMetrics 上报链路缺失该指标表达，
        // 落库用极大哨兵值而非 0，避免直接查表的运维方误读为"零延迟/已追平"
        if (rplStatMetrics.getTrueDelayMills() == null) {
            rplStatMetrics.setTrueDelayMills(TRUE_DELAY_MILLS_UNKNOWN);
        }
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
        String prefixNew = "replica_" + TaskContext.getInstance().getTask().getType() + "_"
            + TaskContext.getInstance().getTask().getId() + "_";
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        CommonMetricsHelper.addReplicaMetrics(commonMetrics, rplSnapshot, prefixNew);
        if (jvmSnapshot != null) {
            CommonMetricsHelper.addJvmMetrics(commonMetrics, jvmSnapshot, prefixNew);
        }
        if (procSnapshot != null) {
            CommonMetricsHelper.addProcMetrics(commonMetrics, procSnapshot, prefixNew);
        }
        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.leaderReport(commonMetrics);
        }
    }
}
