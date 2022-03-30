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

package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.MergeMetrics;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorValue;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_NODATA_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_HB_WINDOW_FORCE_COMPLETE_THRESHOLD;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.MERGER_STAGE_EMPTY_LOOP_EXCEED_THRESHOLD;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.MERGER_STAGE_LOOP_ERROR;

/**
 * Created by ziyang.lb
 * Merge模块的核心组件，负责进行全局归并排序
 **/
public class LogEventMerger implements Merger {

    private static final Logger logger = LoggerFactory.getLogger(LogEventMerger.class);
    // 单位，ms
    private static final Long SLEEP_TIME = 10L;

    private final TaskType taskType;
    private final Collector collector;
    private final String startTso;
    private final boolean dryRun;
    private final int dryRunMode;
    private final Storage storage;
    private final Map<String, MergeSource> mergeSources;
    private final MergeController mergeController;
    private final MergeBarrier mergeBarrier;
    private final ExecutorService executorService;
    private final AtomicBoolean aligned;
    private final List<HeartBeatWindowAware> heartBeatWindowAwares;
    private final AtomicReference<TxnToken> firstToken;
    private final AtomicReference<TxnToken> firstDmlToken;

    private MergeItem lastMergeItem;
    private String lastScaleTso;
    private long forceCountAfterLastScale;
    private String lastTso;
    private HeartBeatWindow currentWindow;
    private Long startTime;
    private Long latestPassTime;
    private Long latestPassCount;
    private boolean forceCompleteHbWindow;
    private volatile boolean running;

    public LogEventMerger(TaskType taskType, Collector collector, boolean isMergeNoTsoXa, String startTso,
                          boolean dryRun, int dryRunMode, Storage storage, String lastScaleTso) {
        this.taskType = taskType;
        this.collector = collector;
        this.startTso = startTso;
        this.dryRun = dryRun;
        this.dryRunMode = dryRunMode;
        this.storage = storage;
        this.lastScaleTso = lastScaleTso;

        if (StringUtils.isNotBlank(startTso)) {
            this.aligned = new AtomicBoolean(true);// 如果startTso不为空，则默认为"已对齐"状态
        } else {
            this.aligned = new AtomicBoolean(false);
        }
        this.firstToken = new AtomicReference<>();
        this.firstDmlToken = new AtomicReference<>();
        this.mergeSources = new ConcurrentHashMap<>();
        this.mergeController = new MergeController();
        if (dryRun) {
            this.mergeBarrier = new MergeBarrier(isMergeNoTsoXa, token -> {
            });
        } else {
            this.mergeBarrier = new MergeBarrier(isMergeNoTsoXa, collector::push);
        }
        this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "Binlog-merger-thread"));
        this.heartBeatWindowAwares = new ArrayList<>();

        this.latestPassTime = 0L;
        this.latestPassCount = 0L;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        mergeSources.values().forEach(MergeSource::start);
        MergeMetrics.get().addMergeSources(mergeSources);
        executorService.execute(() -> {
            logger.info("LogEventMerger start {} ...", mergeSources);
            this.startTime = System.currentTimeMillis();
            while (running) {
                try {
                    boolean skip = false;
                    if (mergeSources.isEmpty()) {
                        try {
                            CommonUtils.sleep(SLEEP_TIME);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }

                    if (lastMergeItem != null) {
                        MergeItem mergeItem = lastMergeItem.getMergeSource().poll();
                        if (mergeItem == null) {
                            skip = true;
                        } else {
                            mergeController.push(mergeItem);
                        }
                    } else {
                        for (Map.Entry<String, MergeSource> entry : mergeSources.entrySet()) {
                            if (mergeController.contains(entry.getKey())) {
                                continue;
                            }

                            MergeItem mergeItem = entry.getValue().poll();
                            if (mergeItem == null) {
                                skip = true;
                            } else {
                                mergeController.push(mergeItem);
                            }
                        }
                    }

                    if (skip) {
                        try {
                            checkEmptyLoopThreshold();
                            CommonUtils.sleep(SLEEP_TIME);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }

                    MergeItem minItem = mergeController.pop();
                    if (minItem == null) {
                        checkEmptyLoopThreshold();
                        MergeMetrics.get().incrementMergePollEmptyCount();
                        continue;
                    }

                    // 对TxnType为FORMAT_DESC类型的事务不做顺序验证，直接透传给下游
                    String minTso = minItem.getTxnToken().getTso();
                    if (lastTso != null && minTso.compareTo(lastTso) < 0
                        && minItem.getTxnToken().getType() != TxnType.FORMAT_DESC) {
                        logger.error("detected disorderly tso，current tso is {}, last tso is {}", minTso, lastTso);
                        throw new PolardbxException(
                            "detected disorderly tso，current tso is " + minTso + ",last tso is " + lastTso);
                    }

                    if (firstToken.compareAndSet(null, minItem.getTxnToken())) {
                        logger.info("the first token in merger is :" + minItem.getTxnToken());
                    }

                    if (minItem.getTxnToken().getType() == TxnType.DML
                        && firstDmlToken.compareAndSet(null, minItem.getTxnToken())) {
                        logger.info("the first dml token in merger is :" + minItem.getTxnToken());
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("received token in merger is : " + minItem.getTxnToken().getTso() + " with type : "
                            + minItem.getTxnToken().getType() + " with sourceId : " + minItem.getSourceId());
                    }

                    checkHeartbeatWindow(minItem);
                    doEmit(minItem);
                    lastMergeItem = minItem;
                    lastTso = minTso;
                    doMetricsAfter(minItem.getTxnToken());
                    if (minItem.getTxnToken().getType() == TxnType.META_SCALE) {
                        lastScaleTso = minItem.getTxnToken().getTso();
                        forceCountAfterLastScale = 0;
                        logger.info("reset last scale token to : " + minItem.getTxnToken());
                    }
                } catch (Throwable t) {
                    MonitorManager.getInstance().triggerAlarm(MERGER_STAGE_LOOP_ERROR, ExceptionUtils.getStackTrace(t));
                    logger.error("fatal error in merger loop, the merger thread will exit", t);
                    throw t;
                }
            }
        });
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        mergeSources.forEach((s, mergeSource) -> mergeSource.stop());
        if (executorService != null) {
            try {
                executorService.shutdownNow();
                executorService.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    @Override
    public void addMergeSource(MergeSource mergeSource) {
        if (mergeSources.containsKey(mergeSource.getSourceId())) {
            throw new PolardbxException("Duplicated merge source: " + mergeSource.getSourceId());
        }
        this.mergeSources.put(mergeSource.getSourceId(), mergeSource);
    }

    @Override
    public void addHeartBeatWindowAware(HeartBeatWindowAware windowAware) {
        this.heartBeatWindowAwares.add(windowAware);
    }

    private void checkHeartbeatWindow(MergeItem item) {
        if (item.getTxnToken().getType() == TxnType.FORMAT_DESC) {
            return;
        }

        if (item.getTxnToken().getType() == TxnType.META_HEARTBEAT) {
            // 当前的Window还未达到complete状态，又收到了下一批次的心跳Token，属于异常现象，抛异常处理
            // 未对齐之前不进行验证
            if (currentWindow != null && !currentWindow.isSameWindow(item.getTxnToken()) && aligned.get()) {
                tryForceComplete(item.getTxnToken());
                if (!currentWindow.isComplete()) {
                    throw new PolardbxException(
                        "Received heartbeat token for next window，but current window is not complete yet. The received "
                            + "token is " + item.getTxnToken() + ", current window's tokens are "
                            + currentWindow.getAllTokens());
                }
            }
            if (currentWindow == null || !currentWindow.isSameWindow(item.getTxnToken())) {
                windowRotate(item);
            }
            currentWindow.addHeartbeatToken(item.getSourceId(), item.getTxnToken());
        } else {
            // 当前Window还未达到complete状态，收到了非心跳Token，属于异常现象，抛异常处理
            if (currentWindow != null && aligned.get()) {// 未对齐之前不进行验证
                tryForceComplete(item.getTxnToken());
                if (!currentWindow.isComplete()) {
                    throw new PolardbxException(
                        "Received none heartbeat token, but current window is not ready yet. The received token is "
                            + item.getTxnToken() + ", current window's tokens are "
                            + currentWindow.getAllTokens());
                }
            }
        }
    }

    private void tryForceComplete(TxnToken latestToken) {
        if (!currentWindow.isComplete()) {
            if (forceCompleteHbWindow) {
                currentWindow.forceComplete();
                return;
            }

            if (lastScaleTso != null) {
                // 需要考虑：打标事务和心跳事务并发执行的情况
                // <p>
                // 之前的一个策略是：使用旧拓扑的心跳事务的snapshotSeq一定小于打标事务的commitSeq，所以，如果snapshotSeq < commitSeq则执行
                // forceComplete，但后来发现此命题并不成立，因为事务执行时是先获取拓扑结构，再获取snapshotSeq，那么持有老拓扑的心跳事务和打标
                // 事务在获取snapshotSeq时存在并发关系。如果先获取snapshotSeq，再获取拓扑结构，则上面的命题是成立的
                // <p>
                // 新策略：最优的方案是Server内核在打标的时候，确认持有老拓扑的心跳事务都已经排空，然后再执行打标操作，但分布式场景下不太好做，另外
                // 有很多老版本的Server，需要考略兼容性。考虑到心跳事务和打标事务之间的并发度很低，正常来说打标事务之后只会出现一次基于老拓扑的心跳事务，
                // 除非Daemon发生脑裂，所以暂时采取一种宽松的策略，如果foreComplete的次数没有超过阈值，则也直接进行force
                long forceCompleteThreshold = DynamicApplicationConfig.getLong(TASK_HB_WINDOW_FORCE_COMPLETE_THRESHOLD);
                long interval = getTsoPhysicalTime(latestToken.getTso(), TimeUnit.SECONDS) - getTsoPhysicalTime(
                    lastScaleTso, TimeUnit.SECONDS);

                if (interval <= forceCompleteThreshold) {
                    currentWindow.forceComplete();
                    logger.warn("Force complete heartbeat window, last scale token`s commit tso is {},"
                            + " current heartbeat window`s commit tso is {}, latest txn token`s commit tso is {},"
                            + " forceCountAfterLastScale is {}, interval is {}.", lastScaleTso,
                        currentWindow.getActualTso(), latestToken.getTso(), forceCountAfterLastScale, interval);
                } else {
                    logger.warn("Can`t force complete heartbeat window, last scale token`s commit tso is {},"
                            + " current heartbeat window`s commit tso is {}, latest txn token`s commit tso is {},"
                            + " forceCountAfterLastScale is {}, interval is {}.", lastScaleTso,
                        currentWindow.getActualTso(), latestToken.getTso(), forceCountAfterLastScale, interval);
                }
            }
        }
    }

    private void doEmit(MergeItem minItem) {
        if (minItem.getTxnToken().getType() == TxnType.FORMAT_DESC) {
            collector.push(minItem.getTxnToken());
            return;
        }

        // 如果startTso为空，在各个merge source未对齐之前，不对外发送数据
        if (StringUtils.isBlank(startTso) && !aligned.get()) {
            logger.info("Token is skipped before aligned, token is {}.", minItem.getTxnToken());
            return;
        }

        if (minItem.getTxnToken().getType() == TxnType.META_HEARTBEAT) {
            if (currentWindow.isComplete()) {
                if (taskType == TaskType.Final) {
                    mergeBarrier.flush();
                }
                heartBeatWindowAwares.forEach(h -> h.setCurrentHeartBeatWindow(currentWindow));
                collector.push(minItem.getTxnToken());
            }
        } else {
            if (taskType == TaskType.Relay) {
                collector.push(minItem.getTxnToken());
            } else {
                if (dryRun) {
                    if (dryRunMode == 1) {
                        mergeBarrier.addTxnToken(minItem.getTxnToken());
                    }
                    clearCache(minItem.getTxnToken());
                } else {
                    mergeBarrier.addTxnToken(minItem.getTxnToken());
                }
            }
        }
    }

    public void windowRotate(MergeItem item) {
        if (StringUtils.isBlank(startTso) && currentWindow != null && currentWindow.isComplete()) {
            if (aligned.compareAndSet(false, true)) {
                logger.info("all merge sources have aligned, in the heartbeat window : " + currentWindow);
            }
        }

        if (currentWindow != null && currentWindow.isForceComplete() && lastScaleTso != null) {
            forceCountAfterLastScale++;
        }

        currentWindow = new HeartBeatWindow(item.getTxnToken().getTxnId(),
            CommonUtils.getActualTso(item.getTxnToken().getTso()), item.getTxnToken().getSnapshotSeq(),
            mergeSources.size());
    }

    private void doMetricsAfter(TxnToken token) {
        try {
            if (token != null && token.getType() != TxnType.META_HEARTBEAT) {
                if (token.getXaTxn()) {
                    MergeMetrics.get().incrementMergePass2PCCount();
                } else {
                    MergeMetrics.get().incrementMergePass1PCCount();
                }
            }
            MergeMetrics.get()
                .setDelayTimeOnMerge(System.currentTimeMillis() - getTsoPhysicalTime(lastTso, TimeUnit.MILLISECONDS));
            this.latestPassTime = System.currentTimeMillis();
            this.latestPassCount++;
        } catch (Throwable t) {
            logger.error("do metrics failed.", t);
        }
    }

    private void clearCache(TxnToken token) {
        // 如果allPartiesCount > 0，说明这是一个事务合并后的delegate token，需要把所有分片的缓存都清空
        if (token.getAllPartiesCount() > 0) {
            token.getAllPartiesList().forEach(p -> {
                TxnKey key = new TxnKey(token.getTxnId(), p);
                storage.deleteAsync(key);
                MergeMetrics.get().setStorageCleanerQueuedSize(storage.getCleanerQueuedSize());
            });
        } else {
            TxnKey key = new TxnKey(token.getTxnId(), token.getPartitionId());
            storage.deleteAsync(key);
            MergeMetrics.get().setStorageCleanerQueuedSize(storage.getCleanerQueuedSize());
        }
    }

    private void checkEmptyLoopThreshold() {
        int threshold = DynamicApplicationConfig.getInt(ALARM_NODATA_THRESHOLD);
        if (firstToken.get() != null) {
            long noDataTime = System.currentTimeMillis() - latestPassTime;
            if (noDataTime > threshold * 1000) {
                MonitorManager.getInstance()
                    .triggerAlarm(MERGER_STAGE_EMPTY_LOOP_EXCEED_THRESHOLD, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            }
        } else if (firstToken.get() == null) {
            long noDataTime = System.currentTimeMillis() - startTime;
            if (noDataTime > threshold * 2 * 1000) {
                MonitorManager.getInstance()
                    .triggerAlarm(MERGER_STAGE_EMPTY_LOOP_EXCEED_THRESHOLD, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            }
        }
    }

    public Long getStartTime() {
        return startTime;
    }

    public Long getLatestPassTime() {
        return latestPassTime;
    }

    public Long getLatestPassCount() {
        return latestPassCount;
    }

    public void setForceCompleteHbWindow(boolean forceCompleteHbWindow) {
        this.forceCompleteHbWindow = forceCompleteHbWindow;
        logger.info("set forceCompleteHbWindow`s value to " + forceCompleteHbWindow);
    }
}
