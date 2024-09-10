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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.TransactionStorage;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.processor.EventFilter;
import com.aliyun.polardbx.binlog.extractor.log.processor.FilterBlacklistTableFilter;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chengjin.lyf on 2020/7/15 7:24 下午
 * @since 1.0.25
 */
@Slf4j
public class TransactionBufferEventFilter implements LogEventFilter<LogEvent> {
    private final List<EventFilter> eventFilterList = new ArrayList<>();
    private TransactionStorage transactionStorage;
    private Transaction currentTran;
    private boolean receiveFormatDesc = false;
    /**
     * 1> DN 5.7，通过Sequence Event记录Snapshot Tso和Commit Tso，有两种SequenceType
     * 2> DN 8.0的V1版本，通过XA End Event记录Snapshot Tso，通过XA Commit Event记录Commit Tso
     * 3> DN 8.0的V2版本，通过Gcn Event记录Snapshot Tso和Commit Tso，但所有类型的事务都有GcnEvent，需要靠flag来辨别是否是TSO事务
     * 对于这两种方式，它们的Snapshot Tso都是记录到XA Start之后的，所以处理模式类似
     */
    private long lastCommitSequenceNum = -1L;
    private long lastSyncPointSequenceNum = -1L;

    private final boolean checkSyncPoint =
        DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_CHECK_SYNC_POINT_ENABLED);

    public TransactionBufferEventFilter() {
        String blacklist = DynamicApplicationConfig.getString(ConfigKeys.TASK_EXTRACT_FILTER_PHYSICAL_TABLE_BLACKLIST);
        if (StringUtils.isNotBlank(blacklist)) {
            eventFilterList.add(new FilterBlacklistTableFilter(blacklist));
        }
    }

    @Override
    public void handle(LogEvent handlerEvent, HandlerContext context) throws Exception {
        processLogEvent(handlerEvent, context);
    }

    private void processLogEvent(LogEvent event, HandlerContext context) throws Exception {
        context.getRuntimeContext().setLogPos(event.getLogPos());

        if (LogEventUtil.isStart(event)) {
            processStart(event, context);
        } else if (LogEventUtil.isEnd(event)) {
            // do nothing
        } else if (LogEventUtil.isCommit(event)) {
            processCommit(event, context);
            tryDoNext(context);
        } else if (LogEventUtil.isRollback(event)) {
            processRollback(event, context);
            tryDoNext(context);
        } else if (LogEventUtil.isPrepare(event)) {
            processPrepare(event, context);
        } else if (LogEventUtil.isSequenceEvent(event)) {
            processSequence(event, context);
        } else if (LogEventUtil.isGcnEvent(event)) {
            processGcn(event, context);
        } else {
            processEvent(event, context);
        }
        if (currentTran != null && !currentTran.isStart()) {
            currentTran = null;
        }
    }

    private void processCommitSequence(long sequence) {
        this.lastCommitSequenceNum = sequence;
    }

    @Override
    public void onStart(HandlerContext context) {
        transactionStorage = new TransactionStorage(context.getRuntimeContext().getThreadRecorder());
    }

    @Override
    public void onStop() {
        if (transactionStorage != null) {
            transactionStorage.clear();
        }
    }

    @Override
    public void onStartConsume(HandlerContext context) {
        transactionStorage.clear();
    }

    private void processPrepare(LogEvent event, HandlerContext context) throws Exception {
        XaPrepareLogEvent prepareLogEvent = (XaPrepareLogEvent) event;
        if (prepareLogEvent.isOnePhase()) {
            // 一阶段真实TSO会导致 noTSO事务与一阶段乱序的情况产生，所以把one phase当作单机事务来处理
            currentTran.setXa(false);
            currentTran.setRealTSO(-1);
            currentTran.setTsoTransaction(false);
            currentTran.setCommit(context.getRuntimeContext());
            lastCommitSequenceNum = -1L;
            tryDoNext(context);
        } else {
            currentTran.setPrepare();
        }
    }

    private void processRollback(LogEvent logEvent, HandlerContext context) {
        lastCommitSequenceNum = -1;
        String xid = LogEventUtil.getXid(logEvent);
        RuntimeContext rc = context.getRuntimeContext();

        if (StringUtils.isNotBlank(xid)) {
            Transaction transaction = transactionStorage.getByXid(xid, context.getRuntimeContext());
            if (transaction == null) {
                log.warn("rollback event not found transaction obj , xid : " + xid + " event log : "
                    + logEvent.getHeader().getLogPos());
                return;
            }
            transaction.setRollback(rc);
        } else {
            currentTran.setRollback(rc);
        }
    }

    private void processStart(LogEvent logEvent, HandlerContext context) {
        lastCommitSequenceNum = -1L;
        if (currentTran != null && currentTran.isStart()) {
            String errorMsg = "occur fatal error, new transaction start but last transaction not finish! last id: "
                + currentTran.getTransactionId() + " ,last pos: " + currentTran.getStartLogPos() +
                ", cur id: " + LogEventUtil.getXid(logEvent) + " ,cur pos:" + logEvent.getLogPos();
            log.error(errorMsg);
            throw new PolardbxException(errorMsg);
        }
        try {
            Transaction tran = new Transaction(logEvent, context.getRuntimeContext());
            tran.setStart();
            currentTran = tran;
            if (tran.isCdcSingle()) {
                tran.release();
                return;
            }
            if (tran.isIgnore()) {
                return;
            }
            transactionStorage.add(tran);
        } catch (Exception e) {
            throw new CanalParseException(
                "new tran error binlogFile : " + context.getRuntimeContext().getBinlogFile() + " , logPos : " + context
                    .getRuntimeContext().getLogPos(), e);
        }
    }

    private void processCommit(LogEvent event, HandlerContext context) throws Exception {
        //DN8.0的V1版本将Commit Tso通过Variables的方式，记录到了XA Commit Event
        if (LogEventUtil.containsCommitGCN(event)) {
            processCommitSequence(((QueryLogEvent) event).getCommitGCN());
        }

        // init commitTran
        Transaction commitTran;
        String xid = LogEventUtil.getXid(event);
        if (StringUtils.isNotBlank(xid)) {
            commitTran = transactionStorage.getByXid(xid, context.getRuntimeContext());
            if (commitTran != null && lastCommitSequenceNum > 0) {
                commitTran.setTsoTransaction(true);
                commitTran.setRealTSO(lastCommitSequenceNum);
            }

            if (commitTran != null && commitTran.isSyncPoint()) {
                lastSyncPointSequenceNum = lastCommitSequenceNum;
            }
        } else {
            RuntimeContext rc = context.getRuntimeContext();
            if (rc.inSyncPointTxn()) {
                if (lastCommitSequenceNum <= rc.getHoldingTso()) {
                    currentTran.setRealTSO(rc.getHoldingTso());
                } else {
                    currentTran.setRealTSO(lastCommitSequenceNum);
                }
            }
            commitTran = currentTran;
        }

        if (checkSyncPoint) {
            // getEventCount > 0 保证处理的是常规事务
            // DN binlog中可能存在下面这种事务：
            // BEGIN
            // SEQUENCE purge SEQUENCE NUMBER: xxx
            // COMMIT
            // processSequence的时候会忽略除了commit sequence以外的其他类型的sequence
            if (commitTran != null && !commitTran.isSyncPointCheckIgnored() && commitTran.getEventCount() > 0) {
                // 说明开启了xa tso事务策略
                if (lastSyncPointSequenceNum > 0) {
                    // 验证：开启xa tso之后所有的事务commit都带tso
                    if (lastCommitSequenceNum < 0) {
                        log.warn("commit without sequence! commit trans:{}", commitTran);
                        LabEventManager.logEvent(LabEventType.SYNC_POINT_COMMIT_WITHOUT_SEQ,
                            "transaction:" + commitTran);
                    } else {
                        // 验证：sync point事务后的单机事务的tso都不小于sync point事务的tso
                        if (StringUtils.isBlank(xid) && lastCommitSequenceNum < lastSyncPointSequenceNum) {
                            log.warn(
                                "local txn's tso is less than sync point txn's tso! local txn's tso:{}, sync point txn's tso:{}",
                                lastCommitSequenceNum, commitTran);
                            LabEventManager.logEvent(LabEventType.SYNC_POINT_UNEXPECTED_LOCAL_SEQ,
                                "local txn tso: " + lastCommitSequenceNum + ", sync point tso: "
                                    + lastSyncPointSequenceNum);
                        }
                    }
                }
            }
        }

        // reset some variables
        lastCommitSequenceNum = -1L;

        // cdc single or ignored transaction is null
        if (commitTran != null) {
            commitTran.setCommit(context.getRuntimeContext());
        }
    }

    private void processSequence(LogEvent event, HandlerContext context) {
        //check commit sequence for DN 5.7
        SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
        if (sequenceLogEvent.isCommitSequence()) {
            processCommitSequence(sequenceLogEvent.getSequenceNum());
        }
    }

    private void processGcn(LogEvent event, HandlerContext context) {
        //check commit sequence for DN 8.0 V2
        GcnLogEvent gcnLogEvent = (GcnLogEvent) event;
        if (LogEventUtil.isHaveCommitSequence(gcnLogEvent)) {
            lastCommitSequenceNum = gcnLogEvent.getGcn();
        }
    }

    private void tryDoNext(HandlerContext context) {
        transactionStorage.purge(transactionGroup -> {
            try {
                context.doNext(transactionGroup);
            } catch (Exception e) {
                throw new PolardbxException("send transaction group to next handler error!", e);
            }
        });
    }

    private void writeFormatDescriptionEvent(FormatDescriptionLogEvent fde, HandlerContext context) throws Exception {
        if (receiveFormatDesc) {
            return;
        }

        FormatDescriptionEvent formatDescriptionEvent = BinlogGenerateUtil.buildFormatDescriptionEvent(
            context.getRuntimeContext().getServerId(),
            context.getRuntimeContext().getVersion());
        Transaction transaction = new Transaction(fde, formatDescriptionEvent, context.getRuntimeContext());
        transactionStorage.add(transaction);
        transaction.setCommit(context.getRuntimeContext());
        receiveFormatDesc = true;
        log.info("send format description event to next");
    }

    private void processEvent(LogEvent event, HandlerContext context) throws Exception {
        if (currentTran == null) {
            if (event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                writeFormatDescriptionEvent((FormatDescriptionLogEvent) event, context);
            }
            // 如果currentTran==null，说明此Query Event是DDL，识别出是DDL，直接push清空storage
            if (event.getHeader().getType() == LogEvent.QUERY_EVENT) {
                Transaction transaction = new Transaction((QueryLogEvent) event, context.getRuntimeContext());
                transactionStorage.add(transaction);
                transaction.setCommit(context.getRuntimeContext());
            }
            return;
        }
        for (EventFilter filter : eventFilterList) {
            if (filter.doFilter(currentTran, event)) {
                return;
            }
        }
        if (event.getHeader().getType() == LogEvent.QUERY_EVENT) {
            // 事务中出现了DDL
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_SKIP_DDL_IN_TRANSACTION)) {
                log.warn("transaction contains ddl, skip it! {}",
                    currentTran.getXid() + ", " + currentTran.getBinlogFileName() + ":" + currentTran.getStartLogPos()
                        + JSON.toJSONString(event));
                return;
            }
        }
        try {
            currentTran.processEvent(event, context.getRuntimeContext());
        } catch (Exception e) {
            log.error("process event error!" + currentTran.getXid() + "   ,  " + currentTran.getBinlogFileName() + ":"
                + currentTran.getStartLogPos() + JSON.toJSONString(event), e);
            throw e;
        }
    }
}
