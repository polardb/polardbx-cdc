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

package com.aliyun.polardbx.rpl.pipeline;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.applier.ApplyHelper;
import com.aliyun.polardbx.rpl.applier.BaseApplier;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.applier.Transaction;
import com.aliyun.polardbx.rpl.applier.TransactionApplier;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WorkerPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author shicai.xsc 2020/11/30 14:59
 * @since 5.0.0.0
 */
@Slf4j
public class SerialPipeline extends BasePipeline {

    private RingBuffer<MessageEvent> msgRingBuffer;
    private ExecutorService offerExecutor;
    private BatchEventProcessor<MessageEvent> offerProcessor;
    private MessageEventFactory messageEventFactory;
    private String position;
    private AtomicLong lastStatisticsTime = new AtomicLong(0);
    private AtomicLong periodBatchSize = new AtomicLong(0);
    private AtomicLong periodBatchCount = new AtomicLong(0);
    private AtomicLong periodBatchCost = new AtomicLong(0);
    private final long LOG_STAT_PERIOD_MILLS = 10000;


    public SerialPipeline(PipelineConfig pipeLineConfig, BaseExtractor extractor, BaseApplier applier) {
        this.pipeLineConfig = pipeLineConfig;
        this.extractor = extractor;
        this.applier = applier;
    }

    @Override
    public boolean init() {
        try {
                messageEventFactory = new MessageEventFactory();
                offerExecutor = ThreadPoolUtil.createExecutorWithFixedNum(1, "applier");
                // create ringBuffer and set ringBuffer eventFactory
                msgRingBuffer = RingBuffer
                    .createSingleProducer(messageEventFactory, pipeLineConfig.getBufferSize(),
                        new BlockingWaitStrategy());
                EventHandler eventHandler;
                SequenceBarrier sequenceBarrier = msgRingBuffer.newBarrier();
                if (pipeLineConfig.isSupportXa()) {
                    eventHandler = new XaTranRingBufferEventHandler(pipeLineConfig.getBufferSize());
                } else if (applier instanceof TransactionApplier) {
                    eventHandler = new TranRingBufferEventHandler(pipeLineConfig.getBufferSize());
                } else {
                    eventHandler = new RingBufferEventHandler(pipeLineConfig.getBufferSize());
                }
                offerProcessor = new BatchEventProcessor<>(msgRingBuffer, sequenceBarrier, eventHandler);
                msgRingBuffer.addGatingSequences(offerProcessor.getSequence());
            return true;
        } catch (Throwable e) {
            log.error("SerialPipeline init failed", e);
            return false;
        }
    }

    @Override
    public void start() {
        try {
            // start extractor thread which will call EXTRACTOR to extract events from
            // binlog and write it to ringBuffer
            log.info("extractor starting");
            extractor.start();
            log.info("extractor started");

            // start offerProcessor thread which will call APPLIER to consume events from
            // ringBuffer
            offerExecutor.submit(offerProcessor);
        } catch (Throwable e) {
            log.error("start extractor occur error", e);
            Runtime.getRuntime().halt(1);
        }
    }

    @Override
    public void stop() {
        extractor.stop();
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
                e.setDbmsEvent(event.getDbmsEvent());
                e.setPosition(event.getPosition());
                e.setXaTransaction(event.getXaTransaction());
                e.setSourceTimestamp(event.getSourceTimestamp());
                e.setExtractTimestamp(event.getExtractTimestamp());
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
    public void directApply(List<DBMSEvent> events) {
        if (!StatisticalProxy.getInstance().apply(events)) {
            log.error("failed to call applier, exit");
            Runtime.getRuntime().halt(1);
        }
        StatisticalProxy.getInstance().addMessageCount(events.size());
    }

    private void takeStatisticsWithFlowControl(long currentBatchSize, long sequence, long start) {
        StatisticalProxy.getInstance().recordPosition(position, false);
        if (currentBatchSize != 0) {
            StatisticalProxy.getInstance().addMessageCount(currentBatchSize);
        }
        long now = System.currentTimeMillis();
        long lastStatisticsTimePre = lastStatisticsTime.get();
        periodBatchSize.addAndGet(currentBatchSize);
        periodBatchCost.addAndGet(now - start);
        if (periodBatchCount.incrementAndGet() % 100 == 0
            || (now > lastStatisticsTimePre + LOG_STAT_PERIOD_MILLS
            && lastStatisticsTime.compareAndSet(lastStatisticsTimePre, now))) {
            StatisticalProxy.getInstance().recordPosition(position, true);
            long avgBatchSize = periodBatchSize.get() / periodBatchCount.get();
            long avgCost = periodBatchCost.get() / periodBatchCount.get();
            long queueSize = msgRingBuffer.getCursor() - sequence;
            log.warn(
                "RingBuffer queue size : " + queueSize + ", average batch size : " + avgBatchSize + ", avg cost : "
                    + avgCost + ", current batch size : " + currentBatchSize + ", current cost : " + (now - start));
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

        private List<DBMSEvent> eventBatch;

        public RingBufferEventHandler(int batchSize) {
            eventBatch = new ArrayList<>(batchSize / 2);
        }

        @Override
        public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
            boolean isDdl = false;
            if (event.getDbmsEvent() instanceof DBMSRowChange) {
                eventBatch.add(event.getDbmsEvent());
            } else if (ApplyHelper.isDdl(event.getDbmsEvent())) {
                isDdl = true;
                // first apply all exist events
                StatisticalProxy.getInstance().apply(eventBatch);
                eventBatch.clear();
                // apply DDL one by one
                event.getDbmsEvent().putOption(
                    new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION_STR, event.getPosition()));
                eventBatch.add(event.getDbmsEvent());
                endOfBatch = true;
                position = event.getPosition();
            } else if (event.getDbmsEvent() instanceof DBMSTransactionEnd) {
                position = event.getPosition();
            } else {
                position = event.getPosition();
            }

            if (endOfBatch) {
                try {
                    StatisticalProxy.getInstance()
                        .setTotalInCache(msgRingBuffer.getBufferSize() - msgRingBuffer.remainingCapacity());
                    long start = System.currentTimeMillis();
                    if (!StatisticalProxy.getInstance().apply(eventBatch)) {
                        log.error("failed to call applier, exit");
                        Runtime.getRuntime().halt(1);
                    }
                    takeStatisticsWithFlowControl(eventBatch.size(), sequence, start);
                    // force flush position info if Ddl happened
                    if (isDdl) {
                        StatisticalProxy.getInstance().flushPosition();
                    }
                    eventBatch.clear();
                } catch (Throwable e) {
                    log.error("failed to call applier, exit", e);
                    Runtime.getRuntime().halt(1);
                }
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onShutdown() {
        }
    }

    /**
     * TranRingBufferEventHandler, this will construct Transactions to call APPLIER
     */
    private class TranRingBufferEventHandler implements EventHandler<MessageEvent>, LifecycleAware {

        private int eventCount = 0;
        private List<Transaction> transactionBatch;

        public TranRingBufferEventHandler(int batchSize) {
            transactionBatch = new ArrayList<>(batchSize / 2);
        }

        @Override
        public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
            Transaction transaction = getTransactionToApply();
            boolean isDdl = false;
            if (event.getDbmsEvent() instanceof DBMSRowChange) {
                transaction.appendRowChange(event.getDbmsEvent());
                eventCount++;
            } else if (ApplyHelper.isDdl(event.getDbmsEvent())) {
                // first apply all exist events
                StatisticalProxy.getInstance().tranApply(transactionBatch);
                transactionBatch.clear();
                transaction = getTransactionToApply();
                transaction.appendQueryLog(event.getDbmsEvent());
                position = event.getPosition();
                event.getDbmsEvent().putOption(
                    new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION_STR, event.getPosition()));
                transaction.setFinished(true);
                isDdl = true;
                endOfBatch = true;
            } else if (event.getDbmsEvent() instanceof DBMSTransactionEnd) {
                position = event.getPosition();
                transaction.setFinished(true);
            } else {
                position = event.getPosition();
            }

            if (endOfBatch) {
                try {
                    // do NOT apply unfinished transaction
                    Transaction lastTransaction = null;
                    if (transactionBatch.size() > 0) {
                        lastTransaction = transactionBatch.get(transactionBatch.size() - 1);
                        if (!lastTransaction.isFinished()) {
                            transactionBatch.remove(transactionBatch.size() - 1);
                        }
                    }
                    long start = System.currentTimeMillis();
                    // apply
                    if (!StatisticalProxy.getInstance().tranApply(transactionBatch)) {
                        log.error("failed to call applier, exit");
                        Runtime.getRuntime().halt(1);
                    }
                    takeStatisticsWithFlowControl(eventCount, sequence, start);
                    eventCount = 0;
                    // force flush position info if Ddl happened
                    // 位点不能跨ddl
                    if (isDdl) {
                        StatisticalProxy.getInstance().flushPosition();
                    }
                    // remove finished, keep the NOT finished transaction
                    transactionBatch.clear();
                    if (lastTransaction != null && !lastTransaction.isFinished()) {
                        transactionBatch.add(lastTransaction);
                        log.info("lastTransaction NOT finished, waiting for DBMSTransactionEnd");
                    }
                } catch (Throwable e) {
                    log.error("failed to call applier, exit", e);
                    Runtime.getRuntime().halt(1);
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
            if (transactionBatch.size() == 0 || transactionBatch.get(transactionBatch.size() - 1).isFinished()) {
                Transaction newTransaction = new Transaction();
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

        String nextPosition = null;
        private List<Transaction> transactionBatch;
        private Map<String, Transaction> transactionMap;
        private LinkedHashMap<String, String> transactionPositionMap;

        public XaTranRingBufferEventHandler(int batchSize) {

            transactionBatch = new ArrayList<>(batchSize / 2);
            transactionMap = new HashMap<>();
            transactionPositionMap = new LinkedHashMap<>();
        }

        @Override
        public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
            boolean isDdl = false;
            DBMSXATransaction xaTransaction = event.getXaTransaction();
            if (event.getDbmsEvent() instanceof DBMSRowChange) {
                if (xaTransaction == null) {
                    Transaction transaction = getTransactionToApply();
                    transaction.appendRowChange(event.getDbmsEvent());
                } else {
                    transactionMap.get(event.getXaTransaction().getXid()).appendRowChange(event.getDbmsEvent());
                }
            } else if (ApplyHelper.isDdl(event.getDbmsEvent())) {
                log.error("receive ddl event which will not be processed: {} ", event);
            } else if (event.getDbmsEvent() instanceof DBMSTransactionEnd) {
                Transaction transaction = getTransactionToApply();
                transaction.setFinished(true);
                // 没有未结束的xa事务
                if (transactionPositionMap.size() == 0) {
                    nextPosition = event.getPosition();
                }
            } else if (xaTransaction != null) {
                switch (event.getXaTransaction().getType()) {
                case XA_START:
                    transactionMap.put(event.getXaTransaction().getXid(), new Transaction());
                    transactionPositionMap.put(event.getXaTransaction().getXid(), event.getPosition());
                    break;
                case XA_END:
                    Transaction transaction = transactionMap.get(event.getXaTransaction().getXid());
                    transaction.setPrepared(true);
                    break;
                case XA_COMMIT:
                    Transaction thisTransaction = removeFromCache(event);
                    if (thisTransaction != null) {
                        transactionBatch.add(thisTransaction);
                    }
                    break;
                case XA_ROLLBACK:
                    removeFromCache(event);
                    break;
                default:
                    break;
                }
            } else {
                // nextPosition = event.getPosition();
            }

            if (endOfBatch) {
                try {
                    // do NOT apply unfinished transaction
                    Transaction lastTransaction = null;
                    if (transactionBatch.size() > 0) {
                        lastTransaction = transactionBatch.get(transactionBatch.size() - 1);
                        if (!lastTransaction.isFinished()) {
                            transactionBatch.remove(transactionBatch.size() - 1);
                        }
                    }
                    int eventCount = 0;
                    for (Transaction transaction : transactionBatch) {
                        eventCount += transaction.getEvents().size();
                    }
                    long start = System.currentTimeMillis();
                    // apply
                    if (applier instanceof TransactionApplier) {
                        if (!StatisticalProxy.getInstance().tranApply(transactionBatch)) {
                            log.error("failed to call applier, exit");
                            Runtime.getRuntime().halt(1);
                        }
                    } else {
                        // 展开transactionBatch
                        List<DBMSEvent> events = new ArrayList<>();
                        for (Transaction transaction : transactionBatch) {
                            events.addAll(transaction.getEvents());
                        }
                        if (!StatisticalProxy.getInstance().apply(events)) {
                            log.error("failed to call applier, exit");
                            Runtime.getRuntime().halt(1);
                        }
                    }
                    takeStatisticsWithFlowControl(eventCount, sequence, start);

                    if (nextPosition != null) {
                        position = nextPosition;
                    }
                    // remove finished, keep the NOT finished transaction
                    transactionBatch.clear();
                    if (lastTransaction != null && !lastTransaction.isFinished()) {
                        transactionBatch.add(lastTransaction);
                        log.info("lastTransaction NOT finished, waiting for DBMSTransactionEnd");
                    }
                } catch (Throwable e) {
                    log.error("failed to call applier, exit", e);
                    Runtime.getRuntime().halt(1);
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
            if (transactionBatch.size() == 0 || transactionBatch.get(transactionBatch.size() - 1).isFinished()) {
                Transaction newTransaction = new Transaction();
                transactionBatch.add(newTransaction);
                return newTransaction;
            }
            return transactionBatch.get(transactionBatch.size() - 1);
        }

        public <K, V> Map.Entry<K, V> getHead(LinkedHashMap<K, V> map) {
            return map.entrySet().iterator().next();
        }

        private Transaction removeFromCache(MessageEvent event) {
            transactionPositionMap.remove(event.getXaTransaction().getXid());
            if (transactionPositionMap.size() != 0) {
                nextPosition = getHead(transactionPositionMap).getValue();
            } else {
                nextPosition = event.getPosition();
            }
            Transaction returnTran = transactionMap.remove(event.getXaTransaction().getXid());
            if (returnTran != null) {
                returnTran.setFinished(true);
            }
            return returnTran;
        }

    }

}
