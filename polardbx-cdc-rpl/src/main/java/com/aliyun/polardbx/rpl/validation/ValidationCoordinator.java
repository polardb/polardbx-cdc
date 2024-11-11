/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation coordinator
 *
 * @author siyu.yusi
 */
@Slf4j
public class ValidationCoordinator {
    private final DataImportMeta.ValidationMeta meta;

    public ValidationCoordinator(DataImportMeta.ValidationMeta meta) {
        this.meta = meta;
    }

    public void validateTable() {
        try {
            Validator validator = new Validator(meta);
            validator.start();
        } catch (Exception e) {
            log.error("Table validation exception", e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            StatisticalProxy.getInstance().recordLastError(e.toString());
            TaskContext.getInstance().getPipeline().stop();
        }
    }
}
