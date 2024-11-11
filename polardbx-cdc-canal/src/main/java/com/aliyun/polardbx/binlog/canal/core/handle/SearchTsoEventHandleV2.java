/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.GcnEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.QueryLogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.SequenceEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.WriteRowEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.XACommitEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.XAPrepareEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.XARollbackEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.processor.XAStartEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 位点的搜索要求：
 * 1、 如果是search command， 则返回的 位点 tso必须是command的tso， position必须是command 空洞排序的开始位点
 * 2、 如果是search tso , 返回的位点 tso 最小 且 position 最小 ，同时 tso 必须大于等于 command 的tso。
 * <p>
 * 消费端 会使用tso rollback 或者 apply tableMeta信息，
 */
public class SearchTsoEventHandleV2 implements ISearchTsoEventHandle {
    private static final Logger logger = LoggerFactory.getLogger("searchLogger");

    private final Map<Integer, ILogEventProcessor> processorMap = new HashMap<>();
    private final long searchTSO;
    private final String clusterId;
    private ProcessorContext context;
    private BinlogPosition endPosition;
    private long totalSize;
    private boolean test = false;
    private boolean quickMode = false;
    //no need reset variables
    private long lastPrintTimestamp = System.currentTimeMillis();
    private long startTSO = -1;
    private long endTSO = -1;
    private long startCmdTSO;

    public SearchTsoEventHandleV2(AuthenticationInfo authenticationInfo, long searchTSO, long startCmdTSO,
                                  boolean quickMode, String clusterId) {
        this.searchTSO = searchTSO;
        this.startCmdTSO = startCmdTSO;
        this.quickMode = quickMode;
        this.context = new ProcessorContext(authenticationInfo, searchTSO, startCmdTSO);
        this.context.setInQuickMode(quickMode);
        this.clusterId = clusterId;
        logger.info("search position in quickMode mode : " + quickMode);
    }

    @Override
    public boolean interrupt() {
        return context.isInterrupt();
    }

    @Override
    public Set<Integer> interestEvents() {
        Set<Integer> flagSet = new HashSet<>();
        flagSet.add(LogEvent.START_EVENT_V3);
        flagSet.add(LogEvent.QUERY_EVENT);
        flagSet.add(LogEvent.WRITE_ROWS_EVENT_V1);
        flagSet.add(LogEvent.WRITE_ROWS_EVENT);
        flagSet.add(LogEvent.ROTATE_EVENT);
        flagSet.add(LogEvent.SEQUENCE_EVENT);
        flagSet.add(LogEvent.GCN_EVENT);
        flagSet.add(LogEvent.TABLE_MAP_EVENT);
        flagSet.add(LogEvent.XA_PREPARE_LOG_EVENT);
        flagSet.add(LogEvent.XID_EVENT);
        flagSet.add(LogEvent.FORMAT_DESCRIPTION_EVENT);
        return flagSet;
    }

    @Override
    public void onStart() {
        this.initProcessor();
        this.startTSO = -1;
        this.endTSO = -1;
    }

    private void initProcessor() {
        // 已经找到位点，还来搜索，说明有大事务只搜索到了complete，那么只搜索start事务就好
        if (this.context.isFind()) {
            logger.warn("already find pos , but may be has big tran with different files!");
            this.processorMap.put(LogEvent.QUERY_EVENT,
                new QueryLogEventProcessor(searchTSO, new XAStartEventProcessor(), null,
                    null));
        } else {
            this.processorMap.put(LogEvent.QUERY_EVENT,
                new QueryLogEventProcessor(searchTSO, new XAStartEventProcessor(),
                    new XACommitEventProcessor(searchTSO, startCmdTSO),
                    new XARollbackEventProcessor()));
            this.processorMap.put(LogEvent.XID_EVENT, new XACommitEventProcessor(searchTSO, startCmdTSO));
            this.processorMap.put(LogEvent.XA_PREPARE_LOG_EVENT, new XAPrepareEventProcessor());
            this.processorMap.put(LogEvent.WRITE_ROWS_EVENT, new WriteRowEventProcessor(searchTSO == -1, clusterId));
            this.processorMap.put(LogEvent.WRITE_ROWS_EVENT_V1, new WriteRowEventProcessor(searchTSO == -1, clusterId));
            this.processorMap.put(LogEvent.SEQUENCE_EVENT, new SequenceEventProcessor());
            this.processorMap.put(LogEvent.GCN_EVENT, new GcnEventProcessor());
        }
    }

    public long getStartTSO() {
        return startTSO;
    }

    public long getEndTSO() {
        return endTSO;
    }

    @Override
    public void onEnd() {
        this.context.onFileComplete();
    }

    @Override
    public void setEndPosition(BinlogPosition endPosition) {
        this.endPosition = endPosition;
        this.totalSize = endPosition.getPosition();
        this.context.setCurrentFile(endPosition.getFileName());
    }

    @Override
    public void handle(LogEvent event, LogPosition logPosition) {
        if (endOfFile(logPosition)) {
            // 如果当前文件找到了pos， 则遇到文件尾，可以退出
            if (context.isPreSelected()) {
                context.setFind(null);
            }
            logger.info(
                " finish search binlog : " + context.getCurrentFile() + " result : " + context.printDetail());
            context.setInterrupt(true);
            return;
        }
        context.setLogPosition(logPosition);

        if (context.isFind()) {
            if (context.getPosition() != null) {
                context.setInterrupt(true);
                return;
            }
        }

        ILogEventProcessor processor = processorMap.get(event.getHeader().getType());
        if (processor == null) {
            return;
        }
        processor.handle(event, context);

        // 当找到合适位点时，只关心事务相关event，加快搜索速度
        if (context.isFind() && processorMap.size() > 5) {

            // 找到位点了，清理processor
            processorMap.clear();
            processorMap.put(LogEvent.QUERY_EVENT,
                new QueryLogEventProcessor(searchTSO, new XAStartEventProcessor(),
                    new XACommitEventProcessor(searchTSO, startCmdTSO),
                    new XARollbackEventProcessor()));
            this.processorMap.put(LogEvent.XID_EVENT, new XACommitEventProcessor(searchTSO, startCmdTSO));
            this.processorMap.put(LogEvent.XA_PREPARE_LOG_EVENT, new XAPrepareEventProcessor());
            this.processorMap.put(LogEvent.SEQUENCE_EVENT, new SequenceEventProcessor());
            this.processorMap.put(LogEvent.GCN_EVENT, new GcnEventProcessor());
        }
        if (this.startTSO == -1 && context.getLastTSO() != null) {
            this.startTSO = context.getLastTSO();
        }
        if (context.getLastTSO() != null) {
            this.endTSO = context.getLastTSO();
        }

        printProgress(logPosition);

    }

    private boolean endOfFile(LogPosition position) {
        return !StringUtils.equals(endPosition.getFileName(), position.getFileName())
            || position.getPosition() >= endPosition.getPosition();
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    private void printProgress(LogPosition logPosition) {
        long logPos = logPosition.getPosition();
        String fileName = logPosition.getFileName();
        long now = System.currentTimeMillis();
        if (now - lastPrintTimestamp > 5000L) {
            logger.info(" search pos progress : " + (logPos * 100 / totalSize) + "% : " + fileName);
            lastPrintTimestamp = now;
        }
    }

    @Override
    public BinlogPosition getCommandPosition() {
        return context.getCommandTran() != null ? context.getPosition() : null;
    }

    @Override
    public String region() {
        return "[" + startTSO + " , " + endTSO + "]";
    }

    @Override
    public BinlogPosition searchResult() {
        return context.getPosition();
    }

    @Override
    public String getTopologyContext() {
        TranPosition commandTran = context.getCommandTran();
        if (commandTran == null) {
            return null;
        }
        if (commandTran.isCdcStartCmd()) {
            return commandTran.getContent();
        }
        return null;
    }

    @Override
    public String getCommandId() {
        TranPosition commandTran = context.getCommandTran();
        if (commandTran == null) {
            return null;
        }
        if (commandTran.isCdcStartCmd()) {
            return commandTran.getCommandId();
        }
        return null;
    }

    @Override
    public String getLastSearchFile() {
        return context.getCurrentFile();
    }
}
