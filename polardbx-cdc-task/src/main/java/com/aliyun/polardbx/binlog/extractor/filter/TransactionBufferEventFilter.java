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
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
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
    /**
     * DN 5.7，通过Sequence Event记录Snapshot Tso和Commit Tso，有两种SequenceType
     * DN 8.0的V1版本，通过XA End Event记录Snapshot Tso，通过XA Commit Event记录Commit Tso
     * 对于这两种方式，它们的Snapshot Tso都是记录到XA Start之后的，所以处理模式类似
     */
    private long lastCommitSequenceNum;
    /**
     * DN8.0的V2版本，通过Gcn Event记录Snapshot Tso和Commit Tso，未做类型的上区分，具体是记录的Snapshot还是Commit
     * 依实际情况而定，lastGcn代表的可能是Snapshot Tso，也可能是 Commit Tso
     */
    private long lastGcn = -1L;

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
            processEnd(event, context);
        } else if (LogEventUtil.isCommit(event)) {
            processCommit(event, context);
            tryDoNext(context);
        } else if (LogEventUtil.isRollback(event)) {
            processRollback(event, context);
            tryDoNext(context);
        } else if (LogEventUtil.isSequenceEvent(event)) {
            processSequence(event, context);
        } else if (LogEventUtil.isGCNEvent(event)) {
            processGcn(event, context);
        } else {
            processEvent(event, context);
        }
    }

    private void processPrepareSequence(long sequence) {
        if (currentTran.isXa()) {
            currentTran.setTsoTransaction();
        }
        currentTran.setSnapshotSeq(sequence);
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

    private void processRollback(LogEvent logEvent, HandlerContext context) throws Exception {
        String xid = LogEventUtil.getXid(logEvent);
        RuntimeContext rc = context.getRuntimeContext();

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
                lastGcn = -1L;
                return;
            }
            // 1> DN8.0的V2版本，将Snapshot tso通过Gcn Event记录到了XA Start前面，所以，需要在此处对Snapshot tso进行处理
            // 2> DN8.0的V2版本，为单机事务也记录了Commit TSO，通过Gcn Event记录到了Begin Event前面，但我们和5.7的行为保持一致，
            //    通过前序2pc事务的真实tso为单机事务生成虚拟tso，不使用Gcn
            // 3> 处理完成后，需立即将lastGcn设置为-1，以免对后序普通XA事务(事务策略不是tso的MySQL原生XA事务)造成影响
            if (lastGcn != -1L) {
                if (currentTran.isXa()) {
                    currentTran.setTsoTransaction();
                    currentTran.setSnapshotSeq(lastGcn);
                }
                lastGcn = -1L;
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

    private void processEnd(LogEvent event, HandlerContext context) {
        // DN8.0的V1版本，将Snapshot Tso通过Variables的方式，记录到了XA End Event
        if (LogEventUtil.containsPrepareGCN(event)) {
            processPrepareSequence(((QueryLogEvent) event).getPrepareGCN());
        }
    }

    private void processCommit(LogEvent event, HandlerContext context) throws Exception {
        //DN8.0的V1版本将Commit Tso通过Variables的方式，记录到了XA Commit Event
        if (LogEventUtil.containsCommitGCN(event)) {
            processCommitSequence(((QueryLogEvent) event).getCommitGCN());
        }

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
            if (lastGcn > 0 && lastCommitSequenceNum > 0) {
                throw new PolardbxException(
                    "lastGcn and lastCommitSequenceNum can`t be greater than zero in the mean while.");
            }
            if (lastCommitSequenceNum > 0) {
                commitTran.setRealTSO(lastCommitSequenceNum);
            } else if (lastGcn > 0) {
                commitTran.setRealTSO(lastGcn);
            }
        }
        commitTran.setCommit(context.getRuntimeContext());
    }

    private void processSequence(LogEvent event, HandlerContext context) {
        //check prepare and commit tso for DN 5.7
        SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
        if (sequenceLogEvent.isCommitSequence()) {
            processCommitSequence(sequenceLogEvent.getSequenceNum());
        } else if (sequenceLogEvent.isSnapSequence()) {
            processPrepareSequence(sequenceLogEvent.getSequenceNum());
        }
    }

    private void processGcn(LogEvent event, HandlerContext context) {
        GcnLogEvent gcnLogEvent = (GcnLogEvent) event;
        lastGcn = gcnLogEvent.getGcn();
    }

    private void tryDoNext(HandlerContext context) throws Exception {
        lastGcn = -1L;
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
            // 如果currentTran==null，说明此Query Event是DDL，识别出是DDL，直接push清空storage
            // DN8.0的V2版本在ddl语句前面会跟随一个Gcn Event，需要对Gcn进行reset操作
            if (event.getHeader().getType() == LogEvent.QUERY_EVENT) {
                Transaction transaction = new Transaction((QueryLogEvent) event, context.getRuntimeContext(), storage);
                transactionStorage.add(transaction);
                transaction.setCommit(context.getRuntimeContext());
                lastGcn = -1L;
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
