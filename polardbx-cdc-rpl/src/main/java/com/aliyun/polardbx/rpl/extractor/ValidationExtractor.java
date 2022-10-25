/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.count;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.or;
import static org.mybatis.dynamic.sql.SqlBuilder.select;

import com.aliyun.polardbx.binlog.dao.ValidationTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.filter.DataImportFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.ValidationExtractorConfig;
import com.aliyun.polardbx.rpl.validation.ValidationContext;
import com.aliyun.polardbx.rpl.validation.ValidationCoordinator;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.render.RenderingStrategy;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Validation main class
 *
 * @author siyu.yusi
 */
@Slf4j
public class ValidationExtractor extends BaseExtractor {
    private Map<String, DataSource> dataSourceMap;
    private ValidationExtractorConfig extractorConfig;
    private ExecutorService executorService;
    private List<Future> runningProcessors;
    private HostInfo srcHost;
    private HostInfo dstHost;
    private String extractorName;
    private DataImportFilter filter;
    private List<ValidationContext> contextList;

    public ValidationExtractor(ValidationExtractorConfig extractorConfig, HostInfo srcHost, HostInfo dstHost, DataImportFilter filter) {
        super(extractorConfig);
        this.extractorName = "ValidationExtractor";
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
        contextList = ValidationContext.getFactory().createCtxList(srcHost, dstHost);
        log.info("Validation context list constructed. size: {}", contextList.size());
        return true;
    }

    @Override
    public void start() throws Exception {
        log.info("Starting extractor {}, src physical db number: {}", extractorName, contextList.size());
        for (ValidationContext context : contextList) {
            ValidationCoordinator coordinator = new ValidationCoordinator(context);
            Future future = executorService.submit(() -> coordinator.validateTable());
            runningProcessors.add(future);
            log.info("Running validation for src DB {} and dst DB {}", context.getSrcPhyDB(), context.getDstLogicalDB());
        }
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future future : runningProcessors) {
            allDone &= future.isDone();
        }

        return allDone && isVTaskFinished();
    }

    private boolean isVTaskFinished() {
        String smid = Long.toString(TaskContext.getInstance().getStateMachineId());
        String sid = Long.toString(TaskContext.getInstance().getServiceId());
        String tid = Long.toString(TaskContext.getInstance().getTaskId());
        ValidationTaskMapper mapper = SpringContextHolder.getObject(ValidationTaskMapper.class);
        long allCount = mapper.count(s -> s
            .where(stateMachineId, isEqualTo(smid))
            .and(serviceId, isEqualTo(sid))
            .and(taskId, isEqualTo(tid))
            .and(type, isEqualTo(getType().getValue()))
            .and(deleted, isEqualTo(false)));

        SelectStatementProvider selectStmt = select(count()).from(validationTask).where(stateMachineId, isEqualTo(smid))
                .and(serviceId, isEqualTo(sid))
                .and(taskId, isEqualTo(tid))
                .and(type, isEqualTo(getType().getValue()))
                .and(deleted, isEqualTo(false))
                .and(state, isEqualTo(ValidationStateEnum.DONE.getValue())).build()
            .render(RenderingStrategy.MYBATIS3);

        long doneCount = mapper.count(selectStmt);

        long remaining = allCount - doneCount;

        log.info("Remaining validation task number: {}", remaining);

        return remaining == 0L;
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
