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

import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.validation.reconciliation.Migrator;
import com.aliyun.polardbx.rpl.validation.reconciliation.TableMigrator;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciliation phase entrance
 *
 * @author siyu.yusi
 */
@Slf4j
public class ReconCoordinator {
    private ValidationContext ctx;

    public ReconCoordinator(ValidationContext context) {
        this.ctx = context;
    }

    public void reconFullLoad() {
        try {
            log.info("Full load recon phase start...");
            Migrator migrator = new TableMigrator(ctx);
            migrator.reSync();
            log.debug("Full load recon phase finished. State machine id: {}, Src phy db: {}, DST logical db: {}",
                    ctx.getStateMachineId(), ctx.getSrcPhyDB(), ctx.getDstLogicalDB());

        } catch (Exception e) {
            MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR, ctx.getTaskId(), e.getMessage());
            log.error("Full recon phase exception: ", e);
        }
    }
}
