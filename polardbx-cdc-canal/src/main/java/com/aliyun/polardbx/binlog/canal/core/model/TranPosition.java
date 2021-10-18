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

package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.system.InstructionCommand;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.canal.system.TxGlobalEvent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranPosition implements IXaTransaction<TranPosition> {

    private static final Logger logger = LoggerFactory.getLogger(TranPosition.class);
    private BinlogPosition begin;
    private BinlogPosition end;
    private Long tso = -1L;

    private String xid;

    private long transId;

    private InstructionCommand command;

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
                command = SystemDB.getInstance().parseInstructionCommand(wr);
                logger.warn("find cdc instruction type : " + command);
            }
            if (SystemDB.isGlobalTxTable(tm.getTableName())) {
                // 这里编码写死，因为没有字符串解析，所以用不到
                TxGlobalEvent txGlobalEvent = SystemDB.getInstance().parseTxGlobalEvent(wr, "utf8");
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
    }

    public BinlogPosition getPosition() {
        if (begin == null) {
            return null;
        }
        begin.setTso(tso);
        begin.setRtso(CommonUtils.generateTSO(tso, StringUtils.rightPad(transId + "", 29, "0"), null));
        return begin;
    }

    public BinlogPosition getEnd() {
        return end;
    }

    public void setEnd(BinlogPosition end) {
        if (this.end != null) {
            throw new RuntimeException("duplicate commit event pos  " + end);
        }
        this.end = end;
    }

    public boolean isCdcStartCmd() {
        return command != null && command.isCdcStart();
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

    @Override
    public boolean isComplete() {
        return begin != null && end != null;
    }

    public String getContent() {
        return command != null ? command.getContent() : null;
    }

    @Override
    public int compareTo(TranPosition o) {
        return (int) (tso - o.getTso());
    }
}
