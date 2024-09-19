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
package com.aliyun.polardbx.rpl.pipeline;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.BaseApplier;
import com.aliyun.polardbx.rpl.applier.DdlApplyHelper;
import com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier;
import com.aliyun.polardbx.rpl.applier.RecoveryApplier;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.applier.Transaction;
import com.aliyun.polardbx.rpl.applier.TransactionApplier;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.extractor.full.MysqlFullExtractor;
import com.aliyun.polardbx.rpl.storage.RplStorage;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorType;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DELAY_ALARM_THRESHOLD_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_APPLY_BATCH_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_APPLY_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

/**
 * @author shicai.xsc 2020/11/30 14:59
 * @since 5.0.0.0
 */
@Slf4j
public class SerialPipeline extends BasePipeline {

    private static final long LOG_STAT_PERIOD_MILLS = 10000;
    private final AtomicLong lastStatisticsTime = new AtomicLong(0);
    private final AtomicLong periodBatchSize = new AtomicLong(0);
    private final AtomicLong periodBatchCount = new AtomicLong(0);
    private final AtomicLong periodBatchCost = new AtomicLong(0);
    private final AtomicBoolean firstDdl = new AtomicBoolean(true);
    private RingBuffer<MessageEvent> msgRingBuffer;
    private ExecutorService offerExecutor;
    private BatchEventProcessor<MessageEvent> offerProcessor;
    private String position;
    private static final boolean isLabEnv = getBoolean(ConfigKeys.IS_LAB_ENV);

    private static boolean srcIsPolarx = true;

    public SerialPipeline(PipelineConfig pipeLineConfig, BaseExtractor extractor, BaseApplier applier) {
        this.pipeLineConfig = pipeLineConfig;
        this.extractor = extractor;
        this.applier = applier;
        if (extractor.getExtractorConfig().getExtractorType() == ExtractorType.RPL_INC) {
            ReplicaMeta replicaMeta = JSON.parseObject(extractor.getExtractorConfig().getPrivateMeta(),
                ReplicaMeta.class);
            srcIsPolarx = replicaMeta.getMasterType() == HostType.POLARX2;
        }
    }

    public static boolean shouldSkip(DBMSEvent dbmsEvent, String maxDdlTsoCheckpoint) {
        String eventTso = dbmsEvent.getRtso();
        if (StringUtils.isBlank(eventTso)) {
            // 只在实验室环境下校验tso存在性
            if (isLabEnv && srcIsPolarx &&  (dbmsEvent instanceof DefaultQueryLog ||
                dbmsEvent instanceof DefaultRowChange)) {
                log.error("dbms event tso should not be null！ position {}, event content {}",
                    dbmsEvent.getPosition(), dbmsEvent);
                throw new PolardbxException("dbms event tso should not be null！");
            } else {
                return false;
            }
        } else {
            return StringUtils.isNotBlank(maxDdlTsoCheckpoint) && eventTso.compareTo(maxDdlTsoCheckpoint) < 0;
        }
    }

    @Override
    public void init() throws Exception {
        if (!(extractor instanceof MysqlFullExtractor)) {
            MessageEventFactory messageEventFactory = new MessageEventFactory();
            offerExecutor = ThreadPoolUtil.createExecutorWithFixedNum(1, "applier");
            // create ringBuffer and set ringBuffer eventFactory
            msgRingBuffer = RingBuffer.createSingleProducer(
                messageEventFactory, pipeLineConfig.getBufferSize(), new BlockingWaitStrategy());
            EventHandler<MessageEvent> eventHandler;
            SequenceBarrier sequenceBarrier = msgRingBuffer.newBarrier();
            if (pipeLineConfig.isSupportXa()) {
                eventHandler = new XaTranRingBufferEventHandler(pipeLineConfig.getBufferSize());
            } else if (applier instanceof TransactionApplier) {
                eventHandler = new TranRingBufferEventHandler(pipeLineConfig.getBufferSize());
            } else if (applier instanceof RecoveryApplier) {
                eventHandler = new RecoveryEventHandler(pipeLineConfig.getBufferSize());
            } else {
                eventHandler = new RingBufferEventHandler(pipeLineConfig.getBufferSize());
            }
            offerProcessor = new BatchEventProcessor<>(msgRingBuffer, sequenceBarrier, eventHandler);
            msgRingBuffer.addGatingSequences(offerProcessor.getSequence());
        }
    }

    @Override
    public void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            try {
                // start extractor thread which will call EXTRACTOR to extract events from
                // binlog and write it to ringBuffer
                log.info("extractor and applier starting");
                extractor.start();
                applier.start();
                log.info("extractor and applier started");

                // start offerProcessor thread which will call APPLIER to consume events from
                // ringBuffer
                if (!(extractor instanceof MysqlFullExtractor)) {
                    offerExecutor.submit(offerProcessor);
                }
            } catch (Exception e) {
                log.error("start extractor occur error", e);
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                    TaskContext.getInstance().getTaskId(), "apply error");
                StatisticalProxy.getInstance().recordLastError(e.toString());
                throw e;
            }
        }
    }

    @Override
    public void stop() {
        // only stop once
        if (running.compareAndSet(true, false)) {
            try {
                StatisticalProxy.getInstance().stop();
                extractor.stop();
                applier.stop();
            } finally {
                System.exit(1);
            }
        }
    }

    @Override
    public boolean checkDone() {
        return extractor.isDone();
    }

    /**
     * This will be called by EXTRACTOR to write messages to ringBuffer
     */
    @Override
    public void writeRingbuffer(List<MessageEvent> events) {
        long lo = -1, hi = -1;
        boolean isLowSet = false;
        try {
            for (MessageEvent event : events) {
                while (msgRingBuffer.remainingCapacity() <= 0) {
                    if (lo != -1 && hi != -1) {
                        msgRingBuffer.publish(lo, hi);
                        lo = -1;
                        hi = -1;
                        isLowSet = false;
                    } else {
                        Thread.sleep(5);
                    }
                }
                long next = msgRingBuffer.next();
                if (!isLowSet) {
                    lo = next;
                    isLowSet = true;
                }
                hi = next;
                MessageEvent e = msgRingBuffer.get(next);
                e.setDbmsEvent(event.getDbmsEventDirect());
                e.setXaTransaction(event.getXaTransaction());
                e.setPersistKey(event.getPersistKey());
                e.setRepoUnit(event.getRepoUnit());
            }
        } catch (Throwable e) {
            log.error("writeRingBuffer exception ", e);
            throw new PolardbxException("write ring buffer error!", e);
        } finally {
            if (lo != -1 && hi != -1) {
                msgRingBuffer.publish(lo, hi);
            }
        }
    }

    @Override
    public void directApply(List<DBMSEvent> events) throws Exception {
        StatisticalProxy.getInstance().apply(events);
    }

    private void takeStatisticsWithFlowControl(long currentBatchSize, long sequence, long start, int xaNum,
                                               boolean parallelApply) {
        if (!parallelApply) {
            StatisticalProxy.getInstance().recordPosition(position);
        }

        long now = System.currentTimeMillis();
        long lastStatisticsTimePre = lastStatisticsTime.get();
        periodBatchSize.addAndGet(currentBatchSize);
        periodBatchCost.addAndGet(now - start);
        periodBatchCount.incrementAndGet();

        if ((parallelApply || now > lastStatisticsTimePre + LOG_STAT_PERIOD_MILLS)
            && lastStatisticsTime.compareAndSet(lastStatisticsTimePre, now)) {
            if (!parallelApply) {
                StatisticalProxy.getInstance().recordPosition(position);
            }
            long avgBatchSize = periodBatchSize.get() / periodBatchCount.get();
            long avgCost = periodBatchCost.get() / periodBatchCount.get();
            long queueSize = msgRingBuffer.getCursor() - sequence;
            log.info("RingBuffer queue size : " + queueSize + ", average batch size : " + avgBatchSize + ", avg cost : "
                + avgCost + ", current batch size : " + currentBatchSize + ", current cost : " + (now - start));
            if (xaNum != 0) {
                log.info("Exist unfinished xa num: {}", xaNum);
            }

            // reset参数
            periodBatchCount.set(0);
            periodBatchSize.set(0);
            periodBatchCost.set(0);
        }
    }

    /**
     * RingBufferEventHandler, this will call APPLIER to consume ringBuffer messages
     */
    private class RingBufferEventHandler implements EventHandler<MessageEvent>, LifecycleAware {

        private final List<DBMSEvent> eventBatch;
        private final boolean parallelSchemaApplyEnabled;
        private final ParallelSchemaApplier parallelSchemaApplier;
        private final String maxDdlCheckpointTso;

        public RingBufferEventHandler(int batchSize) {
            eventBatch = new ArrayList<>(batchSize / 2);
            parallelSchemaApplyEnabled = DynamicApplicationConfig.getBoolean(RPL_PARALLEL_SCHEMA_APPLY_ENABLED);
            parallelSchemaApplier = new ParallelSchemaApplier();
            maxDdlCheckpointTso = DbTaskMetaManager.getLatestSubmittedTsoByTask(
                TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        }

        @Override
        public void onEvent(MessageEvent messageEvent, long sequence, boolean endOfBatch) {
            try {
                DBMSEvent dbmsEvent = messageEvent.getDbmsEventEffective();

                if (parallelSchemaApplyEnabled) {
                    eventBatch.add(dbmsEvent);
                } else {
                    if (shouldSkip(dbmsEvent, maxDdlCheckpointTso)) {
                        log.warn("dbms event is skipped , with position {}.", dbmsEvent.getPosition());
                        return;
                    }

                    if (dbmsEvent instanceof DefaultRowChange) {
                        eventBatch.add(dbmsEvent);
                    } else if (DdlApplyHelper.isDdl(dbmsEvent)) {
                        // first apply all existing events
                        long start = System.currentTimeMillis();
                        StatisticalProxy.getInstance().apply(eventBatch);
                        takeStatisticsWithFlowControl(eventBatch.size(), sequence, start, 0, false);
                        StatisticalProxy.getInstance().flushPosition();
                        eventBatch.clear();

                        // apply DDL one by one
                        ((DefaultQueryLog) dbmsEvent).setFirstDdl(firstDdl);
                        eventBatch.add(dbmsEvent);
                        endOfBatch = true;
                    }
                }

                position = dbmsEvent.getPosition();
                messageEvent.tryRelease();

                if (endOfBatch) {
                    // before apply
                    long cachedEventSize = msgRingBuffer.getBufferSize() - msgRingBuffer.remainingCapacity();
                    StatMetrics.getInstance().setTotalInCache(cachedEventSize);
                    long start = System.currentTimeMillis();

                    // do apply
                    if (!tryDoApply(sequence)) {
                        return;
                    }

                    // after apply
                    takeStatisticsWithFlowControl(eventBatch.size(), sequence, start, 0, parallelSchemaApplyEnabled);
                    eventBatch.clear();
                }
            } catch (Throwable e) {
                try {
                    processApplyError(e);
                } finally {
                    stop();
                }
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onShutdown() {
            if (parallelSchemaApplier != null) {
                parallelSchemaApplier.stop();
            }
        }

        @SneakyThrows
        private boolean isBufferEmpty(long sequence) {
            long maxLoopCount = 10;
            long count = 0;
            while (sequence == msgRingBuffer.getCursor() && count < maxLoopCount) {
                Thread.sleep(10);
                count++;
            }
            return sequence == msgRingBuffer.getCursor();
        }

        private boolean tryDoApply(long sequence) throws Exception {
            if (parallelSchemaApplyEnabled) {
                int batchSize = DynamicApplicationConfig.getInt(RPL_PARALLEL_SCHEMA_APPLY_BATCH_SIZE);
                if (eventBatch.size() >= batchSize || isBufferEmpty(sequence)) {
                    parallelSchemaApplier.parallelApply(eventBatch);
                    return true;
                }
            } else {
                StatisticalProxy.getInstance().apply(eventBatch);
                return true;
            }

            return false;
        }

        private void processApplyError(Throwable e) {
            try {
                log.error("failed to call applier,", e);
                // 延迟半小时以上才报警
                if (StatisticalProxy.getInstance().computeTaskDelay() >
                    DynamicApplicationConfig.getInt(RPL_DELAY_ALARM_THRESHOLD_SECOND)) {
                    StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                        TaskContext.getInstance().getTaskId(), "apply error");
                }
                StatisticalProxy.getInstance().recordLastError(e.getCause().toString());
                stop();
            } catch (Throwable t) {
                log.error("process apply error failed!", t);
                throw t;
            }
        }
    }

    /**
     * TranRingBufferEventHandler, this will construct Transactions to call APPLIER
     */
    private class TranRingBufferEventHandler implements EventHandler<MessageEvent>, LifecycleAware {

        private final List<Transaction> transactionBatch;
        private final String maxDdlCheckpointTso;
        private int eventCount = 0;

        public TranRingBufferEventHandler(int batchSize) {
            transactionBatch = new ArrayList<>(batchSize / 2);
            maxDdlCheckpointTso = DbTaskMetaManager.getLatestSubmittedTsoByTask(
                TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        }

        @Override
        public void onEvent(MessageEvent messageEvent, long sequence, boolean endOfBatch) {
            try {
                Transaction transaction = getTransactionToApply();
                boolean isDdl = false;
                DBMSEvent dbmsEvent = messageEvent.getDbmsEventEffective();
                if (dbmsEvent instanceof DefaultRowChange) {
                    if (shouldSkip(dbmsEvent, maxDdlCheckpointTso)) {
                        log.warn("dbms event is skipped , with position {}.", dbmsEvent.getPosition());
                        return;
                    }
                    transaction.appendRowChange(dbmsEvent);
                    eventCount++;
                } else if (DdlApplyHelper.isDdl(dbmsEvent)) {
                    if (shouldSkip(dbmsEvent, maxDdlCheckpointTso)) {
                        log.warn("dbms event is skipped , with position {}.", dbmsEvent.getPosition());
                        return;
                    }
                    // first apply all exist events
                    StatisticalProxy.getInstance().tranApply(transactionBatch);
                    transactionBatch.clear();
                    transaction = getTransactionToApply();
                    transaction.appendQueryLog(dbmsEvent);
                    position = dbmsEvent.getPosition();
                    transaction.setFinished(true);
                    isDdl = true;
                    endOfBatch = true;
                } else if (dbmsEvent instanceof DBMSTransactionEnd) {
                    position = dbmsEvent.getPosition();
                    transaction.setFinished(true);
                } else {
                    position = dbmsEvent.getPosition();
                }

                messageEvent.tryRelease();
                if (endOfBatch) {
                    // do NOT apply unfinished transaction
                    Transaction lastTransaction = null;
                    if (!transactionBatch.isEmpty()) {
                        lastTransaction = transactionBatch.get(transactionBatch.size() - 1);
                        if (!lastTransaction.isFinished()) {
                            transactionBatch.remove(transactionBatch.size() - 1);
                        }
                    }
                    log.info("pipeline received events, count: {}, transaction count: {}",
                        eventCount,
                        transactionBatch.size());
                    long start = System.currentTimeMillis();
                    // apply
                    StatisticalProxy.getInstance().tranApply(transactionBatch);
                    takeStatisticsWithFlowControl(eventCount, sequence, start, 0, false);
                    eventCount = 0;
                    // force flush position info if Ddl happened
                    // 位点不能跨ddl
                    if (isDdl) {
                        StatisticalProxy.getInstance().flushPosition();
                    }
                    // remove finished, keep the NOT finished transaction
                    transactionBatch.forEach(Transaction::close);
                    transactionBatch.clear();
                    if (lastTransaction != null && !lastTransaction.isFinished()) {
                        transactionBatch.add(lastTransaction);
                    }
                }
            } catch (Exception e) {
                log.error("failed to call applier,", e);
                if (StatisticalProxy.getInstance().computeTaskDelay() >
                    DynamicApplicationConfig.getInt(RPL_DELAY_ALARM_THRESHOLD_SECOND)) {
                    StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                        TaskContext.getInstance().getTaskId(), "apply error");
                }
                StatisticalProxy.getInstance().recordLastError(e.getCause().toString());
                stop();
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onShutdown() {
        }

        private Transaction getTransactionToApply() {
            if (transactionBatch.isEmpty() || transactionBatch.get(transactionBatch.size() - 1).isFinished()) {
                Transaction newTransaction =
                    new Transaction(RplStorage.getRepoUnit(), pipeLineConfig.getPersistConfig());
                transactionBatch.add(newTransaction);
                return newTransaction;
            }
            return transactionBatch.get(transactionBatch.size() - 1);
        }
    }

    /**
     * TranRingBufferEventHandler, this will construct Transactions to call APPLIER
     */
    private class XaTranRingBufferEventHandler implements EventHandler<MessageEvent>, LifecycleAware {
        private final List<Transaction> transactionBatch;
        private final Map<String, Transaction> transactionMap;
        private final LinkedHashMap<String, String> transactionPositionMap;
        // 即将记录的位点
        String nextRecordPosition = null;
        // 用上一个event的结束位点来近似代表本次event的开始位点
        String lastPosition = null;
        // 本次event的结束位点
        String nowPosition = null;

        public XaTranRingBufferEventHandler(int batchSize) {

            transactionBatch = new ArrayList<>(batchSize / 2);
            transactionMap = new HashMap<>();
            transactionPositionMap = new LinkedHashMap<>();
        }

        @Override
        public void onEvent(MessageEvent messageEvent, long sequence, boolean endOfBatch) throws Exception {
            // lastPosition记录本个event的开始位置
            lastPosition = nowPosition;
            DBMSXATransaction xaTransaction = messageEvent.getXaTransaction();
            DBMSEvent dbmsEvent = messageEvent.getDbmsEventEffective();
            nowPosition = dbmsEvent.getPosition();
            if (dbmsEvent instanceof DefaultRowChange) {
                if (xaTransaction == null) {
                    Transaction transaction = getTransactionToApply();
                    transaction.appendRowChange(dbmsEvent);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(
                            "prepare to get transaction from map for DefaultRowChange, xid {}, now event position {}"
                            , messageEvent.getXaTransaction().getXid(), nowPosition);
                    }
                    transactionMap.get(messageEvent.getXaTransaction().getXid()).appendRowChange(dbmsEvent);
                }
            } else if (dbmsEvent instanceof DBMSTransactionEnd) {
                Transaction transaction = getTransactionToApply();
                transaction.setFinished(true);
                // 没有未结束的xa事务，则位点直接推进为常规事务end position
                if (transactionPositionMap.isEmpty()) {
                    nextRecordPosition = dbmsEvent.getPosition();
                }
            } else if (xaTransaction != null) {
                switch (messageEvent.getXaTransaction().getType()) {
                case XA_START:
                    if (log.isDebugEnabled()) {
                        log.debug("put xid to transaction map, xid " + messageEvent.getXaTransaction());
                    }
                    transactionMap.put(messageEvent.getXaTransaction().getXid(),
                        new Transaction(RplStorage.getRepoUnit(), pipeLineConfig.getPersistConfig()));
                    // 这里需存xa start event的开始位点
                    transactionPositionMap.put(messageEvent.getXaTransaction().getXid(), lastPosition);
                    break;
                case XA_END:
                    if (log.isDebugEnabled()) {
                        log.debug("prepare to get transaction from map for XA_END , xid "
                            + messageEvent.getXaTransaction().getXid());
                    }
                    Transaction transaction = transactionMap.get(messageEvent.getXaTransaction().getXid());
                    transaction.setPrepared(true);
                    break;
                case XA_COMMIT:
                    Transaction thisTransaction = removeFromCache(messageEvent, dbmsEvent);
                    if (thisTransaction != null) {
                        // non-xa transaction's end may be filtered by "filterTransactionEnd"
                        // when receive xa commit, means former non-xa transaction is finished
                        // this may produce at most 1s latency when no xa transaction exists
                        if (!transactionBatch.isEmpty()) {
                            transactionBatch.get(transactionBatch.size() - 1).setFinished(true);
                        }
                        transactionBatch.add(thisTransaction);
                    } else {
                        // 收到了commit，但却没有拿到xa事务start和end之间的event，存在丢数据风险
                        // 触发同步报警
                        String errorInfo = "receive commit but not start and end in xa transaction, xid: " +
                            messageEvent.getXaTransaction().getXid() + "position: " + dbmsEvent.getPosition();
                        log.error(errorInfo);
                    }
                    break;
                case XA_ROLLBACK:
                    // 由于是rollback掉的数据，这里没找到对应的start/end可以忽略
                    removeFromCache(messageEvent, dbmsEvent);
                    break;
                default:
                    break;
                }
            } else if (DdlApplyHelper.isDdl(dbmsEvent)) {
                // 先进行xa transaction判断，再进行ddl判断，因为 xa start 也是 query log event
                log.error("receive ddl event which will not be processed, position: {}， event: {} "
                    , dbmsEvent.getPosition(), dbmsEvent);
            } else {
                // 可能会有其他queryLogEvent，甚至是事务中的event
                // 与非xa处理器不同，因为xa rollback的存在，保守起见，这里不推进位点
                // 无业务数据时，位点的推进依赖rds心跳
            }
            messageEvent.tryRelease();

            if (endOfBatch) {
                try {
                    // do NOT apply unfinished transaction
                    Transaction lastTransaction = null;
                    if (!transactionBatch.isEmpty()) {
                        lastTransaction = transactionBatch.get(transactionBatch.size() - 1);
                        if (!lastTransaction.isFinished()) {
                            transactionBatch.remove(transactionBatch.size() - 1);
                        }
                    }
                    int eventCount = 0;
                    for (Transaction transaction : transactionBatch) {
                        eventCount += (int) transaction.getEventCount();
                    }
                    long start = System.currentTimeMillis();

                    // apply
                    if (applier instanceof TransactionApplier) {
                        StatisticalProxy.getInstance().tranApply(transactionBatch);
                    } else {
                        // 展开transactionBatch
                        List<DBMSEvent> events = new ArrayList<>();
                        for (Transaction transaction : transactionBatch) {
                            Transaction.RangeIterator iterator = transaction.rangeIterator();
                            if (transaction.isPersisted()) {
                                log.info("current transaction is persisted, will apply with stream mode!");
                                applyEvents(events);
                                events.clear();
                                while (iterator.hasNext()) {
                                    Transaction.Range range = iterator.next();
                                    applyEvents(range.getEvents());
                                }
                            } else {
                                while (iterator.hasNext()) {
                                    events.addAll(iterator.next().getEvents());
                                }
                            }
                        }
                        if (!events.isEmpty()) {
                            applyEvents(events);
                        }
                    }

                    // set position and flow control
                    if (nextRecordPosition != null) {
                        position = nextRecordPosition;
                    }
                    takeStatisticsWithFlowControl(eventCount, sequence, start, transactionMap.size(), false);

                    // remove finished, keep the NOT finished transaction
                    transactionBatch.forEach(Transaction::close);
                    transactionBatch.clear();
                    if (lastTransaction != null && !lastTransaction.isFinished()) {
                        transactionBatch.add(lastTransaction);
                    }
                } catch (Exception e) {
                    log.error("failed to call applier, exit", e);
                    // 延迟15分钟以上才报警
                    if (StatisticalProxy.getInstance().computeTaskDelay() >
                        DynamicApplicationConfig.getInt(RPL_DELAY_ALARM_THRESHOLD_SECOND)) {
                        StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                            TaskContext.getInstance().getTaskId(), "apply error");
                    }
                    StatisticalProxy.getInstance().recordLastError(e.getCause().toString());
                    stop();
                }
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onShutdown() {
        }

        private Transaction getTransactionToApply() {
            // 如果最后一个transaction未结束，则将新来的非xa event放入未结束的transaction
            if (transactionBatch.isEmpty() || transactionBatch.get(transactionBatch.size() - 1).isFinished()) {
                Transaction newTransaction =
                    new Transaction(RplStorage.getRepoUnit(), pipeLineConfig.getPersistConfig());
                transactionBatch.add(newTransaction);
                return newTransaction;
            }
            return transactionBatch.get(transactionBatch.size() - 1);
        }

        private void applyEvents(List<DBMSEvent> events) throws Exception {
            if (events.isEmpty()) {
                return;
            }
            StatisticalProxy.getInstance().apply(events);
        }

        public <K, V> Map.Entry<K, V> getHead(LinkedHashMap<K, V> map) {
            return map.entrySet().iterator().next();
        }

        private Transaction removeFromCache(MessageEvent messageEvent, DBMSEvent event) {
            transactionPositionMap.remove(messageEvent.getXaTransaction().getXid());
            if (!transactionPositionMap.isEmpty()) {
                // 如果仍有未commit/rollback的xa事务，从linkedhashmap取最早插入的xa start开始位点，作为本次推进到的位点
                if (getHead(transactionPositionMap).getValue() != null) {
                    nextRecordPosition = getHead(transactionPositionMap).getValue();
                }
            } else {
                // 如果没有未commit/rollback的xa事务，直接推进位点到本event的结束位点
                nextRecordPosition = event.getPosition();
            }
            Transaction returnTran = transactionMap.remove(messageEvent.getXaTransaction().getXid());
            if (returnTran != null) {
                returnTran.setFinished(true);
            }
            return returnTran;
        }
    }

    /**
     * For SQL flash back.
     */
    private class RecoveryEventHandler implements EventHandler<MessageEvent>, LifecycleAware {

        private final List<DBMSEvent> eventBatch;

        public RecoveryEventHandler(int batchSize) {
            eventBatch = new ArrayList<>(batchSize / 2);
        }

        @Override
        public void onEvent(MessageEvent messageEvent, long sequence, boolean endOfBatch) {
            try {
                DBMSEvent dbmsEvent = messageEvent.getDbmsEventEffective();
                if (dbmsEvent instanceof DefaultQueryLog || dbmsEvent instanceof DefaultRowsQueryLog
                    || dbmsEvent instanceof DefaultRowChange) {
                    eventBatch.add(dbmsEvent);
                    position = dbmsEvent.getPosition();
                }
                messageEvent.tryRelease();

                if (endOfBatch) {
                    if (log.isDebugEnabled()) {
                        log.debug("pipeline received events, count: {}", eventBatch.size());
                    }
                    StatisticalProxy.getInstance().apply(eventBatch);
                    StatisticalProxy.getInstance().recordPosition(position);
                    eventBatch.clear();
                }
            } catch (Exception e) {
                log.error("failed to call applier, exit", e);
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.RPL_FLASHBACK_ERROR,
                    TaskContext.getInstance().getTaskId(), e.getMessage());
                StatisticalProxy.getInstance().recordLastError(e.getCause().toString());
                stop();
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onShutdown() {
        }
    }

}
