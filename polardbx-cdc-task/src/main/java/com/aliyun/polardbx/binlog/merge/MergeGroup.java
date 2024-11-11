/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.storage.PersistAllChecker;
import com.aliyun.polardbx.binlog.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_GROUP_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.MERGER_STAGE_LOOP_ERROR;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MergeGroup {
    private static final int MERGE_GROUP_QUEUE_SIZE = DynamicApplicationConfig.getInt(TASK_MERGE_GROUP_QUEUE_SIZE);

    private final String identifier;
    private final Storage storage;
    private final AtomicBoolean running;

    /// variables for none leaf node
    private final Map<String, MergeGroup> mergeGroupMap;
    private final MergeController mergeController;
    private final ArrayBlockingQueue<MergeItem> queue;
    private final PersistAllChecker persistAllChecker;
    private ExecutorService executorService;

    /// variables for leaf node
    private final MergeSource directMergeSource;

    MergeGroup(String identifier, Storage storage) {
        this(identifier, storage, null, new HashMap<>(), new MergeController(),
            new ArrayBlockingQueue<>(MERGE_GROUP_QUEUE_SIZE));
    }

    MergeGroup(String identifier, MergeSource mergeSource, Storage storage) {
        this(identifier, storage, mergeSource, null, null, null);
    }

    private MergeGroup(String identifier, Storage storage, MergeSource mergeSource,
                       Map<String, MergeGroup> mergeGroupMap, MergeController mergeController,
                       ArrayBlockingQueue<MergeItem> queue) {
        this.identifier = identifier;
        this.storage = storage;
        this.directMergeSource = mergeSource;
        this.mergeGroupMap = mergeGroupMap;
        this.mergeController = mergeController;
        this.queue = queue;
        this.persistAllChecker = new PersistAllChecker();
        this.running = new AtomicBoolean(false);
    }

    void addMergeSource(String key, MergeSource value) {
        if (directMergeSource != null) {
            throw new PolardbxException("can`t add merge source to merge group whose direct merge source is not null.");
        }
        this.mergeGroupMap.put(key, new MergeGroup(key, value, storage));
    }

    void addMergeGroup(MergeGroup mergeGroup) {
        if (directMergeSource != null) {
            throw new PolardbxException("can`t add merge group to merge group whose direct merge source is not null.");
        }
        this.mergeGroupMap.put(mergeGroup.identifier, mergeGroup);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            if (directMergeSource == null) {
                this.mergeGroupMap.values().forEach(MergeGroup::start);
                this.executorService =
                    Executors.newSingleThreadExecutor(r -> new Thread(r, "binlog-merger-group-thread-" + identifier));
                this.executorService.execute(() -> {
                    log.info("merge group start {} ...", mergeGroupMap);
                    MergeItem lastMergeItem = null;
                    while (running.get()) {
                        try {
                            tryPersistAllQueued();
                            boolean skip = false;
                            if (lastMergeItem != null) {
                                MergeItem mergeItem = lastMergeItem.getMergeGroup().poll();
                                if (mergeItem == null) {
                                    skip = true;
                                } else {
                                    mergeController.push(mergeItem);
                                }
                            } else {
                                for (Map.Entry<String, MergeGroup> entry : mergeGroupMap.entrySet()) {
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
                                continue;
                            }

                            MergeItem minItem = mergeController.pop();
                            if (minItem == null) {
                                continue;
                            }

                            queue.put(minItem);
                            lastMergeItem = minItem;
                        } catch (InterruptedException e) {
                            log.info("merger group with identifier {} is interrupted, exit merge loop.", identifier);
                            break;
                        } catch (Throwable t) {
                            MonitorManager.getInstance()
                                .triggerAlarm(MERGER_STAGE_LOOP_ERROR, ExceptionUtils.getStackTrace(t));
                            log.error("fatal error in merge group loop with identifier {}, the merger thread will exit",
                                identifier, t);
                            throw t;
                        }
                    }
                });
            } else {
                directMergeSource.start();
            }
            log.info("merge group with identifier {} started.", identifier);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (directMergeSource != null) {
                directMergeSource.stop();
            } else {
                mergeGroupMap.values().forEach(MergeGroup::stop);
                if (this.executorService != null) {
                    this.executorService.shutdownNow();
                }
            }
            log.info("merge group with identifier {} stopped.", identifier);
        }
    }

    public MergeItem poll() throws InterruptedException {
        MergeItem mergeItem;
        if (directMergeSource != null) {
            mergeItem = this.directMergeSource.poll();
        } else {
            mergeItem = this.queue.poll(1, TimeUnit.MILLISECONDS);
        }

        if (mergeItem != null) {
            mergeItem = mergeItem.copy();
            mergeItem.setMergeGroupId(identifier);
            mergeItem.setMergeGroup(this);
        }
        return mergeItem;
    }

    public int tryPersist() {
        if (this.directMergeSource != null) {
            return directMergeSource.tryPersist();
        } else {
            return PersistUtil.persist(queue, storage);
        }
    }

    private void tryPersistAllQueued() {
        try {
            persistAllChecker.checkWithCallback(false, () -> {
                AtomicInteger persistedTokenCount = new AtomicInteger(0);
                mergeGroupMap.values().forEach(v -> persistedTokenCount.getAndAdd(v.tryPersist()));
                return "persist count in log-event-merger at this round is " + persistedTokenCount.get();
            });
        } catch (Throwable t) {
            log.error("try persist all queued txn buffer failed.", t);
        }
    }

    @Override
    public String toString() {
        return "MergeGroup{" +
            "identifier='" + identifier + '\'' +
            ", mergeGroupMap=" + mergeGroupMap +
            ", directMergeSource=" + directMergeSource +
            '}';
    }
}
