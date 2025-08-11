/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
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
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.RegexUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider.LOGIC_SCHEMA;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * 位点的搜索要求：
 * 二分查找， 直接扫描最大和最小tso
 * 只支持search request tso，因为local binlog search很快， 所以只支持搜索oss文件
 */
public class BinarySearchTsoEventHandle implements ISearchTsoEventHandle {

    private static final Logger logger = LoggerFactory.getLogger(BinarySearchTsoEventHandle.class);
    private boolean interrupt = false;
    private BinlogPosition endPosition;
    private String currentFile;
    private long minTSO = -1;
    private long maxTSO = -1;
    private final SortTranMap unCompleteTranMap = new SortTranMap();
    private BinlogPosition preSelectedPos;
    private BinlogPosition selectedPos;
    private long lastTso;
    private final long searchTso;
    private final String requestTso;
    private String preSelectedXid;
    private long preSelectedTso;
    private boolean find = false;
    private boolean lockPos = false;
    private final Map<String, Long> lossStartMap = new HashMap<>();
    private final AuthenticationInfo authenticationInfo;

    public BinarySearchTsoEventHandle(long searchTso, String requestTso, AuthenticationInfo authenticationInfo) {
        this.searchTso = searchTso;
        this.requestTso = requestTso;
        this.authenticationInfo = authenticationInfo;
    }

    @Override
    public boolean interrupt() {
        return interrupt;
    }

    @Override
    public Set<Integer> interestEvents() {
        Set<Integer> flagSet = new HashSet<>();
        flagSet.add(LogEvent.START_EVENT_V3);
        flagSet.add(LogEvent.ROTATE_EVENT);
        flagSet.add(LogEvent.SEQUENCE_EVENT);
        flagSet.add(LogEvent.GCN_EVENT);
        flagSet.add(LogEvent.QUERY_EVENT);
        flagSet.add(LogEvent.FORMAT_DESCRIPTION_EVENT);
        return flagSet;
    }

    @Override
    public void onStart() {
        this.maxTSO = -1;
        this.minTSO = -1;
        this.preSelectedPos = null;
        this.preSelectedXid = null;
        this.lastTso = -1;
        this.interrupt = false;
        this.unCompleteTranMap.clear();
        if (find){
            this.lockPos = true;
        } else {
            this.lossStartMap.clear();
        }
    }


    @Override
    public void onEnd() {

    }

    @Override
    public void setEndPosition(BinlogPosition endPosition) {
        this.endPosition = endPosition;
        this.currentFile = endPosition.getFileName();
    }

    public void processTsoEvent(LogEvent event){
        long seq = -1;
        if (event instanceof SequenceLogEvent){
            SequenceLogEvent sle = (SequenceLogEvent) event;
            if (sle.isCommitSequence()){
                seq = sle.getSequenceNum();
            }
        } else if (event instanceof GcnLogEvent){
            GcnLogEvent gle = (GcnLogEvent) event;
            if (LogEventUtil.isHaveCommitSequence(gle)){
                seq = gle.getGcn();
            }
        }

        if (seq != -1){
            if (minTSO == -1 || minTSO > seq){
                minTSO = seq;
            }
            if (maxTSO == -1 || maxTSO < seq){
                maxTSO = seq;
            }
            if (seq >= searchTso && !find){
                selectedPos = preSelectedPos;
                if (preSelectedXid != null){
                    lossStartMap.put(preSelectedXid, preSelectedTso);
                }
                if (selectedPos != null ||
                    preSelectedXid != null){
                    find = true;
                }
            }
            lastTso = seq;
        }
    }

    public void processXAStartEvent(QueryLogEvent event){
        String xid = LogEventUtil.getXid(event);
        if (StringUtils.isNotEmpty(xid) && LogEventUtil.isValidXid(xid)){
            TranPosition tranPosition = new TranPosition();
            tranPosition.setXid(xid);
            try {
                tranPosition.setTransId(LogEventUtil.getTranIdFromXid(xid, authenticationInfo.getCharset()));
            } catch (Exception e) {
                logger.error("process start event failed! pos : " + currentFile + ":" + event.getLogPos(), e);
                throw new PolardbxException(e);
            }

            tranPosition.setBegin(buildPosition(event));
            if (lockPos){
                Long tso = lossStartMap.remove(xid);
                if (tso != null){
                    tranPosition.setTso(tso);
                    BinlogPosition checkPos = tranPosition.getBegin();
                    checkPos.setRtso(tranPosition.buildRTso());
                    if (selectedPos == null || selectedPos.compareTo(checkPos) > 0){
                        selectedPos = checkPos;
                    }
                }
            }else {
                unCompleteTranMap.add(tranPosition);
            }
        }
    }

    /**
     * 当当前tso < search 时
     * 找到pos 则设置为预选pos
     * 找不到pos， 则设置为预选 xid
     *
     * 当当前tso >= search 时，且找不到start ，说明跨文件了，只做记录
     * @param event
     */
    public void processXACommitEvent(QueryLogEvent event){
        String xid = LogEventUtil.getXid(event);
        if (StringUtils.isNotEmpty(xid) && LogEventUtil.isValidXid(xid)){
            TranPosition tranPosition = unCompleteTranMap.get(xid);
            if (lastTso != -1){
                // last tso 可以 == search tso， 原因是binlog顺序是先tso event -> commit event，
                // 如果tso == search ，会优先判断是否有符合需求的前置事物，没有也不会有找不到位点的问题，比如文件的第一个事物就是searchTso
                if (lastTso <= searchTso){
                    // 没有找到位点， 且 tso <= search tso， 设置预选位点
                    if (!find){
                        if (tranPosition != null){
                            tranPosition.setTso(lastTso);
                            preSelectedPos = unCompleteTranMap.getMinPos(tranPosition.buildRTso());
                        } else {
                            preSelectedXid = xid;
                            preSelectedTso = lastTso;
                        }
                    }
                } else {
                    // tso > search tso 数据不能缺失的数据，跨文件需要记录处理一下
                    if (tranPosition == null){
                        // record lost start
                        lossStartMap.put(xid, lastTso);
                    }
                }
            }
            unCompleteTranMap.remove(xid);
        }
        lastTso = -1;
    }

    public boolean isTsoEvent(int eventType){
        return eventType == LogEvent.SEQUENCE_EVENT ||
            eventType == LogEvent.GCN_EVENT;
    }

    @Override
    public void handle(LogEvent event, LogPosition logPosition) {
        if (endOfFile(logPosition)) {
            // 如果当前文件找到了pos， 则遇到文件尾，可以退出
            logger.info(" finish search binlog : {} result : [{}, {}]", currentFile, minTSO, maxTSO);
            this.interrupt = true;
            return;
        }
        int eventType = event.getHeader().getType();

        // 锁定状态，就不需要处理xa start，只处理空洞commit就好
        if (!lockPos && isTsoEvent(eventType)){
            processTsoEvent(event);
        }

         if (eventType == LogEvent.QUERY_EVENT){
            QueryLogEvent qe = (QueryLogEvent) event;
            if (LogEventUtil.isStart(qe)){
                // xa start 处理
                // 如果是lock 状态， 只处理丢失start 的commit记录， 并与select pos 比较，取最小的位点
                // 如果 非lock状态， 正常记录start
                processXAStartEvent(qe);
            } else if (LogEventUtil.isCommit(qe)){
                if (!lockPos){
                    // xa commit 处理
                    // 如果是lock 状态， 不用记录commit ，否则会死循环
                    // 如果是非lock 状态，正常处理commit
                    processXACommitEvent(qe);
                }
            } else {
                if (!LogEventUtil.isEnd(qe) && !LogEventUtil.isRollback(qe)){
                    checkIfReceiveCreateCdcDbDdl(qe.getQuery(), event, logPosition);
                }
            }
        }

    }

    public void checkIfReceiveCreateCdcDbDdl(String ddl, LogEvent event, LogPosition logPosition){
        try{
            boolean ignore = RegexUtil.match(getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX), ddl);
            if (ignore) {
                if (logger.isDebugEnabled()) {
                    logger.debug("ignore ddl sql in searching stage, ddl is {}, log position is {}",
                        ddl, logPosition);
                }
                return;
            }

            SQLStatement statement = parseSQLStatement(ddl);
            if (statement instanceof SQLCreateDatabaseStatement) {
                SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                String databaseName1 = SQLUtils.normalize(createDatabaseStatement.getDatabaseName());
                String databaseName2 = getCdcPhyDbNameByStorageInstId(authenticationInfo);
                if (StringUtils.equalsIgnoreCase(databaseName1, databaseName2)) {
                    BinlogPosition position = new BinlogPosition(logPosition.getFileName(),
                        event.getLogPos() - event.getEventLen(), event.getServerId(), event.getWhen());
                    position.setRtso(requestTso);
                    this.selectedPos = position;
                    this.find = true;
                    logger.info("receive create sql for cdc physical database, sql content is : {}, will use pos : {}",
                        ddl, position);
                }
            }
        }catch (Exception e){
            logger.error("try parse ddlSql failed, log position : {}, sql content : {}. ",
                event.getLogPos(), ddl, e);
        }

    }

    private String getCdcPhyDbNameByStorageInstId(AuthenticationInfo authenticationInfo) {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = jdbcTemplate.queryForList(String.format(
                "select g.phy_db_name from db_group_info g,group_detail_info d where "
                    + "d.group_name = g.group_name and d.storage_inst_id = '%s' and d.db_name = '%s'",
                authenticationInfo.getStorageMasterInstId(),
                LOGIC_SCHEMA),
            String.class);
        return list.isEmpty() ? "" : list.get(0);
    }

    private BinlogPosition buildPosition(LogEvent event) {
        return new BinlogPosition(currentFile, event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }

    private boolean endOfFile(LogPosition position) {
        return !StringUtils.equals(endPosition.getFileName(), position.getFileName())
            || position.getPosition() >= endPosition.getPosition();
    }

    @Override
    public BinlogPosition getCommandPosition() {
        return null;
    }

    @Override
    public String region() {
        return "[" + minTSO + " , " + maxTSO + "]";
    }

    @Override
    public boolean isInQuickMode() {
        return true;
    }

    @Override
    public String unCompleteTran() {
        return "";
    }

    @Override
    public BinlogPosition searchResult() {
        return selectedPos;
    }

    public boolean needCheckLossStart() {
        return find && !lossStartMap.isEmpty();
    }

    @Override
    public String getTopologyContext() {
        return null;
    }

    @Override
    public String getCommandId() {
        return null;
    }

    @Override
    public String getLastSearchFile() {
        return currentFile;
    }

    public long getMinTSO() {
        return minTSO;
    }

    public long getMaxTSO() {
        return maxTSO;
    }
}
