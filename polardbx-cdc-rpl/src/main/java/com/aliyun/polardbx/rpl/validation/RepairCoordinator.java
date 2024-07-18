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
