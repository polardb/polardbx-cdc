/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.LabEventManager;
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
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.IXaTransaction;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.canal.system.TxGlobalEvent;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.domain.po.CdcSyncPointMeta;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.TransactionMemoryLeakDetectorManager;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.service.CdcSyncPointMetaService;
import com.aliyun.polardbx.binlog.storage.AlreadyExistException;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.StorageFactory;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXN_ENTITY_ENABLED;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_DDL_ID;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_DDL_SQL;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_EXT;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_JOB_ID;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_META_INFO;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_SCHEMA_NAME;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_SQL_KIND;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_TABLE_NAME;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.DDL_RECORD_FIELD_VISIBILITY;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.INSTRUCTION_FIELD_INSTRUCTION_CONTENT;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.INSTRUCTION_FIELD_INSTRUCTION_ID;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.INSTRUCTION_FIELD_INSTRUCTION_TYPE;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.POLARX_SYNC_POINT_RECORD_FIELD_ID;
import static com.aliyun.polardbx.binlog.extractor.log.TxnKeyBuilder.buildTxnKey;
import static com.aliyun.polardbx.binlog.extractor.log.TxnKeyBuilder.getTransIdGroupIdPair;

/**
 * 只输出 全局有序唯一TSO，下游合并自己去做 真实TSO+Xid 合并
 *
 * @author chengjin.lyf on 2020/7/17 5:55 下午
 * @since 1.0.25
 */
public class Transaction implements HandlerEvent, IXaTransaction<Transaction> {
    public static final AtomicLong CURRENT_TRANSACTION_COUNT = new AtomicLong(0);
    public static final AtomicLong CURRENT_TRANSACTION_PERSISTED_COUNT = new AtomicLong(0);

    private static final Logger duplicateTransactionLogger = LoggerFactory.getLogger("duplicateTransactionLogger");
    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);

    private static final String ENCODING = "UTF-8";
    private static final String ZERO_19_PADDING = StringUtils.leftPad("0", 10, "0");
    private static final String ENTITY_KEY_PREFIX = "TRANS_ENTITY_";
    private static final AtomicLong ENTITY_KEY_SEQUENCE = new AtomicLong(0);
    private static final ThreadLocal<TransactionCommitListener> COMMIT_LISTENER = new ThreadLocal<>();

    private TxnBuffer txnBuffer;
    private DDLEvent ddlEvent;
    private VirtualTSO virtualTsoModel;
    private Transaction.TRANSACTION_STATE state = Transaction.TRANSACTION_STATE.STATE_START;
    private FormatDescriptionEvent fde;
    private FormatDescriptionLogEvent fdLogEvent;

    private TransEntity entity = new TransEntity();
    private volatile boolean entityPersisted = false;
    private long entityPersistKey;

    public Transaction(FormatDescriptionLogEvent fdLogEvent, FormatDescriptionEvent fde, RuntimeContext rc) {
        Pair<Long, String> pair = getTransIdGroupIdPair();
        this.fde = fde;
        this.fdLogEvent = fdLogEvent;
        this.getEntity().descriptionEvent = true;
        this.getEntity().transactionId = pair.getKey();
        this.getEntity().xid = getEntity().transactionId + rc.getStorageInstId() + "00000-FDE";
        this.getEntity().binlogFileName = rc.getBinlogFile();
        this.getEntity().startLogPos = 0;
        this.getEntity().txnKey = buildTxnKey(rc.getStorageHashCode(), pair);
        TransactionMemoryLeakDetectorManager.getInstance().watch(this);
        CURRENT_TRANSACTION_COUNT.incrementAndGet();
    }

    public Transaction(QueryLogEvent qwe, RuntimeContext rc) throws AlreadyExistException {
        Pair<Long, String> pair = getTransIdGroupIdPair();
        this.getEntity().transactionId = Math.abs(CommonUtils.randomXid());
        this.getEntity().xid = getEntity().transactionId + rc.getStorageInstId() + qwe.getLogPos();
        this.getEntity().binlogFileName = rc.getBinlogFile();
        this.getEntity().startLogPos = qwe.getLogPos();
        this.getEntity().when = qwe.getWhen();
        this.getEntity().txnKey = buildTxnKey(rc.getStorageHashCode(), pair);
        this.buildBuffer();
        qwe.setTrace(generateFakeTraceId());
        this.addTxnBuffer(qwe);
        TransactionMemoryLeakDetectorManager.getInstance().watch(this);
        CURRENT_TRANSACTION_COUNT.incrementAndGet();
    }

    public Transaction(LogEvent logEvent, RuntimeContext rc) throws Exception {
        this.getEntity().xid = LogEventUtil.getXid(logEvent);

        //rewrite charset
        this.getEntity().charset = ENCODING;
        if (logEvent.getHeader().getType() == LogEvent.QUERY_EVENT) {
            QueryLogEvent queryLogEvent = (QueryLogEvent) logEvent;
            if (queryLogEvent.getClientCharset() > 0) {
                this.getEntity().charset = CharsetConversion.getJavaCharset(queryLogEvent.getClientCharset());
            }
        }

        // build transactionId & groupId
        Pair<Long, String> pair = getTransIdGroupIdPair(this.getEntity().xid);
        this.getEntity().transactionId = pair.getKey();
        this.getEntity().groupId = pair.getValue();

        // build transaction info
        if (StringUtils.isNotBlank(this.getEntity().xid)) {
            if (LogEventUtil.isValidXid(getEntity().xid)) {
                // 真实事务id，如果是tso事务，则参与排序，否则不参与排序
                this.getEntity().xa = true;
                this.getEntity().hasRealXid = true;
                this.getEntity().isCdcSingle =
                    SystemDB.isCdcSingleGroup(StringUtils.substringBefore(getEntity().groupId, "@"));
            } else {
                this.getEntity().ignore = true;
            }
        } else {
            this.getEntity().xid = getEntity().transactionId + rc.getStorageInstId() + logEvent.getLogPos();
        }
        this.getEntity().startLogPos = logEvent.getLogPos();
        this.getEntity().binlogFileName = rc.getBinlogFile();
        this.getEntity().when = logEvent.getWhen();
        this.getEntity().txnKey = buildTxnKey(rc.getStorageHashCode(), pair);
        if (!isCdcSingle()) {
            this.buildBuffer();
        }
        TransactionMemoryLeakDetectorManager.getInstance().watch(this);
        CURRENT_TRANSACTION_COUNT.incrementAndGet();
    }

    @SneakyThrows
    public void persistEntity() {
        boolean enablePersistEntity = DynamicApplicationConfig.getBoolean(STORAGE_PERSIST_TXN_ENTITY_ENABLED);

        if (enablePersistEntity && !entityPersisted && isValidStatOfPersistEntity()) {
            entityPersistKey = ENTITY_KEY_SEQUENCE.incrementAndGet();
            byte[] key = buildPersistKey();
            StorageFactory.getStorage().getRepository().selectUnit(entityPersistKey).put(key, entity.serialize());
            entity = null;
            entityPersisted = true;
            CURRENT_TRANSACTION_PERSISTED_COUNT.incrementAndGet();

            if (txnBuffer != null) {
                txnBuffer.persistEntity();
            }
        }
    }

    @SneakyThrows
    public void restoreEntity() {
        if (entityPersisted) {
            byte[] key = buildPersistKey();
            byte[] value = StorageFactory.getStorage().getRepository().selectUnit(entityPersistKey).get(key);
            entity = TransEntity.deserialize(value);
            entityPersisted = false;
            deleteEntity(key);

            if (txnBuffer != null) {
                txnBuffer.restoreEntity();
            }
        }
    }

    private void deleteEntity(byte[] key) throws RocksDBException {
        StorageFactory.getStorage().getRepository().selectUnit(entityPersistKey).delete(key);
        CURRENT_TRANSACTION_PERSISTED_COUNT.decrementAndGet();
    }

    private boolean isValidStatOfPersistEntity() {
        return state == TRANSACTION_STATE.STATE_COMMIT || state == TRANSACTION_STATE.STATE_ROLLBACK;
    }

    private byte[] buildPersistKey() {
        return ByteUtil.bytes(ENTITY_KEY_PREFIX + StringUtils.leftPad(String.valueOf(entityPersistKey), 19, "0"));
    }

    private TransEntity getEntity() {
        if (entityPersisted) {
            LabEventManager.logEvent(LabEventType.TASK_TRANSACTION_PERSIST_ERROR, CommonUtils.getCurrentStackTrace());
            throw new PolardbxException("trans entity is persisted, should restore first!");
        }
        return entity;
    }

    public boolean isArchive() {
        return LogEventUtil.isArchiveXid(getEntity().xid);
    }

    void buildBuffer() throws AlreadyExistException {
        if (TransactionFilter.shouldFilter(getEntity().txnKey, getEntity().binlogFileName, getEntity().startLogPos)) {
            getEntity().ignore = true;
        }

        if (getEntity().ignore) {
            return;
        }

        try {
            txnBuffer = StorageFactory.getStorage().create(getEntity().txnKey);
        } catch (AlreadyExistException e) {
            if (!DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_SKIP_DUPLICATE_TXN_KEY)) {
                throw e;
            }
            duplicateTransactionLogger.warn(
                "ignore duplicate txnKey Exception, will skip it , " + "{ tranId : " + getEntity().transactionId
                    + " , partitionId : " + getEntity().txnKey.getPartitionId() + ", binlogFile: "
                    + getEntity().binlogFileName + ", logPos: " + getEntity().startLogPos + ", xid: " + getEntity().xid
                    + " } ");
            txnBuffer = null;
            getEntity().ignore = true;
        }
    }

    public boolean isIgnore() {
        return getEntity().ignore;
    }

    public boolean isCdcSingle() {
        return getEntity().isCdcSingle;
    }

    public void setListener(TransactionCommitListener listener) {
        COMMIT_LISTENER.set(listener);
    }

    public void processEvent(LogEvent event, RuntimeContext rc) throws Exception {
        processEvent_0(event, rc);
    }

    private void processEvent_0(LogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        if (getEntity().ignore) {
            return;
        }

        if (LogEventUtil.isRowsQueryEvent(event)) {
            RowsQueryLogEvent queryLogEvent = (RowsQueryLogEvent) event;
            try {
                getEntity().originalTraceId = queryLogEvent.getRowsQuery();
                String[] results = LogEventUtil.buildTrace(queryLogEvent);
                if (results != null) {
                    getEntity().nextTraceId = results[0];
                    if (NumberUtils.isCreatable(results[1])) {
                        getEntity().serverId = NumberUtils.createLong(results[1]);
                    }
                }
            } catch (Exception e) {
                logger.error("parser trace error " + queryLogEvent.getRowsQuery(), e);
                throw e;
            }
            // 暂存Rows_Query_Event的内容，放到相邻的下一个Table_Map_Event中
            getEntity().lastRowsQuery = queryLogEvent.getRowsQuery();
            return;
        }
        if (filter(event)) {
            return;
        }
        if (processSpecialTableData(event, rc)) {
            return;
        }
        getEntity().descriptionEvent = event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT;
        if (event.getHeader().getType() == LogEvent.TABLE_MAP_EVENT) {
            // 如果nextTraceId为null，说明binlog_rows_query_log_events参数值为OFF，或者出现了两个连个连续的TableMapEvent
            // 否则物理binlog中肯定会有traceId
            if (getEntity().nextTraceId == null) {
                getEntity().nextTraceId =
                    getEntity().lastTraceId != null ? getEntity().lastTraceId : generateFakeTraceId();
            }
            event.setTrace(getEntity().nextTraceId);
            getEntity().lastTraceId = getEntity().nextTraceId;
            getEntity().nextTraceId = null;
        } else {
            //直接使用前面紧邻的TableMapEvent的TraceId
            event.setTrace(getEntity().lastTraceId);
        }
        if (getEntity().serverId != null) {
            event.setTraceServerId(getEntity().serverId);
        }
        addTxnBuffer(event);
    }

    void addTxnBuffer(LogEvent logEvent) {
        if (txnBuffer == null) {
            return;
        }

        if (StringUtils.isNotBlank(getEntity().lastRowsQuery) && !getEntity().lastRowsQuery.endsWith("*/")) {
            getEntity().lastRowsQuery = StringUtils.substringBetween(getEntity().lastRowsQuery, "/*DRDS", "*/");
            getEntity().lastRowsQuery = "/*DRDS" + getEntity().lastRowsQuery + "*/";
        }
        TxnBufferItem txnItem =
            TxnBufferItem.builder().traceId(logEvent.getTrace()).rowsQuery(getEntity().lastRowsQuery)
                .payload(logEvent.toBytes()).eventType(logEvent.getHeader().getType())
                .originTraceId(getEntity().originalTraceId).binlogFile(getEntity().binlogFileName)
                .binlogPosition(logEvent.getLogPos()).build();

        // RowsQuery Event后跟紧Table_Map，所以第一个lastRowsQuery不为空
        // 这里将lastRowsQuery设为空之后，后面的Table_Map中的rowsQuery将为空，直到获得下一个RowsQuery中的内容
        getEntity().lastRowsQuery = "";
        try {
            txnBuffer.push(txnItem);
        } catch (Exception e) {
            logger.error("push to buffer error local trace : " + getEntity().originalTraceId + " with : "
                + getEntity().binlogFileName + ":" + logEvent.getLogPos() + " buffer : " + txnBuffer.getTxnKey() + " "
                + txnBuffer.itemSize(), e);
            logger.error("buffer cache:");
            Iterator<TxnItemRef> it = txnBuffer.iterator();
            while (it.hasNext()) {
                TxnItemRef ii = it.next();
                logger.error("item type : " + ii.getEventType() + " .. trace : " + ii.getTraceId());
            }
            throw e;
        }
    }

    public Long getServerId() {
        return getEntity().serverId;
    }

    private boolean processSpecialTableData(LogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {

        if (event instanceof RowsLogEvent) {
            RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
            TableMapLogEvent table = rowsLogEvent.getTable();
            if (SystemDB.isGlobalTxTable(table.getTableName())) {
                if (event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                    || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1) {
                    processTxGlobalEvent((WriteRowsLogEvent) rowsLogEvent);
                }
                return true;
            }
            if (SystemDB.isHeartbeat(rowsLogEvent.getTable().getDbName(), rowsLogEvent.getTable().getTableName())) {
                setHeartbeat(true);
                this.getEntity().sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                releaseTxnBuffer();
                return true;
            }
            if (SystemDB.isSyncPoint(rowsLogEvent.getTable().getDbName(), rowsLogEvent.getTable().getTableName())) {
                logger.info("meet sync point table event!");
                if (event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                    || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1) {
                    processSyncPoint((WriteRowsLogEvent) rowsLogEvent, rc);
                }
                releaseTxnBuffer();
                return true;
            }
            if (SystemDB.isLogicDDL(table.getDbName(), table.getTableName())) {
                if (event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                    || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1) {
                    processDDL((WriteRowsLogEvent) rowsLogEvent, rc);
                    this.getEntity().sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                }
                releaseTxnBuffer();
                return true;
            }
            if (SystemDB.isSys(table.getDbName())) {
                if (SystemDB.isInstruction(table.getDbName(), table.getTableName()) && (
                    event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT
                        || event.getHeader().getType() == LogEvent.WRITE_ROWS_EVENT_V1)) {
                    processInstruction((WriteRowsLogEvent) rowsLogEvent, rc);
                }
                releaseTxnBuffer();
                this.getEntity().sourceCdcSchema = rowsLogEvent.getTable().getDbName();
                return true;
            }
        }

        if (event instanceof TableMapLogEvent) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            if (SystemDB.isHeartbeat(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName())) {
                setHeartbeat(true);
                releaseTxnBuffer();
                this.getEntity().sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
            if (SystemDB.isInstruction(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName())) {
                releaseTxnBuffer();
                this.getEntity().sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
            if (SystemDB.isSys(tableMapLogEvent.getDbName())) {
                releaseTxnBuffer();
                this.getEntity().sourceCdcSchema = tableMapLogEvent.getDbName();
                return true;
            }
        }

        return false;
    }

    private void processInstruction(WriteRowsLogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        BinlogParser binlogParser = new BinlogParser();
        binlogParser.parse(SystemDB.getInstructionTableMeta(), event, "utf8");
        String instructionType = (String) binlogParser.getField(INSTRUCTION_FIELD_INSTRUCTION_TYPE);
        String instructionContent = (String) binlogParser.getField(INSTRUCTION_FIELD_INSTRUCTION_CONTENT);
        String instructionId = (String) binlogParser.getField(INSTRUCTION_FIELD_INSTRUCTION_ID);
        this.getEntity().instructionType = InstructionType.valueOf(instructionType);
        this.getEntity().instructionContent = instructionContent;
        this.getEntity().instructionId = instructionId;
    }

    private void processFlushLog() {
        this.getEntity().instructionType = InstructionType.FlushLogs;
    }

    private boolean processCdcInternalDDL(DDLRecord ddlRecord) {
        // prepare parameters

        String sqlKind = ddlRecord.getSqlKind();
        if (StringUtils.equals(sqlKind, "FLUSH_LOGS")) {
            String groupName = ddlRecord.getExtInfo().getGroupName();
            if (StringUtils.isNotBlank(groupName)) {
                // check 指令是否是针对当前集群
                if (StringUtils.equalsIgnoreCase(
                    DynamicApplicationConfig.getString(ConfigKeys.BINLOGX_STREAM_GROUP_NAME), groupName)) {
                    processFlushLog();
                    return true;
                }

            } else if (DynamicApplicationConfig.getClusterType().equals(ClusterType.BINLOG.name())) {
                processFlushLog();
                return true;
            }
            return true;
        }
        return false;
    }

    private void processDDL(WriteRowsLogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        BinlogParser binlogParser = new BinlogParser();
        binlogParser.parse(SystemDB.getDdlTableMeta(), event, "utf8");
        String id = (String) binlogParser.getField(DDL_RECORD_FIELD_DDL_ID);
        String jobId = (String) binlogParser.getField(DDL_RECORD_FIELD_JOB_ID);
        String ddl = (String) binlogParser.getField(DDL_RECORD_FIELD_DDL_SQL);
        String metaInfo = (String) binlogParser.getField(DDL_RECORD_FIELD_META_INFO);
        String sqlKind = (String) binlogParser.getField(DDL_RECORD_FIELD_SQL_KIND);
        String logicSchema = (String) binlogParser.getField(DDL_RECORD_FIELD_SCHEMA_NAME);
        String tableName = (String) binlogParser.getField(DDL_RECORD_FIELD_TABLE_NAME);
        int visibility = Integer.parseInt((String) binlogParser.getField(DDL_RECORD_FIELD_VISIBILITY));
        String ext = (String) binlogParser.getField(DDL_RECORD_FIELD_EXT);

        DDLEvent ddlEvent =
            DDLEventBuilder.build(id, jobId, logicSchema, tableName, sqlKind, ddl, visibility, ext, metaInfo);
        ddlEvent.setPosition(
            new BinlogPosition(rc.getBinlogFile(), event.getLogPos(), -1, event.getHeader().getWhen()));

        Long serverId = ddlEvent.getServerId();
        if (serverId != null) {
            this.getEntity().serverId = serverId;
        }

        if (processCdcInternalDDL(ddlEvent.getDdlRecord())) {
            return;
        }

        this.ddlEvent = ddlEvent;

        if (logger.isDebugEnabled()) {
            logger.debug("receive logic ddl " + JSONObject.toJSONString(ddlEvent.getDdlRecord()));
        }
    }

    private void processSyncPoint(WriteRowsLogEvent event, RuntimeContext rc) throws UnsupportedEncodingException {
        BinlogParser binlogParser = new BinlogParser();
        binlogParser.parse(SystemDB.getSyncPointTableMeta(), event, "utf8");
        String id = (String) binlogParser.getField(POLARX_SYNC_POINT_RECORD_FIELD_ID);
        final CdcSyncPointMetaService service = getObject(CdcSyncPointMetaService.class);
        final long processSyncPointWaitTimeoutMS =
            DynamicApplicationConfig.getLong(ConfigKeys.TASK_PROCESS_SYNC_POINT_WAIT_TIMEOUT_MILLISECOND);
        Optional<CdcSyncPointMeta> record = service.selectById(id);
        if (record.isPresent()) {
            processSyncPointMeta(record.get(), rc);
        } else {
            logger.info("sync point is not present, will wait for a moment");
            try {
                Thread.sleep(processSyncPointWaitTimeoutMS);
            } catch (InterruptedException e) {
            }

            record = service.selectById(id);
            if (record.isPresent()) {
                processSyncPointMeta(record.get(), rc);
            } else {
                logger.info("wait sync point timeout! id:{}", id);
                int affectedRows = service.insertIgnore(id);
                if (affectedRows == 0) { // CN或者其他dispatcher插入了记录
                    record = service.selectById(id);
                    processSyncPointMeta(record.get(), rc);
                } else {
                    logger.info("mark sync point meta as invalid, id:{}", id);
                }
            }
        }
    }

    private void processSyncPointMeta(CdcSyncPointMeta record, RuntimeContext rc) {
        if (record.getValid() == 1) {
            logger.info("sync point is valid, id:{}", record.getId());
            rc.setHoldingTso();
            setSyncPoint(true);
        } else {
            logger.info("sync point is not valid, id:{}", record.getId());
        }
    }

    public void afterCommit(RuntimeContext rc) {
        getEntity().stopLogPos = rc.getLogPos();
        if (isTxGlobal()) {
            if (getEventCount() > 0) {
                // xa事务会有部分分片保存在事务中的情况
                resetTranId(getTxGlobalTid());
            }
        }

        getEntity().virtualTsoStr = generateTSO(rc);
        if (isDDL()) {
            ddlEvent.getPosition().setRtso(getEntity().virtualTsoStr);
        }

        //尽快释放内存空间
        clearTraceId();
    }

    public boolean needRevert() {
        return isRollback() || StringUtils.isEmpty(getEntity().virtualTsoStr) || !isValidTransaction();
    }

    public boolean isVisibleDdl() {
        return isDDL() && ddlEvent.isVisible();
    }

    public boolean isValidTransaction() {
        return getEventCount() > 0 || isDDL() || isHeartbeat() || isDescriptionEvent() || isInstructionCommand()
            || isSyncPoint();
    }

    private boolean isDrdsGlobalTxLogTable(String tableName) {
        return SystemDB.isGlobalTxTable(tableName);
    }

    private boolean isPolarxGlobalTrxLogTable(String tableName) {
        return SystemDB.isPolarxGlobalTrxLogTable(tableName);
    }

    // 对比库，数据导入阶段直接插DN里，没有cts
    private boolean isAndorDatabase(String dbName) {
        return SystemDB.isAndorDatabase(dbName);
    }

    private boolean isDrdsRedoLogTable(String tableName) {
        return SystemDB.isDrdsRedoLogTable(tableName);
    }

    private boolean isMysqlDb(String dbName) {
        return SystemDB.isMysql(dbName);
    }

    private boolean isMetaDb(String dbName) {
        return SystemDB.isMetaDb(dbName);
    }

    private boolean filter(LogEvent event) {
        if (event.getHeader().getType() == LogEvent.TABLE_MAP_EVENT) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            String tableName = tableMapLogEvent.getTableName();
            String dbName = tableMapLogEvent.getDbName();

            setSyncPointCheckIgnoreFlag(dbName, tableName);

            return isDrdsGlobalTxLogTable(tableName) || isDrdsRedoLogTable(tableName)
                || isPolarxGlobalTrxLogTable(tableName);
        }
        if (event instanceof RowsLogEvent) {
            RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
            TableMapLogEvent table = rowsLogEvent.getTable();

            setSyncPointCheckIgnoreFlag(table.getDbName(), table.getDbName());

            return isDrdsRedoLogTable(table.getTableName()) || isPolarxGlobalTrxLogTable(table.getTableName());
        }
        if (event instanceof QueryLogEvent) {
            QueryLogEvent logEvent = (QueryLogEvent) event;
            return StringUtils.startsWithIgnoreCase(logEvent.getQuery(), "savepoint");
        }
        return false;
    }

    private void setSyncPointCheckIgnoreFlag(String dbName, String tbName) {
        getEntity().syncPointCheckIgnoreFlag =
            isMysqlDb(dbName) || isMetaDb(dbName) || isDrdsGlobalTxLogTable(tbName) || isDrdsRedoLogTable(tbName)
                || isPolarxGlobalTrxLogTable(tbName) || isAndorDatabase(dbName);
    }

    private void processTxGlobalEvent(WriteRowsLogEvent rowsLogEvent) {
        TxGlobalEvent txGlobalEvent = SystemDB.parseTxGlobalEvent(rowsLogEvent, getEntity().charset);
        this.getEntity().txGlobalTid = txGlobalEvent.getTxGlobalTid();
        this.getEntity().txGlobalTso = txGlobalEvent.getTxGlobalTso();
        this.getEntity().transactionId = getEntity().txGlobalTid;
        this.getEntity().txGlobal = true;
        this.getEntity().xa = true;
        if (getEntity().txGlobalTso != null && getEntity().txGlobalTso > 0) {
            this.getEntity().tsoTransaction = true;
        }
    }

    public void resetTranId(Long newTransactionId) {
        this.getEntity().transactionId = newTransactionId;
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
                computeTransactionId = getEntity().transactionId;
                currentTso = getEntity().txGlobal ? getEntity().txGlobalTso : getEntity().realTso;
                rc.setMaxTSO(currentTso, getEntity().transactionId);
                uniqueTxnId = StringUtils.leftPad(String.valueOf(getEntity().transactionId), 19, "0") + ZERO_19_PADDING;
            } else {
                // 在sync point的P和C之间的单机事务：
                // 1. real TSO <= holdingTso，采用holdingTso作为real tso，避免binlog中出现TSO乱序
                // 2. real TSO > holdingTso, 采用maxTso作为real tso（和老的逻辑保持一致，依然能够保证事务之间的依赖关系）
                if (rc.inSyncPointTxn() && getEntity().realTso == rc.getHoldingTso()) {
                    currentTso = getEntity().realTso;
                    computeTransactionId = rc.getHoldingTxnId();
                    int nextSeq = rc.getNextHoldingTxnIdSeq(computeTransactionId);
                    uniqueTxnId =
                        StringUtils.leftPad(computeTransactionId + "", 19, "0") + StringUtils.leftPad(nextSeq + "",
                            10, "0");
                } else {
                    long lastTxnId = rc.getMaxTxnId();
                    computeTransactionId = lastTxnId;
                    int nextSeq = rc.nextMaxTxnIdSequence(lastTxnId);
                    uniqueTxnId = StringUtils.leftPad(lastTxnId + "", 19, "0") +
                        StringUtils.leftPad(nextSeq + "", 10, "0");
                }
            }
        }

        if (currentTso == null) {
            throw new PolardbxException("tso should not be null " + this.toString());
        }

        String storageInstId = rc.getStorageInstId();
        if (isDDL() || isDescriptionEvent() || isInstructionCommand() || isTsoTransaction()) {
            storageInstId = null;
        }

        String vto = CommonUtils.generateTSO(currentTso, uniqueTxnId, storageInstId);
        virtualTsoModel = new VirtualTSO(currentTso, computeTransactionId, Integer.parseInt(vto.substring(38, 48)));
        return vto;
    }

    public boolean isStart() {
        return state == TRANSACTION_STATE.STATE_START;
    }

    public boolean isDescriptionEvent() {
        return getEntity().descriptionEvent;
    }

    public boolean isCommit() {
        return state == TRANSACTION_STATE.STATE_COMMIT;
    }

    public void setCommit(RuntimeContext rc) {
        if (isCommit()) {
            throw new PolardbxException("duplicate commit event!");
        }

        this.state = TRANSACTION_STATE.STATE_COMMIT;

        if (isSyncPoint()) {
            rc.resetHoldingTso();
        }

        afterCommit(rc);
        if (isIgnore()) {
            return;
        }
        COMMIT_LISTENER.get().onCommit(this);
    }

    public boolean isPrepare() {
        return state == TRANSACTION_STATE.STATE_PREPARE;
    }

    public String getVirtualTsoStr() {
        return getEntity().virtualTsoStr;
    }

    public void setVirtualTSO(String virtualTSO) {
        this.getEntity().virtualTsoStr = virtualTSO;
    }

    public VirtualTSO getVirtualTSOModel() {
        return virtualTsoModel;
    }

    public void setRealTSO(long realTSO) {
        this.getEntity().realTso = realTSO;
    }

    public boolean hasTso() {
        return StringUtils.isNotBlank(getEntity().virtualTsoStr);
    }

    public boolean isXa() {
        return getEntity().xa;
    }

    public void setXa(boolean xa) {
        this.getEntity().xa = xa;
    }

    public String getXid() {
        return getEntity().xid;
    }

    public void setXid(String xid) {
        this.getEntity().xid = xid;
    }

    public void setStart() {
        this.state = TRANSACTION_STATE.STATE_START;
    }

    public void setPrepare() {
        this.state = TRANSACTION_STATE.STATE_PREPARE;
    }

    public void setCharset(String charset) {
        this.getEntity().charset = charset;
    }

    public Long getTransactionId() {
        return getEntity().txnKey.getTxnId();
    }

    public String getPartitionId() {
        return getEntity().txnKey.getPartitionId();
    }

    public int getEventCount() {
        return txnBuffer == null ? 0 : txnBuffer.itemSize();
    }

    private String generateFakeTraceId() {
        return LogEventUtil.buildTraceId(getEventCount() + "", null);
    }

    public boolean isTxGlobal() {
        return getEntity().txGlobal;
    }

    public Long getTxGlobalTso() {
        return getEntity().txGlobalTso;
    }

    public Long getTxGlobalTid() {
        return getEntity().txGlobalTid;
    }

    public boolean isTsoTransaction() {
        return getEntity().tsoTransaction;
    }

    public void setTsoTransaction(boolean tsoTransaction) {
        this.getEntity().tsoTransaction = tsoTransaction;
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
        releaseTxnBuffer();
        if (isIgnore()) {
            return;
        }
        COMMIT_LISTENER.get().onCommit(this);
    }

    private void releaseTxnBuffer() {
        if (txnBuffer != null && !txnBuffer.isCompleted()) {
            StorageFactory.getStorage().delete(txnBuffer.getTxnKey());
            txnBuffer.deleteEntity();
            txnBuffer = null;
        }
    }

    private void clearTraceId() {
        this.getEntity().lastTraceId = null;
        this.getEntity().nextTraceId = null;
        this.getEntity().originalTraceId = null;
        this.getEntity().lastRowsQuery = null;
    }

    @SneakyThrows
    public void release() {
        TransactionMemoryLeakDetectorManager.getInstance().unWatch(this);
        releaseTxnBuffer();
        if (entityPersisted) {
            deleteEntity(buildPersistKey());
        }
        CURRENT_TRANSACTION_COUNT.decrementAndGet();
    }

    @Override
    public boolean isComplete() {
        return isCommit() || isRollback();
    }

    public boolean isHeartbeat() {
        return getEntity().heartbeat;
    }

    public void setHeartbeat(boolean heartbeat) {
        this.getEntity().heartbeat = heartbeat;
    }

    public void setSyncPoint(boolean b) {
        this.getEntity().syncPoint = b;
    }

    public boolean isSyncPoint() {
        return this.getEntity().syncPoint;
    }

    public boolean isSyncPointCheckIgnored() {
        return this.getEntity().syncPointCheckIgnoreFlag;
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
        return getEntity().startLogPos;
    }

    public long getWhen() {
        return getEntity().when;
    }

    public TRANSACTION_STATE getState() {
        return state;
    }

    @Override
    public String toString() {
        restoreEntity();
        return "Transaction {"
            + "  tso = " + getEntity().virtualTsoStr
            + ", xid = " + getEntity().xid
            + ", transactionId = " + getEntity().transactionId
            + ", eventCount = " + getEventCount()
            + ", partitionId = " + getEntity().txnKey.getPartitionId()
            + ", txGlobal = " + getEntity().txGlobal
            + ", txGlobalTso = " + getEntity().txGlobalTso
            + ", txGlobalTid = " + getEntity().txGlobalTid
            + ", xa = " + getEntity().xa
            + ", tsoTransaction = " + getEntity().tsoTransaction
            + ", isHeartbeat = " + getEntity().heartbeat
            + ", isSyncpoint = " + getEntity().syncPoint
            + ", hasBuffer = " + (txnBuffer != null)
            + ", startLogPos = " + getEntity().binlogFileName + ":" + getEntity().startLogPos
            + ", stopLogPos = " + getEntity().stopLogPos
            + ", memSize = " + getMemSize()
            + ", persisted = " + isBufferPersisted()
            + ", isDdl = " + isDDL()
            + ", isStorageChangeCommand = " + isStorageChangeCommand()
            + ", isDescriptionEvent = " + isDescriptionEvent()
            + ", virtualTsoModel = " + virtualTsoModel
            + '}';
    }

    @Override
    public int compareTo(Transaction o) {
        return virtualTsoModel.compareTo(o.virtualTsoModel);
    }

    public String getInstructionContent() {
        return getEntity().instructionContent;
    }

    public String getInstructionId() {
        return getEntity().instructionId;
    }

    public boolean isInstructionCommand() {
        return getEntity().instructionType != null;
    }

    public boolean isStorageChangeCommand() {
        return getEntity().instructionType == InstructionType.StorageInstChange;
    }

    public boolean isCDCStartCommand() {
        return getEntity().instructionType == InstructionType.CdcStart;
    }

    public boolean isMetadataBuildCommand() {
        return getEntity().instructionType == InstructionType.MetaSnapshot;
    }

    public boolean isEnvConfigChangeCommand() {
        return getEntity().instructionType == InstructionType.CdcEnvConfigChange;
    }

    public boolean isFlushLogCommand() {
        return this.getEntity().instructionType == InstructionType.FlushLogs;
    }

    public String getBinlogFileName() {
        return getEntity().binlogFileName;
    }

    public String getSourceCdcSchema() {
        return getEntity().sourceCdcSchema;
    }

    public FormatDescriptionLogEvent getFdLogEvent() {
        return fdLogEvent;
    }

    public TxnKey getTxnKey() {
        return getEntity().txnKey;
    }

    public void persistTxnBuffer() {
        if (txnBuffer != null) {
            txnBuffer.persist();
        }
        if (isValidStatOfPersistEntity()) {
            persistEntity();
        }
        if (!entityPersisted) {
            getEntity().shouldPersist = true;
        }
    }

    public boolean isBufferPersisted() {
        if (txnBuffer != null) {
            return txnBuffer.isPersisted();
        }
        return false;
    }

    public void markBufferComplete() {
        if (txnBuffer != null && txnBuffer.itemSize() > 0) {
            txnBuffer.markComplete();
        }
    }

    public long getMemSize() {
        return txnBuffer != null ? txnBuffer.memSize() : 0;
    }

    public IteratorBuffer iterator() {
        if (txnBuffer == null) {
            return null;
        }
        return txnBuffer.iteratorWrapper();
    }

    public boolean shouldPersistEntity() {
        return getEntity() != null && getEntity().shouldPersist;
    }

    public boolean isLargeTrans() {
        return txnBuffer != null && txnBuffer.isLargeTrans();
    }

    public enum TRANSACTION_STATE {
        STATE_START, STATE_PREPARE, STATE_COMMIT, STATE_ROLLBACK
    }

}
