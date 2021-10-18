/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.TransactionStorage;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil;
import com.aliyun.polardbx.binlog.storage.Storage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author chengjin.lyf on 2020/7/15 7:24 下午
 * @since 1.0.25
 */
public class TransactionBufferEventFilter implements LogEventFilter<LogEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TransactionBufferEventFilter.class);
    private final Storage storage;
    private TransactionStorage transactionStorage;
    private Transaction currentTran;
    private boolean receiveFormatDesc = false;
    private long lastSequenceNum;

    public TransactionBufferEventFilter(Storage storage) {
        this.storage = storage;
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
            //check prepare tso for AliSQL 8.0
            if (LogEventUtil.containsPrepareGCN(event)) {
                processPrepareSequence(((QueryLogEvent) event).getPrepareGCN());
            }
        } else if (LogEventUtil.isCommit(event)) {
            //check commit tso for AliSQL 8.0
            if (LogEventUtil.containsCommitGCN(event)) {
                processCommitSequence(((QueryLogEvent) event).getCommitGCN());
            }
            processCommit(event, context);
            tryDoNext(context);
        } else if (LogEventUtil.isRollback(event)) {
            processRollback(event, context.getRuntimeContext());
            tryDoNext(context);
        } else if (LogEventUtil.isSequenceEvent(event)) {
            //check prepare and commit tso for AliSQL 5.7
            SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
            if (sequenceLogEvent.isCommitSequence()) {
                processCommitSequence(sequenceLogEvent.getSequenceNum());
            } else if (sequenceLogEvent.isSnapSequence()) {
                processPrepareSequence(sequenceLogEvent.getSequenceNum());
            }
        } else {
            try {
                processEvent(event, context);
            } catch (Exception e) {
                throw new PolardbxException(e);
            }
        }
    }

    private void processPrepareSequence(long sequence) {
        if (currentTran.isXa()) {
            currentTran.setTsoTransaction();
        }
        currentTran.setSnapshotSeq(sequence);
    }

    private void processCommitSequence(long sequence) {
        this.lastSequenceNum = sequence;
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

    private void processRollback(LogEvent logEvent, RuntimeContext rc) {
        String xid = LogEventUtil.getXid(logEvent);
        if (StringUtils.isNotBlank(xid)) {
            Transaction transaction = transactionStorage.getByXid(xid);
            if (transaction == null) {
                logger.warn("rollback event not found transaction obj , xid : " + xid + " event log : "
                    + logEvent.getHeader().getLogPos());
                return;
            }
            transaction.setRollback(rc);
        } else {
            currentTran.setRollback(rc);
        }
    }

    private void processStart(LogEvent logEvent, HandlerContext context) {
        if (currentTran != null && currentTran.isStart()) {
            String errorMsg = "occur fatal error, new transaction start but last transaction not finish! last id : "
                + currentTran.getTransactionId() + ", cur id " + LogEventUtil.getXid(logEvent);
            logger.error(errorMsg);
            throw new PolardbxException(errorMsg);
        }
        try {
            Transaction tran = new Transaction(logEvent, context.getRuntimeContext(), storage);
            tran.setStart();
            currentTran = tran;
            if (tran.isCdcSingle()) {
                tran.release();
                return;
            }
            transactionStorage.add(tran);
        } catch (Exception e) {
            throw new CanalParseException(e);
        }
    }

    private void processCommit(LogEvent event, HandlerContext context) {
        String xid = LogEventUtil.getXid(event);
        Transaction commitTran = null;
        if (StringUtils.isNotBlank(xid)) {
            commitTran = transactionStorage.getByXid(xid);
        }

        if (commitTran == null) {
            commitTran = currentTran;
            currentTran = null;
        }
        if (commitTran == null) {
            // maybe other commit
            return;
        }
        if (commitTran.isTsoTransaction()) {
            commitTran.setRealTSO(lastSequenceNum);
        }
        commitTran.setCommit(context.getRuntimeContext());
    }

    private void tryDoNext(HandlerContext context) throws Exception {
        transactionStorage.purge();
        TransactionGroup group;
        while ((group = transactionStorage.fetchNext()) != null) {
            context.doNext(group);
        }
    }

    private void writeFormatDescriptionEvent(FormatDescriptionLogEvent fde, HandlerContext context) throws Exception {
        if (receiveFormatDesc) {
            return;
        }
        Transaction transaction = new Transaction(fde,
            BinlogGenerateUtil.buildFormatDescriptionEvent(context.getRuntimeContext().getServerId(),
                context.getRuntimeContext().getVersion()),
            context.getRuntimeContext());
        transactionStorage.add(transaction);
        transaction.setCommit(context.getRuntimeContext());
        receiveFormatDesc = true;
        logger.info("send format description event to next");
    }

    private void processEvent(LogEvent event, HandlerContext context) throws Exception {
        if (currentTran == null) {
            if (event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                writeFormatDescriptionEvent((FormatDescriptionLogEvent) event, context);
            }
            if (event.getHeader().getType() == LogEvent.QUERY_EVENT) {
                // 识别出是DDL，直接push清空storage。
                Transaction transaction = new Transaction((QueryLogEvent) event, context.getRuntimeContext(), storage);
                transactionStorage.add(transaction);
                transaction.setCommit(context.getRuntimeContext());
            }
            return;
        }
        currentTran.processEvent(event, context.getRuntimeContext());

        if (currentTran.isCommit()) {
            tryDoNext(context);
        }

        if (!currentTran.isStart()) {
            currentTran = null;
        }
    }
}
