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

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XAStartEventProcessor implements ILogEventProcessor<QueryLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("searchLogger");

    @Override
    public void handle(QueryLogEvent event, ProcessorContext context) {
        if (context.getCurrentTran() != null) {
            throw new PolardbxException(
                "new transaction start , but last tran not complete!" + context.getCurrentTran() + " this pos : "
                    + context
                    .getLogPosition());
        }

        String xid = LogEventUtil.getXid(event);

        if (xid == null) {
            if (context.isFind()) {
                return;
            }
            TranPosition tranPosition = new TranPosition();
            tranPosition.setBegin(buildPosition(event, context));
            context.setCurrentTran(tranPosition);
            return;
        }
        Long tso = context.getCommitXidMap().remove(xid);

        if (context.isFind()) {
            // 已经找到位点了，到这里可能只是匹配大事务
            // 如果tso有值，说明肯定是夸文件了，这里匹配下，commit集合空了可以直接返回
            if (tso != null) {
                if (context.getCommitXidMap().isEmpty()) {
                    // 匹配上了，可以退出搜索位点。
                    BinlogPosition beginPos = buildPosition(event, context);
                    beginPos.setTso(tso);
                    try {
                        beginPos.setRtso(CommonUtils.generateTSO(tso, StringUtils
                            .rightPad(
                                LogEventUtil.getTranIdFromXid(xid, context.getAuthenticationInfo().getCharset()) + "",
                                29,
                                "0"), null));
                    } catch (Exception e) {
                        throw new PolardbxException("build begin pos error", e);
                    }
                    context.setFindPos(beginPos);
                    context.setInterrupt(true);
                }
            } else {
                // 标记一下start set
                context.getStartXidSet().add(xid);
            }
            return;
        }

        LogPosition logPosition = context.getLogPosition();
        TranPosition tranPosition = new TranPosition();
        try {
            AuthenticationInfo authenticationInfo = context.getAuthenticationInfo();
            tranPosition.setTransId(LogEventUtil.getTranIdFromXid(xid, authenticationInfo.getCharset()));
        } catch (Exception e) {
            logger.error("process start event failed! pos : " + logPosition.toString(), e);
            throw new PolardbxException(e);
        }
        tranPosition.setXid(xid);
        tranPosition.setBegin(buildPosition(event, context));
        if (context.getLastTSO() != null) {
            tranPosition.setTso(context.getLastTSO());
        }
        context.setCurrentTran(tranPosition);
        context.getUnCompleteTranPos().add(tranPosition);
    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
