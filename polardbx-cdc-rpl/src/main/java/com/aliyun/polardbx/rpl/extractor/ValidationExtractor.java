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
import com.aliyun.polardbx.binlog.dao.ValidationTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.ValidationExtractorConfig;
import com.aliyun.polardbx.rpl.validation.ValidationCoordinator;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.render.RenderingStrategy;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.deleted;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.serviceId;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.state;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.stateMachineId;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.taskId;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.type;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.validationTask;
import static org.mybatis.dynamic.sql.SqlBuilder.count;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.select;

/**
 * Validation main class
 *
 * @author siyu.yusi
 */
@Slf4j
public class ValidationExtractor extends BaseExtractor {
    private final ExecutorService executorService;
    private final List<Future<?>> runningProcessors;

    public ValidationExtractor(ValidationExtractorConfig extractorConfig) {
        super(extractorConfig);
        this.extractorConfig = extractorConfig;
        executorService = ThreadPoolUtil.createExecutorWithFixedNum(extractorConfig.getParallelCount(), "validation");
        runningProcessors = new ArrayList<>();
    }

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void start() throws Exception {
        log.info("Starting validation extractor");
        DataImportMeta.ValidationMeta validationMeta =
            JSON.parseObject(extractorConfig.getPrivateMeta(), DataImportMeta.ValidationMeta.class);
        validationMeta.setType(ValidationTypeEnum.FORWARD);

        ValidationCoordinator coordinator = new ValidationCoordinator(validationMeta);
        Future<?> future = executorService.submit(coordinator::validateTable);
        runningProcessors.add(future);
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future<?> future : runningProcessors) {
            allDone &= future.isDone();
        }
        if (allDone) {
            if (isTaskFinished()) {
                return true;
            } else {
                log.error("All futures have been done but some tasks are not finished");
                throw new PolardbxException("All futures have been done but some tasks are not finished");
            }
        }
        return false;
    }

    private boolean isTaskFinished() {
        String smid = Long.toString(TaskContext.getInstance().getStateMachineId());
        String sid = Long.toString(TaskContext.getInstance().getServiceId());
        String tid = Long.toString(TaskContext.getInstance().getTaskId());
        ValidationTaskMapper mapper = SpringContextHolder.getObject(ValidationTaskMapper.class);
        long allCount = mapper.count(s -> s
            .where(stateMachineId, isEqualTo(smid))
            .and(serviceId, isEqualTo(sid))
            .and(taskId, isEqualTo(tid))
            .and(type, isEqualTo(getType().name()))
            .and(deleted, isEqualTo(false)));

        SelectStatementProvider selectStmt = select(count()).from(validationTask).where(stateMachineId, isEqualTo(smid))
            .and(serviceId, isEqualTo(sid))
            .and(taskId, isEqualTo(tid))
            .and(type, isEqualTo(getType().name()))
            .and(deleted, isEqualTo(false))
            .and(state, isEqualTo(ValidationStateEnum.DONE.name())).build()
            .render(RenderingStrategy.MYBATIS3);

        long doneCount = mapper.count(selectStmt);

        long remaining = allCount - doneCount;

        log.info("Remaining validation task number: {}", remaining);

        return remaining == 0L;
    }

    private ValidationTypeEnum getType() {
        RplService rplService = TaskContext.getInstance().getService();
        switch (ServiceType.valueOf(rplService.getServiceType())) {
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
