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
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDownloader;
import com.aliyun.polardbx.binlog.cdc.meta.MetaMonitor;
import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.collect.LogEventCollector;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.BinlogExtractor;
import com.aliyun.polardbx.binlog.extractor.ExtractorBuilder;
import com.aliyun.polardbx.binlog.extractor.MockExtractor;
import com.aliyun.polardbx.binlog.extractor.MultiStreamStartTsoWindow;
import com.aliyun.polardbx.binlog.extractor.RpcExtractor;
import com.aliyun.polardbx.binlog.merge.LogEventMerger;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.metadata.MetaGenerator;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.rpc.TxnMessageProvider;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.StorageFactory;
import com.aliyun.polardbx.binlog.transmit.ChunkMode;
import com.aliyun.polardbx.binlog.transmit.LogEventTransmitter;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.transmit.relay.RelayLogEventTransmitter;
import com.aliyun.polardbx.binlog.util.DNStorageSqlExecutor;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_COLLECT_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

/**
 * Created by ziyang.lb
 * Task Engine: Task模块的内核，负责组装各个组件
 **/
public class TaskEngine implements TxnMessageProvider {
    private static final Logger logger = LoggerFactory.getLogger(TaskEngine.class);

    private final MetaGenerator metaGenerator;
    private final TaskConfigProvider taskConfigProvider;
    private volatile TaskRuntimeConfig taskRuntimeConfig;

    private LogEventMerger merger;
    private Storage storage;
    private Collector collector;
    private Transmitter transmitter;
    private volatile boolean running;

    public TaskEngine(TaskConfigProvider taskConfigProvider, TaskRuntimeConfig taskRuntimeConfig) {
        this.taskConfigProvider = taskConfigProvider;
        this.taskRuntimeConfig = taskRuntimeConfig;
        this.metaGenerator = new MetaGenerator();
    }

    public synchronized void start(String startTSO) {
        if (running) {
            return;
        }
        running = true;

        if (StringUtils.isBlank(startTSO)) {
            // startTSO 为空，且没有写入command直接flush
            if (!metaGenerator.exists()) {
                flushLogs();
            }
            metaGenerator.tryStart();
        }

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

    public void flushLogs() {
        List<String>
            storageList = taskRuntimeConfig.getMergeSourceInfos().stream().map(MergeSourceInfo::getBinlogParameter)
            .map(BinlogParameter::getStorageInstId).collect(
                Collectors.toList());
        for (String s : storageList) {
            DNStorageSqlExecutor storageSqlExecutor = new DNStorageSqlExecutor(s);
            storageSqlExecutor.tryFlushDnBinlog();
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        BinlogDownloader.getInstance().stop();
        MultiStreamStartTsoWindow.getInstance().clear();
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
        try {
            logger.info("renew starting with tso " + startTSO);
            this.stop();
            this.start(startTSO);
            logger.info("renew finished with tso " + startTSO);
        } catch (Throwable t) {
            logger.error("meet fatal error when restart task engine.", t);
            Runtime.getRuntime().halt(1);
        }
    }

    private void build(String startTso) {
        checkValid(startTso);
        this.storage = StorageFactory.getStorage();
        this.transmitter = buildTransmitter(startTso);
        //对startTso进行重写，多流模式下startTso从Transmitter获取
        if (taskRuntimeConfig.getType() == TaskType.Dispatcher && transmitter instanceof RelayLogEventTransmitter) {
            startTso = ((RelayLogEventTransmitter) transmitter).getStartTso();
        }

        this.collector = buildCollector();
        this.merger = buildMerger(startTso);

        AtomicInteger extractorNum = new AtomicInteger();
        final String finalStartTso = startTso;
        String rdsBinlogPath =
            DynamicApplicationConfig.getString(TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR) + File.separator +
                DynamicApplicationConfig.getString(TASK_NAME);

        List<String> sourcesList = new ArrayList<>();
        this.taskRuntimeConfig.getMergeSourceInfos().forEach(i -> {
            MergeSource mergeSource =
                new MergeSource(i.getId(), new ArrayBlockingQueue<>(calcMergeSourceQueueSize()), storage);
            mergeSource.setStartTSO(finalStartTso);

            if (i.getType() == MergeSourceType.BINLOG) {
                BinlogExtractor extractor =
                    ExtractorBuilder.buildExtractor(i.getBinlogParameter(), storage, mergeSource, rdsBinlogPath);
                mergeSource.setExtractor(extractor);
                extractorNum.incrementAndGet();
                sourcesList.add(extractor.getDnHost().getStorageInstId());
            } else if (i.getType() == MergeSourceType.RPC) {
                RpcExtractor extractor = new RpcExtractor(mergeSource, storage);
                extractor.setRpcParameter(i.getRpcParameter());
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
        updateSourcesList(sourcesList);
        BinlogDownloader.getInstance().init(rdsBinlogPath, extractorNum.get());
        MetaMonitor.getInstance().setStorageCount(taskRuntimeConfig.getMergeSourceInfos().size());
    }

    private int calcMergeSourceQueueSize() {
        int defaultSize = DynamicApplicationConfig.getInt(TASK_MERGE_SOURCE_QUEUE_SIZE);
        int maxTotalSize = DynamicApplicationConfig.getInt(TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE);
        int mergeSourceSize = taskRuntimeConfig.getMergeSourceInfos().size();
        double calcSize = maxTotalSize / ((double) mergeSourceSize);
        return Math.min(defaultSize, new Double(calcSize).intValue());
    }

    private void checkValid(String startTso) {
        String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTso);
        //TODO for dispatcher
        if (taskRuntimeConfig.getType() != TaskType.Dispatcher && taskRuntimeConfig.getBinlogTaskConfig() != null) {
            long start = System.currentTimeMillis();
            while (true) {
                BinlogTaskConfig binlogTaskConfig = taskRuntimeConfig.getBinlogTaskConfig();
                ExecutionConfig taskConfig =
                    JSONObject.parseObject(binlogTaskConfig.getConfig(), ExecutionConfig.class);

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

                    taskRuntimeConfig = taskConfigProvider.getTaskRuntimeConfig();
                } else {
                    break;
                }
            }
        }
    }

    private Transmitter buildTransmitter(String startTso) {
        if (taskRuntimeConfig.getType() == TaskType.Dispatcher) {
            return new RelayLogEventTransmitter(storage, taskRuntimeConfig.getBinlogTaskConfig().getVersion(),
                extractRecoverTsoMapFromTaskConfig());
        } else {
            return new LogEventTransmitter(taskRuntimeConfig.getType(),
                DynamicApplicationConfig.getInt(TASK_TRANSMIT_QUEUE_SIZE),
                storage,
                ChunkMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.TASK_TRANSMIT_CHUNK_MODE)),
                DynamicApplicationConfig.getInt(ConfigKeys.TASK_TRANSMIT_CHUNK_ITEM_SIZE),
                DynamicApplicationConfig.getInt(ConfigKeys.TASK_TRANSMIT_MAX_MESSAGE_SIZE),
                DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_TRANSMIT_DRY_RUN),
                startTso);
        }
    }

    private Map<String, String> extractRecoverTsoMapFromTaskConfig() {
        BinlogTaskConfig binlogTaskConfig = taskRuntimeConfig.getBinlogTaskConfig();
        ExecutionConfig taskConfig = JSONObject.parseObject(binlogTaskConfig.getConfig(), ExecutionConfig.class);
        return taskConfig.getRecoverTsoMap();
    }

    private Collector buildCollector() {
        return new LogEventCollector(storage,
            transmitter,
            DynamicApplicationConfig.getInt(TASK_COLLECT_QUEUE_SIZE),
            taskRuntimeConfig.getType(),
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGE_XA_WITHOUT_TSO));
    }

    private LogEventMerger buildMerger(String startTSO) {
        String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTSO);
        LogEventMerger result = new LogEventMerger(taskRuntimeConfig.getType(),
            collector,
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGE_XA_WITHOUT_TSO),
            startTSO,
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_MERGE_DRY_RUN),
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_MERGE_DRY_RUN_MODE),
            storage,
            StringUtils.equals(expectedStorageTso, ORIGIN_TSO) ? null : expectedStorageTso);
        result.addHeartBeatWindowAware(collector);
        result.setForceCompleteHbWindow(taskRuntimeConfig.isForceCompleteHbWindow());
        return result;
    }

    private void updateSourcesList(List<String> sourcesList) {
        BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(
            BinlogTaskInfoMapper.class);
        Optional<BinlogTaskInfo> optionalTaskInfo = taskInfoMapper.selectOne(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID)))
                .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskRuntimeConfig.getName())));
        if (optionalTaskInfo.isPresent()) {
            BinlogTaskInfo taskInfo = optionalTaskInfo.get();
            taskInfo.setSourcesList(JSONObject.toJSONString(sourcesList));
            taskInfoMapper.updateByPrimaryKey(taskInfo);
        } else {
            logger.error("Task not found in db, taskName: {}", taskRuntimeConfig.getName());
        }
    }
}
