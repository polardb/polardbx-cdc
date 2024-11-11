/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.system.InstructionCommand;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.canal.system.TxGlobalEvent;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TranPosition implements IXaTransaction<TranPosition> {

    private static final Logger logger = LoggerFactory.getLogger(TranPosition.class);
    private BinlogPosition begin;
    private Long tso = -1L;

    private String xid;

    private long transId;

    private String rtso;

    private InstructionCommand command;

    private BinlogPosition end;

    private List<ITranStatChangeListener> listenerList = new ArrayList<>();

    public TranPosition() {
    }

    public long getTransId() {
        return transId;
    }

    public void setTransId(long transId) {
        this.transId = transId;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public void processEvent(LogEvent event) {
        if (event instanceof WriteRowsLogEvent) {
            WriteRowsLogEvent wr = (WriteRowsLogEvent) event;
            TableMapLogEvent tm = wr.getTable();
            if (SystemDB.isInstruction(tm.getDbName(), tm.getTableName())) {
                command = SystemDB.parseInstructionCommand(wr);
                logger.warn("tranPosition find cdc instruction type : " + command);
            }
            if (SystemDB.isGlobalTxTable(tm.getTableName())) {
                // 这里编码写死，因为没有字符串解析，所以用不到
                TxGlobalEvent txGlobalEvent = SystemDB.parseTxGlobalEvent(wr, "utf8");
                transId = txGlobalEvent.getTxGlobalTid();
                Long tmpTso = txGlobalEvent.getTxGlobalTso();
                if (tmpTso != null) {
                    tso = tmpTso;
                }
            }
        }

    }

    public BinlogPosition getBegin() {
        return begin;
    }

    public void setBegin(BinlogPosition begin) {
        this.begin = begin;
        detectedStateChange();
    }

    public void registerStateChangeListener(ITranStatChangeListener listener) {
        this.listenerList.add(listener);
    }

    private void detectedStateChange() {
        if (begin != null && end != null) {
            for (ITranStatChangeListener l : listenerList) {
                l.onComplete(this);
            }
        }
    }

    public BinlogPosition getPosition() {
        return begin;
    }

    public String buildRTso() {
        return CommonUtils.generateTSO(tso, StringUtils.rightPad(transId + "", 29, "0"), null);
    }

    public void complete(BinlogPosition end) {
        if (this.end != null) {
            throw new RuntimeException("duplicate commit event pos  " + end);
        }
        this.end = end;
        detectedStateChange();
    }

    public boolean isCdcStartCmd() {
        return command != null && command.isCdcStart();
    }

    public boolean isMetaSnapCmd() {
        return command != null && command.isMetaSnapshotCmd();
    }

    public boolean isStorageChangeCmd() {
        return command != null && command.isStorageChangeCmd();
    }

    public Long getTso() {
        return tso;
    }

    public void setTso(Long tso) {
        this.tso = tso;
    }

    public String getRtso() {
        return rtso;
    }

    public void setRtso(String rtso) {
        this.rtso = rtso;
    }

    @Override
    public boolean isComplete() {
        return end != null;
    }

    public void setEnd(BinlogPosition end) {
        this.end = end;
    }

    public String getContent() {
        return command != null ? command.getContent() : null;
    }

    public String getCommandId() {
        return command.getInstructionId();
    }

    public void setCommand(InstructionCommand command) {
        this.command = command;
    }

    @Override
    public int compareTo(TranPosition o) {
        return (int) (tso - o.getTso());
    }

    @Override
    public String toString() {
        return "TranPosition{" +
            "begin=" + begin +
            ", end=" + end +
            ", tso=" + tso +
            ", xid='" + xid + '\'' +
            ", transId=" + transId +
            ", command=" + command +
            ", listenerList=" + listenerList +
            '}';
    }
}
