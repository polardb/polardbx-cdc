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

package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SearchTsoEventHandle implements EventHandle {
    private static final Logger logger = LoggerFactory.getLogger(
        SearchTsoEventHandle.class);
    private final long searchTSO;
    private final Map<String, TranPosition> tranPositionMap = Maps.newHashMap();
    private final AuthenticationInfo authenticationInfo;
    private BinlogPosition returnBinlogPosition;
    private BinlogPosition endPosition;
    private String currentFile;
    private long totalSize;
    private long logPos = 4;
    private long lastPrintTimestamp = System.currentTimeMillis();
    private TranPosition currentTransaction;
    private TranPosition commandTransaction;
    private long lastTSO = -1;

    private boolean interupt = false;
    private long minTSO;

    public SearchTsoEventHandle(long searchTSO, AuthenticationInfo authenticationInfo, long minTSO) {
        this.searchTSO = searchTSO;
        this.authenticationInfo = authenticationInfo;
        this.minTSO = minTSO;
    }

    @Override
    public boolean interupt() {
        return interupt;
    }

    @Override
    public void onStart() {
        tranPositionMap.clear();
        currentTransaction = null;
        lastTSO = -1;
    }

    @Override
    public void onEnd() {

    }

    public void setEndPosition(BinlogPosition endPosition) {
        this.endPosition = endPosition;
        this.totalSize = endPosition.getPosition();
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public void reset() {
        lastTSO = -1;
        tranPositionMap.clear();
        logPos = 4;
        interupt = false;
    }

    @Override
    public void handle(LogEvent event, LogPosition logPosition) {
        if (LogEventUtil.isStart(event)) {
            onStart(event, logPosition);
        } else if (LogEventUtil.isPrepare(event)) {
            onPrepare(event, logPosition);
        } else if (LogEventUtil.isCommit(event)) {
            // check commit tso for AliSQL 8.0
            if (LogEventUtil.containsCommitGCN(event) && checkCommitSequence(((QueryLogEvent) event).getCommitGCN())) {
                return;
            }
            onCommit(event, logPosition);
            onTransactionEndEvent();
        } else if (LogEventUtil.isRollback(event)) {
            onRollback(event, logPosition);
            onTransactionEndEvent();
        } else if (LogEventUtil.isSequenceEvent(event)) {
            // check commit tso fro AliSQL 5.7
            SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
            if (sequenceLogEvent.isCommitSequence() && checkCommitSequence(sequenceLogEvent.getSequenceNum())) {
                return;
            }
        } else {
            if (currentTransaction != null) {
                currentTransaction.processEvent(event);
                if (currentTransaction.isCdcStartCmd() || currentTransaction.isStorageChangeCmd()) {
                    this.commandTransaction = currentTransaction;
                }
            }
        }
        if (returnBinlogPosition != null) {
            interupt = true;
            logger.info("returnBinlogPosition is found, stop searching. position info is {}:{}",
                returnBinlogPosition.getFileName(), returnBinlogPosition.getFilePattern());
            return;
        }
        logPos = event.getLogPos();

        if (logPos >= endPosition.getPosition() || !logPosition.getFileName()
            .equalsIgnoreCase(endPosition.getFileName())) {
            lastTSO = -1L;
            interupt = true;
            logger.warn("reach end logPosition：" + logPosition + ", endPos : " + endPosition + " , logPos:" + logPos);
            return;
        }
        printProcess(logPos, logPosition.getFileName());
    }

    private boolean checkCommitSequence(long sequence) {
        lastTSO = sequence;
        if (searchTSO > 0 && lastTSO > searchTSO && isCmdTxnNullOrCompleted()) {
            interupt = true;
            logger.info("search tso " + searchTSO + " is less than last tso " + lastTSO + ", stop searching.");
            return true;
        }
        return false;
    }

    private void onTransactionEndEvent() {
        currentTransaction = null;
        lastTSO = -1;
    }

    private boolean isCmdTxnNullOrCompleted() {
        return commandTransaction == null || commandTransaction.isComplete();
    }

    private void onStart(LogEvent event, LogPosition logPosition) {
        String xid = LogEventUtil.getXid(event);
        if (xid == null) {
            currentTransaction = new TranPosition();
            return;
        }
        TranPosition tranPosition = new TranPosition();
        try {
            tranPosition.setTransId(LogEventUtil.getTranIdFromXid(xid, authenticationInfo.getCharset()));
        } catch (Exception e) {
            logger.error("process start event failed! pos : " + logPosition.toString(), e);
            throw new PolardbxException(e);
        }
        tranPosition.setXid(xid);
        tranPosition.setBegin(buildPosition(event, logPosition));
        currentTransaction = tranPosition;
        tranPositionMap.put(tranPosition.getXid(), tranPosition);
    }

    private BinlogPosition buildPosition(LogEvent event, LogPosition logPosition) {
        return new BinlogPosition(currentFile, logPos, event.getServerId(), event.getWhen());
    }

    private void onPrepare(LogEvent event, LogPosition logPosition) {
        currentTransaction = null;
    }

    private void onCommit(LogEvent event, LogPosition logPosition) {
        String xid = LogEventUtil.getXid(event);
        TranPosition tranPosition;
        if (xid != null) {
            tranPosition = tranPositionMap.get(xid);
        } else {
            tranPosition = currentTransaction;
        }
        if (tranPosition != null) {
            tranPosition.setEnd(buildPosition(event, logPosition));
            tranPosition.setTso(lastTSO);
            if (minTSO > 0 && lastTSO < minTSO) {
                return;
            }
            if (returnBinlogPosition == null && lastTSO > 0 && lastTSO < searchTSO) {
                returnBinlogPosition = tranPosition.getPosition();
            }
            if (searchTSO == -1 && tranPosition.isCdcStartCmd()) {
                returnBinlogPosition = tranPosition.getPosition();
            }
        }
    }

    private void onRollback(LogEvent event, LogPosition logPosition) {
        String xid = LogEventUtil.getXid(event);
        if (xid != null) {
            tranPositionMap.remove(xid);
        }
    }

    private void printProcess(long logPos, String fileName) {
        long now = System.currentTimeMillis();
        if (now - lastPrintTimestamp > 5000L) {
            logger.info(" search pos progress : " + (logPos * 100 / totalSize) + "% : " + fileName);
            lastPrintTimestamp = now;
        }
    }

    public BinlogPosition getCommandPosition() {
        return commandTransaction != null ? commandTransaction.getPosition() : null;
    }

    public BinlogPosition searchResult() {
        return returnBinlogPosition;
    }

    public String getTopologyContext() {
        return commandTransaction != null ? commandTransaction.getContent() : null;
    }
}
