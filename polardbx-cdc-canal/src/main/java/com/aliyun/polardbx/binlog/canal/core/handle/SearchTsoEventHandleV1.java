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
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoTimestamp;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_SEARCH_TSO_CHECK_PRE_STORAGE_CHANGE;
import static com.aliyun.polardbx.binlog.canal.system.SystemDB.LOGIC_SCHEMA;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.cmdId;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static com.aliyun.polardbx.binlog.scheduler.model.TaskConfig.ORIGIN_TSO;
import static com.aliyun.polardbx.binlog.util.StorageUtil.buildExpectedStorageTso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class SearchTsoEventHandleV1 implements ISearchTsoEventHandle {
    private static final Logger logger = LoggerFactory.getLogger(SearchTsoEventHandleV1.class);

    private final String requestTso;
    private final long searchTSO;
    private final Map<String, TranPosition> tranPositionMap = Maps.newHashMap();
    private final AuthenticationInfo authenticationInfo;
    private final long minTso;
    private final boolean isRequestTso4MarkStorageChange;
    private final boolean isRequestTso4MarkStorageAdd;
    private final long preStorageChangeTso;

    //need reset variables
    private BinlogPosition returnBinlogPosition;
    private BinlogPosition endPosition;
    private String currentFile;
    private long totalSize;
    private long logPos = 4;
    private TranPosition currentTransaction;
    private TranPosition commandTransaction;
    private boolean interrupt = false;
    private long lastTso = -1;
    private long lastGcn = -1L;
    private boolean isReceivedCreateCdcPhyDbEvent;
    private boolean test = false;

    //no need reset variables
    private BinlogPosition firstTsoPositionAfterCreateCdcPhyEvent;
    private long lastPrintTimestamp = System.currentTimeMillis();

    public SearchTsoEventHandleV1(AuthenticationInfo authenticationInfo, String requestTso, long searchTSO,
                                  long minTso) {
        this.authenticationInfo = authenticationInfo;
        this.requestTso = requestTso;
        this.searchTSO = searchTSO;
        this.minTso = minTso;
        this.isRequestTso4MarkStorageChange = checkRequestTsoMark4StorageChange();
        this.isRequestTso4MarkStorageAdd = checkRequestTsoMark4StorageAdd();

        String preStorageChangeVTso = buildExpectedStorageTso(requestTso);
        preStorageChangeTso = preStorageChangeVTso.equals(ORIGIN_TSO) ? -1L : getTsoTimestamp(preStorageChangeVTso);
    }

    @Override
    public boolean interrupt() {
        return interrupt;
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
        reset();
    }

    @Override
    public void onEnd() {

    }

    @Override
    public void setEndPosition(BinlogPosition endPosition) {
        this.endPosition = endPosition;
        this.totalSize = endPosition.getPosition();
        setCurrentFile(endPosition.getFileName());
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    //每次调用onStart之前需执行一下reset
    public void reset() {
        lastGcn = -1;
        lastTso = -1;
        tranPositionMap.clear();
        logPos = 4;
        currentTransaction = null;
        commandTransaction = null;
        returnBinlogPosition = null;
        isReceivedCreateCdcPhyDbEvent = false;
        interrupt = false;
    }

    @Override
    public void handle(LogEvent event, LogPosition logPosition) {
        try {
            search(event, logPosition);
        } catch (Throwable t) {
            if (logPosition != null) {
                String message = String.format("meet fatal error when search tso at position %s:%s",
                    logPosition.getFileName(), logPosition.getPosition());
                throw new CanalParseException(message, t);
            } else {
                throw new CanalParseException(t);
            }
        }
    }

    private void search(LogEvent event, LogPosition logPosition) {
        if (LogEventUtil.isStart(event)) {
            onStart(event, logPosition);
        } else if (LogEventUtil.isPrepare(event)) {
            onPrepare(event, logPosition);
        } else if (LogEventUtil.isCommit(event)) {
            onCommit(event, logPosition);
            onTransactionEndEvent();
        } else if (LogEventUtil.isRollback(event)) {
            onRollback(event, logPosition);
            onTransactionEndEvent();
        } else if (LogEventUtil.isSequenceEvent(event)) {
            onSequence(event);
        } else if (LogEventUtil.isGCNEvent(event)) {
            onGcn(event);
        } else {
            if (currentTransaction != null) {
                currentTransaction.processEvent(event);
                if (currentTransaction.isCdcStartCmd() || currentTransaction.isStorageChangeCmd()) {
                    this.commandTransaction = currentTransaction;
                }
            } else {
                onDdl(event, logPosition);
            }
        }

        if (interrupt) {
            return;
        }

        if (returnBinlogPosition != null) {
            interrupt = true;
            logger.info("returnBinlogPosition is found, stop searching. position info is {}:{}:{}",
                returnBinlogPosition.getFileName(), returnBinlogPosition.getPosition(),
                returnBinlogPosition.getRtso());
            return;
        }

        logPos = event.getLogPos();
        if (logPos >= endPosition.getPosition() || !logPosition.getFileName()
            .equalsIgnoreCase(endPosition.getFileName())) {
            if (isRequestTso4MarkStorageAdd && isReceivedCreateCdcPhyDbEvent
                && firstTsoPositionAfterCreateCdcPhyEvent != null) {
                returnBinlogPosition = firstTsoPositionAfterCreateCdcPhyEvent;
                logger.info("the add storage command for request tso [{}] is lost in this storage, "
                    + "the firstTsoPositionAfterCreateCdcPhyEvent is in the different binlog file with createCdcPhyEvent"
                    + ", will use it ,and its log Position is : {} ", requestTso, returnBinlogPosition);
            }
            lastTso = -1L;
            interrupt = true;
            logger.warn("reach end logPosition：" + logPosition + ", endPos : " + endPosition + " , logPos:" + logPos);
            return;
        }
        printProcess(logPos, logPosition.getFileName());
    }

    private boolean checkCommitSequence(long sequence) {
        lastTso = sequence;
        if (justDetectStorageAddCommandNotFound()) {
            return false;
        }
        if (searchTSO > 0 && lastTso > searchTSO && isCmdTxnNullOrCompleted()) {
            interrupt = true;
            logger.info("search tso " + searchTSO + " is less than last tso " + lastTso + ", stop searching.");
            return true;
        }
        return false;
    }

    private void onTransactionEndEvent() {
        currentTransaction = null;
        lastTso = -1;
        lastGcn = -1L;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    private boolean isCmdTxnNullOrCompleted() {
        return commandTransaction == null || commandTransaction.isComplete();
    }

    private void onStart(LogEvent event, LogPosition logPosition) {
        lastGcn = -1L;
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
        if (LogEventUtil.containsCommitGCN(event) && checkCommitSequence(((QueryLogEvent) event).getCommitGCN())) {
            return;
        }
        if (lastGcn != -1L && checkCommitSequence(lastGcn)) {
            return;
        }

        String xid = LogEventUtil.getXid(event);
        TranPosition tranPosition;
        if (xid != null) {
            tranPosition = tranPositionMap.get(xid);
            tranPositionMap.remove(xid);
        } else {
            tranPosition = currentTransaction;
        }

        if (tranPosition != null) {
            tranPosition.setEnd(buildPosition(event, logPosition));
            tranPosition.setTso(lastTso);
            if (minTso > 0 && lastTso < minTso) {
                return;
            }
            if (justDetectStorageAddCommandNotFound()) {
                processStorageAddCommandNotFound(tranPosition);
                return;
            }
            if (justDetectStorageChangeCommandHasFound()) {
                logger.info("found storage change command transaction for request tso " + requestTso);
                returnBinlogPosition = commandTransaction.getPosition();
                return;
            }
            if (returnBinlogPosition == null && lastTso > 0 && lastTso < searchTSO
                && checkPreStorageChange(lastTso)) {
                logger.info("found returnBinlogPosition which is less than request tso : " + requestTso);
                returnBinlogPosition = tranPosition.getPosition();
            }
            if (searchTSO == -1 && tranPosition.isCdcStartCmd()) {
                logger.info("found returnBinlogPosition which is start command.");
                returnBinlogPosition = tranPosition.getPosition();
            }
        }
    }

    private boolean checkPreStorageChange(long lastTso) {
        if (test) {
            return true;
        }
        boolean needCheck = DynamicApplicationConfig.getBoolean(TASK_SEARCH_TSO_CHECK_PRE_STORAGE_CHANGE);
        if (needCheck) {
            return lastTso >= preStorageChangeTso;
        }
        return true;
    }

    private void onRollback(LogEvent event, LogPosition logPosition) {
        String xid = LogEventUtil.getXid(event);
        if (xid != null) {
            tranPositionMap.remove(xid);
        }
    }

    private void onSequence(LogEvent event) {
        SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
        if (sequenceLogEvent.isCommitSequence()) {
            checkCommitSequence(sequenceLogEvent.getSequenceNum());
        }
    }

    private void onGcn(LogEvent event) {
        GcnLogEvent gcnLogEvent = (GcnLogEvent) event;
        lastGcn = gcnLogEvent.getGcn();
    }

    private void onDdl(LogEvent event, LogPosition logPosition) {
        //DN8.0 V2版本ddl也有记录tso，需要进行重置
        if (event.getHeader().getType() == LogEvent.QUERY_EVENT) {
            lastGcn = -1L;

            if (isRequestTso4MarkStorageAdd) {
                QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                String ddlSql = queryLogEvent.getQuery();
                try {
                    SQLStatementParser parser =
                        SQLParserUtils.createSQLStatementParser(ddlSql, DbType.mysql, FastSQLConstant.FEATURES);
                    List<SQLStatement> statementList = parser.parseStatementList();
                    SQLStatement statement = statementList.get(0);

                    if (statement instanceof SQLCreateDatabaseStatement) {
                        SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                        String databaseName1 = SQLUtils.normalize(createDatabaseStatement.getDatabaseName());
                        String databaseName2 = getCdcPhyDbNameByStorageInstId();
                        if (StringUtils.equalsIgnoreCase(databaseName1, databaseName2)) {
                            isReceivedCreateCdcPhyDbEvent = true;
                            logger.info("receive create sql for cdc physical database, sql content is : " + ddlSql);
                        }
                    }
                } catch (Throwable t) {
                    logger.warn("try parse ddlSql failed : " + ddlSql);
                }
            }
        }
    }

    private boolean checkRequestTsoMark4StorageChange() {
        if (StringUtils.isNotBlank(requestTso)) {
            StorageHistoryInfoMapper storageHistoryMapper =
                SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
            Optional<StorageHistoryInfo> optional =
                storageHistoryMapper.selectOne(s -> s.where(tso, isEqualTo(requestTso)));
            if (optional.isPresent()) {
                logger.warn("request tso is a tso for marking storage change, " + requestTso);
                return true;
            }
        }
        return false;
    }

    private boolean checkRequestTsoMark4StorageAdd() {
        if (StringUtils.isNotBlank(requestTso)) {
            StorageHistoryInfoMapper storageHistoryMapper =
                SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
            Optional<StorageHistoryInfo> optional1 =
                storageHistoryMapper.selectOne(s -> s.where(tso, isEqualTo(requestTso)));

            if (optional1.isPresent()) {
                BinlogPolarxCommandMapper commandMapper =
                    SpringContextHolder.getObject(BinlogPolarxCommandMapper.class);
                Optional<BinlogPolarxCommand> optional2 =
                    commandMapper.selectOne(c -> c.where(cmdId, isEqualTo(optional1.get().getInstructionId())));
                if (optional2.isPresent() && StringUtils.equals("ADD_STORAGE", optional2.get().getCmdType())) {
                    logger.warn("request tso is a tso for marking storage add, " + requestTso);
                    return true;
                }
            }
        }
        return false;
    }

    private String getCdcPhyDbNameByStorageInstId() {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = jdbcTemplate.queryForList(String.format(
            "select g.phy_db_name from db_group_info g,group_detail_info d where "
                + "d.group_name = g.group_name and d.inst_id = '%s' and d.storage_inst_id = '%s' and d.db_name = '%s'",
            DynamicApplicationConfig.getString(POLARX_INST_ID), authenticationInfo.getStorageInstId(), LOGIC_SCHEMA),
            String.class);
        return list.isEmpty() ? "" : list.get(0);
    }

    // 早期的CN内核，CdcManager在处理ScaleOut时有并发问题，可能会出现新增的DN节点上缺失Storage打标信息的情况，此处做容错处理
    // 如果searchTso是打标操作对应的Tso，假如没有找到小于searchTso的tso，则从大于等于searchTso的第一个tso开始消费
    private boolean justDetectStorageAddCommandNotFound() {
        return isRequestTso4MarkStorageAdd && lastTso >= searchTSO && (commandTransaction == null || !commandTransaction
            .isStorageChangeCmd() || (commandTransaction.isStorageChangeCmd() && commandTransaction.isComplete()
            && commandTransaction.getTso() != searchTSO));
    }

    private boolean justDetectStorageChangeCommandHasFound() {
        return isRequestTso4MarkStorageChange && commandTransaction != null && commandTransaction.isStorageChangeCmd()
            && commandTransaction.isComplete() && commandTransaction.getTso() == searchTSO;
    }

    private void processStorageAddCommandNotFound(TranPosition tranPosition) {
        firstTsoPositionAfterCreateCdcPhyEvent = tranPosition.getPosition();
        if (isReceivedCreateCdcPhyDbEvent) {
            returnBinlogPosition = tranPosition.getPosition();
            logger.info("the add storage command for request tso [{}] is lost in this storage,"
                + " the firstTsoPositionAfterCreateCdcPhyEvent is in the same binlog file with createCdcPhyEvent,"
                + " will use it, and its log Position is {} ", requestTso, returnBinlogPosition);
        } else {
            logger.info("the add storage command for request tso [{}] is not found in this file [{}], with lastTso [{}]"
                + " ,stop searching.", requestTso, currentFile, lastTso);
            interrupt = true;
        }
    }

    private void printProcess(long logPos, String fileName) {
        long now = System.currentTimeMillis();
        if (now - lastPrintTimestamp > 5000L) {
            logger.info(" search pos progress : " + (logPos * 100 / totalSize) + "% : " + fileName);
            lastPrintTimestamp = now;
        }
    }

    @Override
    public BinlogPosition getCommandPosition() {
        return commandTransaction != null ? commandTransaction.getPosition() : null;
    }

    @Override
    public String region() {
        return "";
    }

    @Override
    public BinlogPosition searchResult() {
        return returnBinlogPosition;
    }

    @Override
    public String getTopologyContext() {
        return commandTransaction != null ? commandTransaction.getContent() : null;
    }

    @Override
    public String getLastSearchFile() {
        return currentFile;
    }
}
