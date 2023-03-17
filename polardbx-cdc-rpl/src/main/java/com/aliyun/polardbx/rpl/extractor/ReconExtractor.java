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
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.filter.DataImportFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.ReconExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.validation.ReconCoordinator;
import com.aliyun.polardbx.rpl.validation.ValidationContext;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Validation main class
 *
 * @author siyu.yusi
 */
@Slf4j
public class ReconExtractor extends BaseExtractor {
    private ReconExtractorConfig extractorConfig;
    private ExecutorService executorService;
    private List<Future> runningProcessors;
    private HostInfo srcHost;
    private HostInfo dstHost;
    private String extractorName;
    private DataImportFilter filter;
    private List<ValidationContext> contextList;

    public ReconExtractor(ReconExtractorConfig extractorConfig, HostInfo srcHost, HostInfo dstHost, DataImportFilter filter) {
        super(extractorConfig);
        this.extractorName = "ReconExtractor";
        this.extractorConfig = extractorConfig;
        this.srcHost = srcHost;
        this.dstHost = dstHost;
        this.filter = filter;
        executorService = ThreadPoolUtil.createExecutorWithFixedNum(extractorConfig.getParallelCount(),
                                                                    extractorName);
        runningProcessors = new ArrayList<>();
    }

    @Override
    public boolean init() throws Exception {
        log.info("Initializing extractor {}", extractorName);
        contextList = ValidationContext.getFactory().createCtxList(srcHost, dstHost, filter);
        return true;
    }

    @Override
    public void start() throws Exception {
        log.info("Starting {}", extractorName);

        for (ValidationContext context : contextList) {
            ReconCoordinator coordinator = new ReconCoordinator(context);
            Future future = executorService.submit(() -> coordinator.reconFullLoad());
            runningProcessors.add(future);
            log.info("Running recon for src DB {} and dst DB {}", context.getSrcPhyDB(), context.getDstLogicalDB());
        }
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future future : runningProcessors) {
            allDone &= future.isDone();
        }
        if (allDone)  {
            if (isReconFinished()) {
                return true;
            } else {
                log.error("All futures have been done but some tasks are not finished");
                System.exit(-1);
            }
        }
        return false;
    }

    private boolean isReconFinished() {
        String fsmId = Long.toString(TaskContext.getInstance().getStateMachineId());
        // compute corresponding validation task id
        String taskId = Long.toString(TaskContext.getInstance().getTaskId() -
            TaskContext.getInstance().getPhysicalNum());
        ValidationDiffMapper mapper = SpringContextHolder.getObject(ValidationDiffMapper.class);
        long diffCnt = mapper.count(s -> s
            .where(ValidationDiffDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(fsmId))
            .and(ValidationDiffDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
            .and(ValidationDiffDynamicSqlSupport.type, SqlBuilder.isEqualTo(getType().getValue()))
            .and(ValidationDiffDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));

        log.info("Checking recon tasks. Remaining diff cnt: {}", diffCnt);

        return diffCnt == 0;
    }

    private ValidationTypeEnum getType() {
        RplService rplService = TaskContext.getInstance().getService();
        switch (ServiceType.from(rplService.getServiceType())) {
        case FULL_VALIDATION:
        case RECONCILIATION:
            return ValidationTypeEnum.FORWARD;
        case FULL_VALIDATION_CROSSCHECK:
        case RECONCILIATION_CROSSCHECK:
            return ValidationTypeEnum.BACKWARD;
        }
        return ValidationTypeEnum.FORWARD;
    }
}
