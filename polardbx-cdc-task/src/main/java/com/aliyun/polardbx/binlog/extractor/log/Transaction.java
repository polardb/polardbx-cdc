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

package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.IXaTransaction;
import com.aliyun.polardbx.binlog.canal.system.InstructionType;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.canal.system.TxGlobalEvent;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.TransactionMemoryLeakDectorManager;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.storage.AlreadyExistException;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;

/**
 * 只输出 全局有序唯一TSO，下游合并自己去做 真实TSO+Xid 合并
 *
 * @author chengjin.lyf on 2020/7/17 5:55 下午
 * @since 1.0.25
 */
public class Transaction implements HandlerEvent, IXaTransaction<Transaction>, Iterable<TxnItemRef> {

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final String encoding = "UTF-8";
    private static final String ZERO_19_PADDING = StringUtils.leftPad("0", 10, "0");
    private String virtualTSO;
    private String xid;
    private Long transactionId;
    private TRANSACTION_STATE state = TRANSACTION_STATE.STATE_START;
    private String partitionId;
    private String nextTraceId;
    private String lastTraceId;
    private String charset;
    private boolean txGlobal = false;
    private Long txGlobalTso;
    private Long txGlobalTid;
    private boolean xa = false;
    private boolean tsoTransaction = false;
    private boolean descriptionEvent = false;
    private long startTime;
    private String startSchema;
    private boolean heartbeat = false;
    private InstructionType instructionType = null;
    private String instructionContent = null;
    private String instructionId = null;
    private long startLogPos;
    private long stopLogPos;
    private String binlogFileName;
    private DDLEvent ddlEvent;
    private FormatDescriptionEvent fde;
    private FormatDescriptionLogEvent fdle;
    private long realTSO = -1;
    private TransactionCommitListener listener;
    private VirtualTSO virtualTSOModel;
    private Long snapshotSeq;
    private String storageInstanceId;
    private TxnBuffer buffer;
    private Storage storage;
    private String sourceCdcSchema;
    private String groupName;
    private TxnKey bufferKey;

    public Transaction(FormatDescriptionLogEvent fdle, FormatDescriptionEvent fde, RuntimeContext rc) {
        this.fde = fde;
        this.fdle = fdle;
        this.descriptionEvent = true;
        startSchema = "";
        this.transactionId = Math.abs(CommonUtils.randomXid());
        this.partitionId = rc.getStorageInstId() + startSchema;
        this.xid = transactionId + partitionId + "00000-FDE";
        this.storageInstanceId = rc.getStorageInstId();
        this.binlogFileName = rc.getBinlogFile();
        this.startLogPos = 0;
        TransactionMemoryLeakDectorManager.getInstance().watch(this);
    }

    public Transaction(QueryLogEvent qwe, RuntimeContext rc, Storage storage) throws AlreadyExistException {
        this.storage = storage;
        startSchema = "";
        generateKey(qwe, rc);
        this.storageInstanceId = rc.getStorageInstId();
        this.binlogFileName = rc.getBinlogFile();
        this.startLogPos = qwe.getLogPos();
        buildBuffer();
        qwe.setTrace(generateFakeTraceId());
        addTxnBuffer(qwe);
        TransactionMemoryLeakDectorManager.getInstance().watch(this);
    }

    public Transaction(LogEvent logEvent, RuntimeContext rc, Storage storage) throws Exception {
        this.storage = storage;
        this.xid = LogEventUtil.getXid(logEvent);
        this.charset = encoding;
        this.storageInstanceId = rc.getStorageInstId();
        if (logEvent.getHeader().getType() == LogEvent.QUERY_EVENT) {
            QueryLogEvent queryLogEvent = (QueryLogEvent) logEvent;
            if (queryLogEvent.getClientCharset() > 0) {
                this.charset = CharsetConversion.getJavaCharset(queryLogEvent.getClientCharset());
            }
            this.startSchema = queryLogEvent.getDbName();
        }
        if (StringUtils.isNotBlank(this.xid)) {
            // 真实事务id，如果是tso事务，则参与排序，否则不参与排序
            this.transactionId = LogEventUtil.getTranIdFromXid(xid, encoding);
            String groupName = LogEventUtil.getGroupFromXid(xid, encoding);
            this.groupName = groupName;
            this.partitionId = rc.getStorageInstId() + groupName;
            this.xa = true;
        } else {
            // 随机生成一下，不参与排序
            generateKey(logEvent, rc);
        }
        this.startTime = logEvent.getWhen();
        this.startLogPos = logEvent.getLogPos();
        this.binlogFileName = rc.getBinlogFile();
        if (!isCdcSingle()) {
            buildBuffer();
        }
        TransactionMemoryLeakDectorManager.getInstance().watch(this);
    }

    private void buildBuffer() throws AlreadyExistException {
        this.bufferKey = new TxnKey(transactionId + "", partitionId);
        buffer = storage.create(bufferKey);
    }

    public TxnKey getBufferKey() {
        return bufferKey;
    }

    public boolean isCdcSingle() {
        return SystemDB.isCdcSingleGroup(groupName);
    }

    private void generateKey(LogEvent logEvent, RuntimeContext rc) {
        this.transactionId = Math.abs(CommonUtils.randomXid());
        this.partitionId = rc.getStorageInstId();
        this.xid = transactionId + partitionId + logEvent.getLogPos();
    }

    public void setListener(TransactionCommitListener listener) {
        this.listener = listener;
    }

    public void processEvent(LogEvent event, RuntimeContext rc) throws Exception {
        if (LogEventUtil.isPrepare(event)) {
            XaPrepareLogEvent prepareLogEvent = (XaPrepareLogEvent) event;
            if (prepareLogEvent.isOnePhase()) {
                xa = false;
                // 一阶段真实TSO会导致 noTSO事务与一阶段乱序的情况产生
                tsoTransaction = false;
                this.realTSO = -1;
                setCommit(rc);
            } else {
                setPrepare();
            }
        } else if (LogEventUtil.isEnd(event)) {
            // do nothing
        } else {
            processEvent_0(event, rc);
        }
    }

    private void processEvent_0(LogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        if (LogEventUtil.isRowsQueryEvent(event)) {
            RowsQueryLogEvent queryLogEvent = (RowsQueryLogEvent) event;
            try {
                nextTraceId = LogEventUtil.buildTrace(queryLogEvent);
            } catch (Exception e) {
                logger.error("parser trace error " + queryLogEvent.getRowsQuery(), e);
                throw e;
            }
            return;
        }
        if (filter(event)) {
            return;
        }
        if (processSpecialTableData(event, rc)) {
            return;
        }
        descriptionEvent = event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT;
        if (event.getHeader().getType() == LogEvent.TABLE_MAP_EVENT) {
            //如果nextTraceId为null，说明binlog_rows_query_log_events参数值为OFF，否则物理binlog中肯定会有traceId
            if (nextTraceId == null) {
                nextTraceId = generateFakeTraceId();
            }
            event.setTrace(nextTraceId);
            lastTraceId = nextTraceId;
            nextTraceId = null;
        } else {
            //直接使用前面紧邻的TableMapEvent的TraceId
            event.setTrace(lastTraceId);
        }

        addTxnBuffer(event);
    }

    private void addTxnBuffer(LogEvent logEvent) {
        if (buffer == null) {
            return;
        }
        TxnBufferItem txnItem = TxnBufferItem.builder()
            .traceId(logEvent.getTrace())
            .payload(logEvent.toBytes())
            .eventType(logEvent.getHeader().getType())
            .build();
        buffer.push(txnItem);
    }

    private boolean processSpecialTableData(LogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {

        if (event instanceof RowsLogEvent) {
            RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
            TableMapLogEvent table = rowsLogEvent.getTable();
            if (SystemDB.isGlobalTxTable(table.getTableName())) {
                if (event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                    || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1) {
                    processTxGlobleEvent((WriteRowsLogEvent) rowsLogEvent);
                }
                return true;
            }
            if (SystemDB.isHeartbeat(rowsLogEvent.getTable().getDbName(), rowsLogEvent.getTable().getTableName())) {
                setHeartbeat(true);
                this.sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                releaseBuffer();
                return true;
            }
            if (SystemDB.isLogicDDL(table.getDbName(), table.getTableName())) {
                if (event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                    || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1) {
                    processDDL((WriteRowsLogEvent) rowsLogEvent, rc);
                    this.sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                }
                releaseBuffer();
                return true;
            }
            if (SystemDB.isSys(table.getDbName())) {
                if (SystemDB.isInstruction(table.getDbName(), table.getTableName()) && (
                    event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                        || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1)) {
                    processInstruction((WriteRowsLogEvent) rowsLogEvent, rc);
                }
                releaseBuffer();
                this.sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                return true;
            }
        }

        if (event instanceof TableMapLogEvent) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            if (SystemDB.isHeartbeat(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName())) {
                setHeartbeat(true);
                releaseBuffer();
                this.sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
            if (SystemDB.isInstruction(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName())) {
                releaseBuffer();
                this.sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
            if (SystemDB.isSys(tableMapLogEvent.getDbName())) {
                releaseBuffer();
                this.sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
        }

        return false;
    }

    private void processInstruction(WriteRowsLogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        BinlogParser binlogParser = new BinlogParser();
        binlogParser.parse(SystemDB.getInstance().getInstructionTableMeta(), event, "utf8");
        String instructionType = (String) binlogParser.getField(SystemDB.INSTRUCTION_FIELD_INSTRUCTION_TYPE);
        String instructionContent = (String) binlogParser.getField(SystemDB.INSTRUCTION_FIELD_INSTRUCTION_CONTENT);
        String instructionId = (String) binlogParser.getField(SystemDB.INSTRUCTION_FIELD_INSTRUCTION_ID);
        this.instructionType = InstructionType.valueOf(instructionType);
        this.instructionContent = instructionContent;
        this.instructionId = instructionId;
    }

    private void processDDL(WriteRowsLogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        BinlogParser binlogParser = new BinlogParser();
        binlogParser.parse(SystemDB.getInstance().getDdlTableMeta(), event, "utf8");
        String ddl = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_DDL_SQL);
        String metaInfo = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_META_INFO);
        String sqlKind = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_SQL_KIND);
        String logicSchema = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_SCHEMA_NAME);
        String tableName = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_TABLE_NAME);
        String visible = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_VISIBILITY);
        String ext = (String) binlogParser.getField(SystemDB.DDL_RECORD_FIELD_EXT);
        DDLRecord ddlRecord = DDLRecord.builder()
            .ddlSql(ddl)
            .sqlKind(sqlKind)
            .schemaName(logicSchema)
            .tableName(tableName)
            .metaInfo(metaInfo)
            .build();
        ddlEvent = new DDLEvent();
        ddlEvent.setDdlRecord(ddlRecord);
        ddlEvent.setExt(ext);
        ddlEvent.setPosition(new BinlogPosition(rc.getBinlogFile(),
            event.getLogPos(),
            -1,
            event.getHeader().getWhen()));
        ddlEvent.setVisible(Integer.parseInt(visible) == 1);
        logger.warn("receive logic ddl " + new Gson().toJson(ddlRecord));

    }

    public void afterCommit(RuntimeContext rc) {
        stopLogPos = rc.getLogPos();
        if (isTxGlobal()) {
            if (getEventCount() > 0) {
                // xa事务会有部分分片保存在事务中的情况
                resetTranId(getTxGlobalTid());
            }
        }

        virtualTSO = generateTSO(rc);
        if (isDDL()) {
            ddlEvent.getPosition().setRtso(virtualTSO);
        }
    }

    public boolean needRevert() {
        return isRollback() || StringUtils.isEmpty(virtualTSO);
    }

    public boolean isVisible() {
        return getEventCount() > 0 || isDDL() && ddlEvent.isVisible() || isHeartbeat() || isDescriptionEvent()
            || isInstructionCommand();
    }

    public boolean canNotFilter() {
        return getEventCount() > 0 || isDDL() || isHeartbeat() || isDescriptionEvent() || isInstructionCommand();
    }

    private boolean isGlobalTxTable(String tableName) {
        return SystemDB.DRDS_GLOBAL_TX_LOG.equalsIgnoreCase(tableName);
    }

    private boolean filter(LogEvent event) {
        if (event.getHeader().getType() == LogEvent.TABLE_MAP_EVENT) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            return isGlobalTxTable(tableMapLogEvent.getTableName());
        }
        return false;
    }

    private void processTxGlobleEvent(WriteRowsLogEvent rowsLogEvent) {
        TxGlobalEvent txGlobalEvent = SystemDB.getInstance().parseTxGlobalEvent(rowsLogEvent, charset);
        txGlobalTid = txGlobalEvent.getTxGlobalTid();
        txGlobalTso = txGlobalEvent.getTxGlobalTso();
        this.transactionId = txGlobalTid;
        txGlobal = true;
        xa = true;
        if (txGlobalTso != null && txGlobalTso > 0) {
            tsoTransaction = true;
        }
    }

    public void resetTranId(Long newTransactionId) {
        this.transactionId = newTransactionId;
    }

    private String generateTSO(RuntimeContext rc) {
        // 默认初始化为maxTSO，下面会判断如果是真TSO事务，则取最近tso
        Long currentTso = rc.getMaxTSO();
        String uniqueTxnId;
        long computeTransactionId = 0;
        if (isDescriptionEvent()) {
            currentTso = 0L;
            uniqueTxnId = StringUtils.leftPad("0", 29, "0");
        } else {
            if (isTsoTransaction()) {
                rc.setMaxTxnId(transactionId);
                computeTransactionId = transactionId;
                long tmpCurrentTso = txGlobal ? txGlobalTso : realTSO;
                if (tmpCurrentTso > currentTso) {
                    rc.setMaxTSO(tmpCurrentTso);
                }
                currentTso = tmpCurrentTso;
                uniqueTxnId = StringUtils.leftPad(String.valueOf(transactionId), 19, "0") + ZERO_19_PADDING;
            } else {
                long lastTxnId = rc.getMaxTxnId();
                computeTransactionId = lastTxnId;
                int nextSeq = rc.nextMaxTxnIdSequence(lastTxnId);
                uniqueTxnId = StringUtils.leftPad(lastTxnId + "", 19, "0") + StringUtils.leftPad(
                    nextSeq + "", 10, "0");
            }
        }

        if (currentTso == null) {
            throw new PolardbxException("tso should not be null " + this.toString());
        }

        String storageInstId = rc.getStorageInstId();

        if (isDDL() || isDescriptionEvent() || isInstructionCommand()) {
            storageInstId = null;
        }

        String vto = CommonUtils.generateTSO(currentTso, uniqueTxnId, storageInstId);
        virtualTSOModel = new VirtualTSO(currentTso, computeTransactionId, Integer.parseInt(vto.substring(38, 48)));
        return vto;
    }

    public boolean isStart() {
        return state == TRANSACTION_STATE.STATE_START;
    }

    public boolean isDescriptionEvent() {
        return descriptionEvent;
    }

    public boolean isCommit() {
        return state == TRANSACTION_STATE.STATE_COMMIT;
    }

    public void setCommit(RuntimeContext rc) {
        if (isCommit()) {
            throw new PolardbxException("duplicate commit event!");
        }
        this.state = TRANSACTION_STATE.STATE_COMMIT;

        afterCommit(rc);
        listener.onCommit(this);
    }

    public boolean isPrepare() {
        return state == TRANSACTION_STATE.STATE_PREPARE;
    }

    public String getVirtualTSO() {
        return virtualTSO;
    }

    public void setVirtualTSO(String virtualTSO) {
        this.virtualTSO = virtualTSO;
    }

    public VirtualTSO getVirtualTSOModel() {
        return virtualTSOModel;
    }

    public void setRealTSO(long realTSO) {
        this.realTSO = realTSO;
    }

    public boolean hasTso() {
        return StringUtils.isNotBlank(virtualTSO);
    }

    public boolean isXa() {
        return xa;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public void setStart() {
        this.state = TRANSACTION_STATE.STATE_START;
    }

    public void setPrepare() {
        this.state = TRANSACTION_STATE.STATE_PREPARE;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getPartitionId() {
        return partitionId;
    }

    public int getEventCount() {
        return buffer == null ? 0 : buffer.itemSize();
    }

    private String generateFakeTraceId() {
        return LogEventUtil.buildTraceId(getEventCount() + "", null);
    }

    public boolean isTxGlobal() {
        return txGlobal;
    }

    public Long getTxGlobalTso() {
        return txGlobalTso;
    }

    public Long getTxGlobalTid() {
        return txGlobalTid;
    }

    public boolean isTsoTransaction() {
        return tsoTransaction;
    }

    public boolean isRollback() {
        return state == TRANSACTION_STATE.STATE_ROLLBACK;
    }

    public void setRollback(RuntimeContext rc) {
        this.state = TRANSACTION_STATE.STATE_ROLLBACK;
        this.rollback(rc);
    }

    private void rollback(RuntimeContext rc) {
        afterCommit(rc);
        releaseBuffer();
        this.listener.onCommit(this);
    }

    private void releaseBuffer() {
        if (buffer != null && !buffer.isCompleted()) {
            storage.delete(buffer.getTxnKey());
            buffer = null;
            bufferKey = null;
        }
    }

    public void release() {
        TransactionMemoryLeakDectorManager.getInstance().unWatch(this);
        releaseBuffer();
    }

    @Override
    public boolean isComplete() {
        return isCommit() || isRollback();
    }

    public long getStartTime() {
        return startTime;
    }

    public String getStartSchema() {
        return startSchema;
    }

    public boolean isHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(boolean heartbeat) {
        this.heartbeat = heartbeat;
    }

    public boolean isDDL() {
        return ddlEvent != null;
    }

    public DDLEvent getDdlEvent() {
        return ddlEvent;
    }

    public void setDdlEvent(DDLEvent ddlEvent) {
        this.ddlEvent = ddlEvent;
    }

    public void setTsoTransaction() {
        this.tsoTransaction = true;
    }

    public byte[] getDescriptionLogEventData() {
        AutoExpandBuffer data = new AutoExpandBuffer(1024, 1024);
        try {
            int len = fde.write(data);
            byte[] output = new byte[len];
            System.arraycopy(data.toBytes(), 0, output, 0, len);
            return output;
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    public long getStartLogPos() {
        return startLogPos;
    }

    public TRANSACTION_STATE getState() {
        return state;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return "Transaction {" + "tso = '" + virtualTSO + '\'' + ", xid = '" + xid + '\'' + ", transactionId = '"
            + transactionId + '\'' + ", eventCount = " + getEventCount() + ", partitionId = '" + partitionId + '\''
            + ", txGlobal = " + txGlobal + ", txGlobalTso = '" + txGlobalTso + '\'' + ", txGlobalTid = '" + txGlobalTid
            + '\'' + ", xa = " + xa + ", tsoTransaction = " + tsoTransaction + ", heartbeat = " + heartbeat
            + ", hasBuffer = " + (buffer != null) + ", startLogPos = " + binlogFileName + ":" + startLogPos
            + ", stopLogPos = " + stopLogPos + ", virtualTsoModel = " + virtualTSOModel + '}'
            + sb.toString();
    }

    @Override
    public int compareTo(Transaction o) {
        return virtualTSOModel.compareTo(o.virtualTSOModel);
    }

    public String getInstructionContent() {
        return instructionContent;
    }

    public String getInstrunctionCommitKey() {
        return storageInstanceId + ":" + instructionType + ":" + instructionContent;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public boolean isInstructionCommand() {
        return instructionType != null;
    }

    public boolean isStorageChangeCommand() {
        return instructionType == InstructionType.StorageInstChange;
    }

    public boolean isCDCStartCommand() {
        return instructionType == InstructionType.CdcStart;
    }

    public Long getSnapshotSeq() {
        return snapshotSeq;
    }

    public void setSnapshotSeq(Long snapshotSeq) {
        this.snapshotSeq = snapshotSeq;
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public String getSourceCdcSchema() {
        return sourceCdcSchema;
    }

    public FormatDescriptionLogEvent getFdle() {
        return fdle;
    }

    public void markBufferComplete() {
        if (buffer != null && buffer.itemSize() > 0) {
            buffer.markComplete();
        }
    }

    @Override
    public Iterator<TxnItemRef> iterator() {
        if (buffer == null) {
            return null;
        }
        return buffer.iterator();
    }

    public static enum TRANSACTION_STATE {
        STATE_START, STATE_PREPARE, STATE_COMMIT, STATE_ROLLBACK
    }

}
