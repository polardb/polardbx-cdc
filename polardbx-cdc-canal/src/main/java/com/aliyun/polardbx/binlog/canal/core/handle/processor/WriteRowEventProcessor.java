/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
