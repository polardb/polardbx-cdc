/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.RelayWriterMetrics;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_LOG_DETAIL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.Constants.MDC_STREAM_SEQ;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildMinRelayKeyStr;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildPrimaryKeyString;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildRelayKey;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildRelayKeyStr;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class ParallelDataWriter {
    private static final Logger TRANSMIT_WRITE_LOGGER = LoggerFactory.getLogger("transmitWriteLogger");
    private static final int WRITER_COUNT = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_PARALLELISM);

    private final AtomicBoolean running;
    private final Writer[] writers = new Writer[WRITER_COUNT];
    private final Consumer<WriteItem> consumer;

    public ParallelDataWriter(Consumer<WriteItem> consumer) {
        this.consumer = consumer;
        this.running = new AtomicBoolean(false);
        for (int i = 0; i < WRITER_COUNT; i++) {
            this.writers[i] = new Writer(i);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            for (int i = 0; i < WRITER_COUNT; i++) {
                this.writers[i].start();
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            for (int i = 0; i < WRITER_COUNT; i++) {
                this.writers[i].stop();
            }
        }
    }

    public void write(WriteItem item) {
        int index = item.getStreamSeq() % WRITER_COUNT;
        try {
            writers[index].put(item);
        } catch (InterruptedException e) {
            throw new PolardbxException("put write item error!", e);
        }
    }

    private class Writer {
        private final ArrayBlockingQueue<WriteItem> queue;
        private final RelayWriterMetrics metrics;
        private final Thread thread;
        private final AtomicLong putCount;
        private final AtomicLong takeCount;

        public Writer(int seq) {
            int queueSize = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_QUEUE_SIZE);
            this.queue = new ArrayBlockingQueue<>(queueSize);
            this.metrics = new RelayWriterMetrics();
            this.putCount = new AtomicLong(0);
            this.takeCount = new AtomicLong(0);
            this.thread = new Thread(() -> {
                while (running.get()) {
                    try {
                        WriteItem writeItem = queue.take();
                        MDC.put(MDC_STREAM_SEQ, writeItem.getStreamSeq() + "");
                        fillKey(writeItem);
                        consumer.accept(writeItem);
                        logAfter(writeItem);
                        takeCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        log.error("relay writer thread {} is interrupted!", Thread.currentThread().getName(), e);
                    } catch (Throwable t) {
                        log.error("something goes wrong with relay writer thread {}",
                            Thread.currentThread().getName(), t);
                    } finally {
                        MDC.remove(MDC_STREAM_SEQ);
                    }
                }
            });

            String threadName = "relay-data-writer-thread-" + seq;
            this.thread.setName(threadName);
            RelayWriterMetrics.register(threadName, metrics);
        }

        public void put(WriteItem item) throws InterruptedException {
            queue.put(item);
            putCount.incrementAndGet();

            metrics.setQueuedSize(putCount.get() - takeCount.get());
            metrics.setThreadId(thread.getName());
            metrics.setPutCount(putCount.get());
            metrics.setTakeCount(takeCount.get());
            metrics.getStreams().add(item.getStreamSeq());
        }

        private void fillKey(WriteItem writeItem) {
            String keyStr;
            if (writeItem.getTxnToken().getType() == TxnType.DML) {
                keyStr = buildRelayKeyStr(writeItem.getTxnToken().getTso(),
                    writeItem.getTraceId(), writeItem.getSubSeq());
            } else {
                keyStr = buildMinRelayKeyStr(writeItem.getTxnToken().getTso());
            }
            writeItem.setKey(buildRelayKey(keyStr));
            writeItem.setKeyStr(keyStr);
        }

        private void logAfter(WriteItem writeItem) {
            boolean logDetailEnable = DynamicApplicationConfig.getBoolean(BINLOGX_TRANSMIT_WRITE_LOG_DETAIL_ENABLED);
            if (logDetailEnable) {
                if (writeItem.getTxnToken().getType() == TxnType.DML) {
                    logWriteDml(writeItem.getStreamSeq(), writeItem.getKeyStr(), writeItem.getItemList());
                } else {
                    logWriteTag(writeItem);
                }
            }
        }

        private void logWriteDml(int streamSeq, String keyStr, List<TxnItem> list) {
            list.forEach(t -> {
                List<ByteString> primaryKeyList = t.getPrimaryKeyList();
                TRANSMIT_WRITE_LOGGER.info("type:dml | stream:{} | schema:{} | table:{} | save key:{} | hash key:{}"
                        + " | pk str:{} | event type:{} | event size:{}", streamSeq, t.getSchema(), t.getTable(),
                    keyStr, t.getHashKey(), buildPrimaryKeyString(primaryKeyList), t.getEventType(),
                    t.getPayload().size());
            });
        }

        private void logWriteTag(WriteItem writeItem) {
            TRANSMIT_WRITE_LOGGER.info("type:broadcast | stream:{} | save key:{}", writeItem.getStreamSeq(),
                writeItem.getKeyStr());
        }

        public void start() {
            this.thread.start();
        }

        public void stop() {
            this.thread.interrupt();
        }
    }
}
