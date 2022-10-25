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

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XACommitEventProcessor implements ILogEventProcessor {

    private static final Logger log = LoggerFactory.getLogger("searchLogger");

    private long searchTSO;
    private long startCmdTSO;

    public XACommitEventProcessor(long searchTSO, long startCmdTSO) {
        this.searchTSO = searchTSO;
        this.startCmdTSO = startCmdTSO;
    }

    /**
     * 停止条件
     * 1、 收到第一个>=searchTSO的event， 取上一个合法位点返回。
     * 2、 收到 storageChangeCommand 且新增加的是当前storage , 直接返回(这个可以用receiveCreateCdcPhyDbEvent就可以返回)。
     * 3、 收到 cdcStartCommand 直接返回
     * 4？、 storageHistory 表中有当前storage记录，且对应的commandId 在command 表中有ADD_STORAGE 指令，则查找到的第一个>=searchTSO直接返回。
     **/
    private void afterCommit(TranPosition tranPosition, ProcessorContext context) {

        Long curTso = tranPosition.getTso();
        if (searchTSO != -1 && curTso != null) {
            // 如果tso比最小的baseTSO还小，不能作为位点
            if (startCmdTSO != -1 && curTso < startCmdTSO) {
                return;
            }
            // 场景1
            if (curTso < searchTSO) {
                context.setPreSelected();
                if (context.isInQuickMode()) {
                    context.setFind();
                    //快速模式，如果当前TSo > searchTSO，直接返回.
                    context.setInterrupt(true);
                }
            } else if (context.isPreSelected()) {
                context.setFind();
                if (context.isInQuickMode()) {
                    //快速模式，如果当前TSo > searchTSO，直接返回.
                    context.setInterrupt(true);
                }
            } else if (curTso == searchTSO) {
                //相等，如果没有谁知位点，则可直接设置位点
                if (!context.isFind()) {
                    context.setFind();
                }
                if (context.isInQuickMode()) {
                    //快速模式，如果当前TSo > searchTSO，直接返回.
                    context.setInterrupt(true);
                }
            } else if (curTso > searchTSO) {
                //收到 cdc DDL 还没有找到command 或者tso ，可能是情况4， 直接返回。
                if (context.isReceivedCreateCdcPhyDbEvent()) {
                    context.setFind();
                    context.onFileComplete();
                    log.info("find receive crate cdc db position, start pos @ " + context.getPosition());
                    context.setInterrupt(true);
                }
                if (context.isInQuickMode()) {
                    //快速模式，如果当前TSo > searchTSO，直接返回.
                    context.setInterrupt(true);
                }
            }
        } else {
            // 当没有searchTSO ， 说明初始化状态，搜索到start cmd直接停止。
            TranPosition commandTran = context.getCommandTran();
            if (commandTran != null && commandTran.getEnd() != null && commandTran.isCdcStartCmd()) {
                long commitTSO = commandTran.getTso();
                context.setFind();
                //可以直接退出了
                context.onFileComplete();
                // 比command tso 还小的commit ，就不记录了.
                context.getCommitXidMap().entrySet().removeIf(entry -> entry.getValue() < commitTSO);
                log.info("find cdc start pos @ " + context.getPosition());
                context.setInterrupt(true);
            }
        }
    }

    private void processCommit(LogEvent event, String xid, ProcessorContext context) {
        if (LogEventUtil.containsCommitGCN(event)) {
            context.setLastTSO(((QueryLogEvent) event).getCommitGCN());
        }
        // 找到了，也要搜索完整个文件
        if (context.isFind()) {
            if (!context.getStartXidSet().remove(xid) && !context.getUnCompleteTranPos().remove(xid)) {
                tryRecordLonglyCommit(xid, context);
            }
            return;
        }
        TranPosition tranPosition = context.getUnCompleteTranPos().get(xid);
        if (tranPosition == null) {
            // 这里特殊处理一下，如果没有收到start,说明跨文件了，要记录一下当前commit event
            tryRecordLonglyCommit(xid, context);
            return;
        }

        tranPosition.setTso(context.getLastTSO());
        afterCommit(tranPosition, context);
        tranPosition.setEnd(buildPosition(event, context));
    }

    private void tryRecordLonglyCommit(String xid, ProcessorContext context) {
        if (context.getLastTSO() != null && context.getLastTSO() > searchTSO) {
            // 只记录大于 searchTSO的 commit
            if (!context.isInQuickMode()) {
                context.getCommitXidMap().put(xid, context.getLastTSO());
            }
        }
    }

    @Override
    public void handle(LogEvent event, ProcessorContext context) {
        String xid = LogEventUtil.getXid(event);
        if (StringUtils.isNotBlank(xid)) {
            processCommit(event, xid, context);
        } else {
            context.setCurrentTran(null);
        }
//        context.setLastTSO(null);

    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
