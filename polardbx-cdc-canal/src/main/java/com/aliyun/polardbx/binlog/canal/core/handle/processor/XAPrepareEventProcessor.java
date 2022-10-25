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

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

public class XAPrepareEventProcessor implements ILogEventProcessor<XaPrepareLogEvent> {
    @Override
    public void handle(XaPrepareLogEvent event, ProcessorContext context) {
        if (event.isOnePhase()) {
//            context.setLastTSO(null);
            if (context.getCurrentTran() == null) {
                return;
            }
            context.getCurrentTran().setEnd(buildPosition(event, context));
        }
        context.setCurrentTran(null);
    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
