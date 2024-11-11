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
import com.aliyun.polardbx.rpl.validation.reconciliation.Repairer;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciliation phase entrance
 *
 * @author siyu.yusi
 */
@Slf4j
public class RepairCoordinator {

    private final DataImportMeta.ValidationMeta meta;

    public RepairCoordinator(DataImportMeta.ValidationMeta meta) {
        this.meta = meta;
    }

    public void doRepair() {
        try {
            Repairer repairer = new Repairer(meta);
            repairer.start();
        } catch (Exception e) {
            log.error("Data repair exception", e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            StatisticalProxy.getInstance().recordLastError(e.toString());
            TaskContext.getInstance().getPipeline().stop();
        }
    }
}
