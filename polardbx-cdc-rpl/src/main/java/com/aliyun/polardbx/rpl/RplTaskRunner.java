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

package com.aliyun.polardbx.rpl;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.AddressUtil;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.applier.BaseApplier;
import com.aliyun.polardbx.rpl.applier.MergeApplier;
import com.aliyun.polardbx.rpl.applier.MergeTransactionApplier;
import com.aliyun.polardbx.rpl.applier.MysqlApplier;
import com.aliyun.polardbx.rpl.applier.SplitApplier;
import com.aliyun.polardbx.rpl.applier.SplitTransactionApplier;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.applier.TransactionApplier;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.extractor.CanalBinlogExtractor;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.filter.ReplicateFilter;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.SerialPipeline;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ApplierType;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorType;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FilterType;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.ReplicateMeta;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author shicai.xsc 2020/12/8 14:11
 * @since 5.0.0.0
 */
@Data
@Slf4j
public class RplTaskRunner {

    private long taskId;
    private RplTask task;
    private BaseExtractor extractor;
    private BasePipeline pipeline;
    private BaseApplier applier;
    private BaseFilter filter;
    private String config;
    private boolean enableStatistic = true;

    public RplTaskRunner(long taskId) {
        this.taskId = taskId;
    }

    public void start() {
        try {
            log.info("RplTaskEngine initializing");
            if (!init()) {
                log.error("RplTaskRunner init failed");
                return;
            }
            log.info("RplTaskEngine initialized");

            // start task
            log.info("RplTaskEngine starting");
            pipeline.start();
            log.info("RplTaskRunner started");

            // wait task done
            while (!pipeline.checkDone()) {
                Thread.sleep(1000);
            }

            FSMMetaManager.setTaskFinish(taskId);
            log.info("RplTaskRunner done");
        } catch (Throwable e) {
            log.error("RplTaskRunner exited", e);
        }
    }

    public void stop() {
        pipeline.stop();
    }

    private boolean init() {
        try {
            task = DbTaskMetaManager.getTask(taskId);
            RplService service = DbTaskMetaManager.getService(task.getServiceId());
            RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(service.getStateMachineId());
            if (task == null || service == null || stateMachine == null) {
                log.error("Has been deleted from db");
                System.exit(1);
            }
            config = stateMachine.getConfig();
            log.info("RplTaskRunner init, task id: {}", taskId);

            // do this before init applier and extractor
            TaskContext context = TaskContext.getInstance();
            context.setService(service);
            context.setTask(task);
            context.setWorker(AddressUtil.getHostAddress().getHostAddress());
            context.setStateMachine(stateMachine);
            context.setConfig(config);
            ExtractorConfig extractorConfig = JSON.parseObject(task.getExtractorConfig(), ExtractorConfig.class);

            log.info("RplTaskRunner prepare filter");
            initFilter();
            log.info("RplTaskRunner prepare extractor");
            initExtractor();
            log.info("RplTaskRunner prepare applier");
            initApplier();
            log.info("RplTaskRunner prepare pipeline");
            initPipeline();
            StatisticalProxy.getInstance().init(pipeline, task.getPosition());
            extractor.setPipeline(pipeline);

            log.info("RplTaskRunner init all");
            return filter.init() && extractor.init() && pipeline.init() && applier.init();
        } catch (Throwable e) {
            log.error("RplTaskRunner init failed", e);
            return false;
        }
    }

    private void initIncExtractor(int extractorType) {
        BinlogPosition binlogPosition = null;
        if (StringUtils.isNotBlank(task.getPosition())) {
            binlogPosition = BinlogPosition.parseFromString(task.getPosition());
        }
        ApplierConfig applierConfig = JSON.parseObject(task.getApplierConfig(), ApplierConfig.class);
        switch (ExtractorType.from(extractorType)) {
        case RPL_INC:
            ExtractorConfig extractorConfig = JSON.parseObject(task.getExtractorConfig(), ExtractorConfig.class);
            extractor = new CanalBinlogExtractor(extractorConfig,
                extractorConfig.getHostInfo(),
                applierConfig.getHostInfo(),
                binlogPosition,
                filter);
            break;
        default:
            break;
        }
        // if applier enabled transaction, the extractor should NOT filter
        // TransactionEnd
        ((CanalBinlogExtractor) extractor)
            .setFilterTransactionEnd(applierConfig.getApplierType() != ApplierType.TRANSACTION.getValue());
    }

    private void initExtractor() {
        ExtractorConfig config = JSON.parseObject(task.getExtractorConfig(), ExtractorConfig.class);
        switch (ExtractorType.from(config.getExtractorType())) {
        case RPL_INC:
            initIncExtractor(config.getExtractorType());
            break;
        default:
            break;
        }

    }

    private void initApplier() {
        ApplierConfig config = JSON.parseObject(task.getApplierConfig(), ApplierConfig.class);
        ExtractorConfig extractorConfig = JSON.parseObject(task.getExtractorConfig(), ExtractorConfig.class);
        HostInfo hostInfo = config.getHostInfo();

        switch (ApplierType.from(config.getApplierType())) {
        case TRANSACTION:
            applier = new TransactionApplier(config, hostInfo);
            break;
        case SERIAL:
            applier = new MysqlApplier(config, hostInfo);
            break;
        case SPLIT:
            applier = new SplitApplier(config, hostInfo);
            break;
        case SPLIT_TRANSACTION:
            applier = new SplitTransactionApplier(config, hostInfo);
            break;
        case MERGE:
            applier = new MergeApplier(config, hostInfo);
            break;
        case MERGE_TRANSACTION:
            applier = new MergeTransactionApplier(config, hostInfo);
            break;
        default:
            break;
        }
        if (applier instanceof MysqlApplier) {
            ((MysqlApplier) applier).setSrcHostInfo(extractorConfig.getHostInfo());
        }
    }

    private void initPipeline() {
        PipelineConfig pipelineConfig = JSON.parseObject(task.getPipelineConfig(), PipelineConfig.class);
        pipeline = new SerialPipeline(pipelineConfig, extractor, applier);
    }

    private void initFilter() {
        ExtractorConfig extractorConfig = JSON.parseObject(task.getExtractorConfig(), ExtractorConfig.class);
        switch (FilterType.from(extractorConfig.getFilterType())) {
        case RPL_FILTER:
            ReplicateMeta replicateMeta = JSON.parseObject(
                extractorConfig.getSourceToTargetConfig(), ReplicateMeta.class);
            filter = new ReplicateFilter(replicateMeta);
            break;
        case NO_FILTER:
            filter = new BaseFilter();
            break;
        default:
            break;
        }
    }
}
