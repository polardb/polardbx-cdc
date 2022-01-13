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

package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.BinlogDownloader;
import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.collect.LogEventCollector;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskInfo;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.BinlogExtractor;
import com.aliyun.polardbx.binlog.extractor.ExtractorBuilder;
import com.aliyun.polardbx.binlog.extractor.MockExtractor;
import com.aliyun.polardbx.binlog.extractor.RpcExtractor;
import com.aliyun.polardbx.binlog.merge.LogEventMerger;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.metadata.MetaGenerator;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.rpc.TxnMessageProvider;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.storage.DeleteMode;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.aliyun.polardbx.binlog.storage.Repository;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.transmit.ChunkMode;
import com.aliyun.polardbx.binlog.transmit.LogEventTransmitter;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_CLEAN_WORKER_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_FORCE_PERSIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_IS_PERSIST_ON;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_DELETE_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_MEMORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_REPO_UNIT_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_TXNITEM_PERSIST_THRESHOLDE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_TXN_PERSIST_THRESHOLD;

/**
 * Created by ziyang.lb
 * Task Engine: Task模块的内核，负责组装各个组件
 **/
public class TaskEngine implements TxnMessageProvider {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger logger = LoggerFactory.getLogger(TaskEngine.class);

    private final MetaGenerator metaGenerator;
    private final TaskInfoProvider taskInfoProvider;
    private volatile TaskInfo taskInfo;

    private LogEventMerger merger;
    private Storage storage;
    private Collector collector;
    private Transmitter transmitter;
    private volatile boolean running;

    public TaskEngine(TaskInfoProvider taskInfoProvider, TaskInfo taskInfo) {
        this.taskInfoProvider = taskInfoProvider;
        this.taskInfo = taskInfo;
        this.metaGenerator = new MetaGenerator();
    }

    public synchronized void start(String startTSO) {
        if (running) {
            return;
        }
        running = true;

        metaGenerator.tryStart();

        //采取一个简单的策略，每次重启的时候，重新初始化所有的组件，这样可以尽可能规避出现脏的数据状态，减少出Bug的概率
        build(startTSO);

        BinlogDownloader.getInstance().start();

        //启动顺序，按照依赖关系编排，最好不可随意更改
        storage.start();
        transmitter.start();
        collector.start();
        merger.start();

        logger.info("task engine started.");
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        BinlogDownloader.getInstance().stop();
        //停止顺序，按照依赖关系编排，最好不可随意更改
        if (merger != null) {
            merger.stop();
        }
        if (collector != null) {
            collector.stop();
        }
        if (transmitter != null) {
            transmitter.stop();
        }
        if (storage != null) {
            storage.stop();
        }
        logger.info("task engine stopped.");
    }

    @Override
    public boolean checkTSO(String startTSO, TxnOutputStream<DumpReply> outputStream,
                            boolean keepWaiting) throws InterruptedException {
        if (!running) {
            return false;
        }
        return transmitter.checkTSO(startTSO, outputStream, keepWaiting);
    }

    @Override
    public void dump(String startTso, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        transmitter.dump(startTso, outputStream);
    }

    @Override
    public synchronized void restart(String startTSO) {
        logger.info("renew starting with tso " + startTSO);
        this.stop();
        this.start(startTSO);
        logger.info("renew finished with tso " + startTSO);
    }

    private void build(String startTSO) {
        checkValid(startTSO);
        this.storage = buildStorage();
        this.transmitter = buildTransmitter();
        this.collector = buildCollector();
        this.merger = buildMerger(startTSO);

        AtomicInteger extractorNum = new AtomicInteger();
        this.taskInfo.getMergeSourceInfos().forEach(i -> {
            MergeSource mergeSource = new MergeSource(i.getId(), new ArrayBlockingQueue<>(i.getQueueSize()), storage);
            mergeSource.setStartTSO(startTSO);

            if (i.getType() == MergeSourceType.BINLOG) {
                BinlogExtractor extractor =
                    ExtractorBuilder.buildExtractor(i.getBinlogParameter(), storage, mergeSource);
                mergeSource.setExtractor(extractor);
                extractorNum.incrementAndGet();
            } else if (i.getType() == MergeSourceType.RPC) {
                RpcExtractor extractor = new RpcExtractor(i.getTaskName(), mergeSource, storage);
                extractor.setRpcParameter(i.getRpcParameter());// 可以为空，测试专用
                mergeSource.setExtractor(extractor);
            } else if (i.getType() == MergeSourceType.MOCK) {
                MockExtractor extractor =
                    new MockExtractor(i.getMockParameter().getTxnType(), i.getMockParameter().getDmlCount(),
                        i.getMockParameter().getEventSiz(), i.getMockParameter().isUseBuffer(),
                        i.getMockParameter().getPartitionId(),
                        mergeSource,
                        storage);
                mergeSource.setExtractor(extractor);
            } else {
                throw new PolardbxException("invalid merge source type :" + i.getType());
            }
            merger.addMergeSource(mergeSource);
        });
    }

    private void checkValid(String startTso) {
        String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTso);
        if (taskInfo.getBinlogTaskConfig() != null) {
            long start = System.currentTimeMillis();
            while (true) {
                BinlogTaskConfig binlogTaskConfig = taskInfo.getBinlogTaskConfig();
                com.aliyun.polardbx.binlog.scheduler.model.TaskConfig taskConfig =
                    GSON.fromJson(binlogTaskConfig.getConfig(),
                        com.aliyun.polardbx.binlog.scheduler.model.TaskConfig.class);

                if (!StringUtils.equals(expectedStorageTso, taskConfig.getTso())) {
                    logger.error("The input tso {} is inconsistent with the expected tso {}, will retry.",
                        taskConfig.getTso(), expectedStorageTso);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }

                    if (System.currentTimeMillis() - start > 5 * 1000) {
                        throw new PolardbxException(
                            "The input tso " + taskConfig.getTso() + " is inconsistent with the expected tso "
                                + expectedStorageTso);
                    }

                    taskInfo = taskInfoProvider.get();
                } else {
                    break;
                }
            }
        }
    }

    private Storage buildStorage() {
        return new LogEventStorage(new Repository(DynamicApplicationConfig.getBoolean(STORAGE_IS_PERSIST_ON),
            DynamicApplicationConfig.getString(STORAGE_PERSIST_PATH) + "/" + DynamicApplicationConfig
                .getString(ConfigKeys.TASK_NAME),
            DynamicApplicationConfig.getBoolean(STORAGE_FORCE_PERSIST),
            DynamicApplicationConfig.getDouble(STORAGE_PERSIST_MEMORY_THRESHOLD),
            DynamicApplicationConfig.getInt(STORAGE_TXN_PERSIST_THRESHOLD),
            DynamicApplicationConfig.getInt(STORAGE_TXNITEM_PERSIST_THRESHOLDE),
            DeleteMode.valueOf(DynamicApplicationConfig.getString(STORAGE_PERSIST_DELETE_MODE)),
            DynamicApplicationConfig.getInt(STORAGE_PERSIST_REPO_UNIT_COUNT)),
            DynamicApplicationConfig.getInt(STORAGE_CLEAN_WORKER_COUNT));
    }

    private Transmitter buildTransmitter() {
        return new LogEventTransmitter(taskInfo.getType(),
            8192 * 2,
            storage,
            ChunkMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.TASK_TRANSMITTER_CHUNK_MODE)),
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_TRANSMITTER_CHUNK_ITEMSIZE),
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_TRANSMITTER_MAX_MESSAGE_SIZE),
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_TRANSMITTER_DRYRUN));
    }

    private Collector buildCollector() {
        return new LogEventCollector(storage,
            transmitter,
            8192,
            taskInfo.getType(),
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGER_MERGE_NOTSO_XA));
    }

    private LogEventMerger buildMerger(String startTSO) {
        LogEventMerger result = new LogEventMerger(taskInfo.getType(),
            collector,
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGER_MERGE_NOTSO_XA),
            startTSO,
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGER_DRYRUN),
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_MERGER_DRYRUN_MODE),
            storage);
        result.addHeartBeatWindowAware(collector);
        result.setForceCompleteHbWindow(taskInfo.isForceCompleteHbWindow());
        return result;
    }
}
