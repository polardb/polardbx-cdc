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
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.canal.system.InstructionCommand;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteRowEventProcessor implements ILogEventProcessor<WriteRowsLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("searchLogger");

    private final boolean searchCdcStart;
    private final String clusterId;

    public WriteRowEventProcessor(boolean searchCdcStart, String clusterId) {
        this.searchCdcStart = searchCdcStart;
        this.clusterId = clusterId;
    }

    @Override
    public void handle(WriteRowsLogEvent event, ProcessorContext context) {
        if (context.getCurrentTran() == null) {
            return;
        }
        TableMapLogEvent tm = event.getTable();
        if (SystemDB.isInstruction(tm.getDbName(), tm.getTableName())) {
            if (isTerminalCommand(context.getCommandTran())) {
                return;
            }
            InstructionCommand command = SystemDB.parseInstructionCommand(event);
            if (searchCdcStart && !command.isCdcStart()) {
                return;
            }

            context.getCurrentTran().setCommand(command);
            context.setCommandTran(context.getCurrentTran());
            logger.warn("wrap find cdc instruction type : " + command);
            return;
        }

    }

    private boolean isTerminalCommand(TranPosition tranPosition) {
        if (tranPosition == null) {
            return false;
        }
        if (tranPosition.isStorageChangeCmd()) {
            return true;
        }

        return tranPosition.isCdcStartCmd() && CommonUtils.isCdcStartCommandIdMatch(tranPosition.getCommandId());
    }
}
