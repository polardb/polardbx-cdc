/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.monitor.MonitorType.MERGER_STAGE_LOOP_ERROR;

@Slf4j
public class DirectLogEventMerger implements Merger {
    private final Collector collector;
    private final MergeBarrier mergeBarrier;
    private final AtomicBoolean running;
    private final AtomicReference<TxnToken> firstToken;
    private final AtomicReference<TxnToken> firstDmlToken;
    private MergeSource mergeSource;
    private ExecutorService executorService;
    private String lastTso;

    public DirectLogEventMerger(Collector collector) {
        this.collector = collector;
        this.running = new AtomicBoolean(false);
        this.firstToken = new AtomicReference<>();
        this.firstDmlToken = new AtomicReference<>();
        this.mergeBarrier = new MergeBarrier(false, collector::push);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            this.executorService = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "direct-merger-thread-" + mergeSource.getSourceId()));
            this.mergeSource.start();
            this.executorService.execute(() -> {
                while (running.get()) {
                    try {
                        MergeItem currMergeItem = mergeSource.poll();
                        if (currMergeItem == null) {
                            continue;
                        }

                        String currTso = currMergeItem.getTxnToken().getTso();
                        if (lastTso != null && currTso.compareTo(lastTso) < 0
                            && currMergeItem.getTxnToken().getType() != TxnType.FORMAT_DESC) {
                            log.error("detected disorderly tso，current tso is {}, last tso is {}", currTso, lastTso);
                            throw new PolardbxException(
                                "detected disorderly tso，current tso is " + currTso + ",last tso is " + lastTso);
                        }

                        if (firstToken.compareAndSet(null, currMergeItem.getTxnToken())) {
                            log.info("the first token in direct merger is :" + currMergeItem.getTxnToken());
                        }

                        if (currMergeItem.getTxnToken().getType() == TxnType.DML
                            && firstDmlToken.compareAndSet(null, currMergeItem.getTxnToken())) {
                            log.info("the first dml token in direct merger is :" + currMergeItem.getTxnToken());
                        }

                        if (log.isDebugEnabled()) {
                            log.debug("received token in direct merger is : " + currMergeItem.getTxnToken().getTso()
                                + " with type : " + currMergeItem.getTxnToken().getType() + " with sourceId : "
                                + currMergeItem.getSourceId());
                        }

                        emit((currMergeItem.getTxnToken()));
                        lastTso = currTso;
                    } catch (InterruptedException e) {
                        log.info("direct log event merger is interrupted, exit merge loop.");
                        break;
                    } catch (Throwable t) {
                        MonitorManager.getInstance()
                            .triggerAlarm(MERGER_STAGE_LOOP_ERROR, ExceptionUtils.getStackTrace(t));
                        log.error("fatal error in direct merger loop, the direct merger thread will exit", t);
                        Runtime.getRuntime().halt(1);
                    }
                }
            });
            log.info("direct log event merger started, with merge source id " + mergeSource.getSourceId());
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            this.mergeSource.stop();
            if (this.executorService != null) {
                try {
                    this.executorService.shutdownNow();
                    this.executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
            log.info("direct log event merger stopped, with merge source id " + mergeSource.getSourceId());
        }
    }

    @Override
    public void addMergeSource(MergeSource mergeSource) {
        this.mergeSource = mergeSource;
    }

    @Override
    public Map<String, MergeSource> getMergeSources() {
        Map<String, MergeSource> map = new HashMap<>();
        map.put(mergeSource.getSourceId(), mergeSource);
        return map;
    }

    @Override
    public void addHeartBeatWindowAware(HeartBeatWindowAware windowAware) {
        //do nothing
    }

    private void emit(TxnToken txnToken) {
        if (txnToken.getType() == TxnType.FORMAT_DESC) {
            collector.push(txnToken);
            return;
        }

        doEmit(txnToken);
    }

    private void doEmit(TxnToken txnToken) {
        if (txnToken.getType() == TxnType.META_HEARTBEAT) {
            mergeBarrier.flush();
            collector.push(txnToken);
        } else {
            mergeBarrier.addTxnToken(txnToken);
        }
    }
}
