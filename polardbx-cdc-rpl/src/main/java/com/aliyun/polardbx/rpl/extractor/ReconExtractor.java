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

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.ReconExtractorConfig;
import com.aliyun.polardbx.rpl.validation.RepairCoordinator;
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
    private final ExecutorService executorService;
    private final List<Future<?>> runningProcessors;

    public ReconExtractor(ReconExtractorConfig extractorConfig) {
        super(extractorConfig);
        this.extractorConfig = extractorConfig;
        executorService =
            ThreadPoolUtil.createExecutorWithFixedNum(1, "ReconExecutor");
        runningProcessors = new ArrayList<>();
    }

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void start() throws Exception {
        log.info("Starting recon extractor");
        DataImportMeta.ValidationMeta validationMeta =
            JSON.parseObject(extractorConfig.getPrivateMeta(), DataImportMeta.ValidationMeta.class);
        validationMeta.setType(ValidationTypeEnum.FORWARD);

        RepairCoordinator coordinator = new RepairCoordinator(validationMeta);
        Future<?> future = executorService.submit(coordinator::doRepair);
        runningProcessors.add(future);
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future<?> future : runningProcessors) {
            allDone &= future.isDone();
        }
        if (allDone) {
            if (isReconFinished()) {
                return true;
            } else {
                log.error("All futures have been done but some tasks are not finished");
                throw new PolardbxException("All futures have been done but some tasks are not finished");
            }
        }
        return false;
    }

    private boolean isReconFinished() {
        String fsmId = Long.toString(TaskContext.getInstance().getStateMachineId());
        // compute corresponding validation task id
        String taskId = Long.toString(TaskContext.getInstance().getTaskId() - 1);
        ValidationDiffMapper mapper = SpringContextHolder.getObject(ValidationDiffMapper.class);
        long diffCnt = mapper.count(s -> s
            .where(ValidationDiffDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(fsmId))
            .and(ValidationDiffDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
            .and(ValidationDiffDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));

        log.info("Checking recon tasks. Remaining diff cnt: {}", diffCnt);

        return diffCnt == 0;
    }
}
