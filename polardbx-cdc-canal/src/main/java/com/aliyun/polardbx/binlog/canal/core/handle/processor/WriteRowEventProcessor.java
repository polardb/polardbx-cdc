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
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.system.InstructionCommand;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteRowEventProcessor implements ILogEventProcessor<WriteRowsLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("searchLogger");

    private boolean searchCdcStart;

    public WriteRowEventProcessor(boolean searchCdcStart) {
        this.searchCdcStart = searchCdcStart;
    }

    @Override
    public void handle(WriteRowsLogEvent event, ProcessorContext context) {
        TableMapLogEvent tm = event.getTable();
        if (SystemDB.isInstruction(tm.getDbName(), tm.getTableName())) {
            if (context.getCommandTran() != null) {
                return;
            }
            InstructionCommand command = SystemDB.getInstance().parseInstructionCommand(event);
            if (searchCdcStart && !command.isCdcStart()) {
                return;
            }
            context.getCurrentTran().setCommand(command);
            context.setCommandTran(context.getCurrentTran());
            logger.warn("find cdc instruction type : " + command);
            return;
        }
        // 忽略掉这块逻辑，优化性能
//        if (SystemDB.isGlobalTxTable(tm.getTableName())) {
//            // 这里编码写死，因为没有字符串解析，所以用不到
//            TxGlobalEvent txGlobalEvent = SystemDB.getInstance().parseTxGlobalEvent(event, "utf8");
//            Long transId = txGlobalEvent.getTxGlobalTid();
//            Long tmpTso = txGlobalEvent.getTxGlobalTso();
//            if (tmpTso != null) {
//                context.setLastTSO(tmpTso);
//            }
//        }

    }
}
