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
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation coordinator
 *
 * @author siyu.yusi
 */
@Slf4j
public class ValidationCoordinator {
    private ValidationContext ctx;

    public ValidationCoordinator(ValidationContext context) {
        this.ctx = context;
    }

    public void validateTable() {
        try {
            // 0x00 Split table into chunks
            log.info("Start validating tables. Src phy db: {}, Src table list size: {}", ctx.getSrcPhyDB(), ctx.getSrcPhyTableList().size());
            // 0x01 validating table
            Validator validator = new TableValidator(ctx);
            validator.findDiffRows();

            // 0x03 update task status
            log.info("Finished table validation for src db: {}, smid: {}, sid: {}, tid: {}",
                ctx.getSrcPhyDB(), ctx.getStateMachineId(), ctx.getServiceId(), ctx.getTaskId());
        } catch (Exception e) {
            log.error("Table validation exception", e);
            MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR, ctx.getTaskId(),
                e.getMessage());
            Runtime.getRuntime().halt(1);
        }
    }
}
