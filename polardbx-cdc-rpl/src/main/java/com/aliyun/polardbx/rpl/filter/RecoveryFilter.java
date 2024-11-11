/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryApplierConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yudong
 */
@Slf4j
public class RecoveryFilter {
    private static final String hintPrefix = "/*DRDS /";
    private static final String hintEnd = "/ */";
    private static final String hintDivision = "/";
    private static final int hintSplitNum = 5;
    private static final int hintTraceIdIdx = 3;
    private static final String hintTraceIdLogicalSqlSpliter = "-";

    private boolean match = false;

    private final String traceId;

    private final String sqlType;

    private final Timestamp beginTime;

    private final Timestamp endTime;

    private final Set<String> filesToConsume;

    private final String schema;

    private boolean isFuzzySearch = false;

    private boolean isEnd = false;

    private Set<String> tableSet = null;

    private String queryLog = "";

    public RecoveryFilter(RecoveryApplierConfig config) {
        this.filesToConsume = new HashSet<>(config.getBinlogList());
        this.traceId = config.getTraceId();
        this.beginTime = new Timestamp(config.getBeginTime());
        this.endTime = new Timestamp(config.getEndTime());
        this.sqlType = config.getSqlType();
        this.schema = config.getSchema();
        if (StringUtils.isNotEmpty(config.getTable())) {
            String[] tableList = config.getTable().split(",");
            tableSet = new HashSet<>();
            for (String tableName : tableList) {
                tableSet.add(tableName.toLowerCase());
            }
        }

        if (StringUtils.isEmpty(traceId)) {
            isFuzzySearch = true;
            log.info("TraceId is empty, change to fuzzy search");
        }

    }

    public DefaultRowChange convert(DBMSEvent event) {
        if (event == null) {
            return null;
        }

        if (filterByBeginTimestamp(event)) {
            if (log.isDebugEnabled()) {
                log.debug("skip event {}, which timestamp is less than begin time {}.", event.getPosition(), beginTime);
            }
            return null;
        }

        if (isEnd) {
            return generateEndFlag();
        }

        if (!filterByEndTimestamp(event) || !filterByFileName(event)) {
            isEnd = true;
            return generateEndFlag();
        }

        if (event instanceof DefaultRowsQueryLog) {
            if (!isFuzzySearch) {
                this.queryLog = ((DefaultRowsQueryLog) event).getRowsQuery();
                String traceId = extractTraceId(queryLog);
                match = matchTraceId(traceId, this.traceId);
                log.info("match: " + match);
            }
        } else if (event instanceof DefaultRowChange) {
            if (!filterBySchemaAndTable((DefaultRowChange) event)) {
                return null;
            }

            if (isFuzzySearch) {
                if (filterBySqlType((DefaultRowChange) event)) {
                    return fillFuzzyOption((DefaultRowChange) event);
                }
            } else {
                if (match) {
                    return fillFuzzyOption((DefaultRowChange) event);
                }
            }
        }
        return null;
    }

    private boolean filterByBeginTimestamp(DBMSEvent event) {
        Timestamp timestamp = getEventTimestamp(event);
        return timestamp.compareTo(beginTime) < 0;
    }

    /**
     * Return whether event is after endTime.
     */
    private boolean filterByEndTimestamp(DBMSEvent event) {
        Timestamp timestamp = getEventTimestamp(event);
        boolean result = timestamp.compareTo(endTime) <= 0;
        if (log.isDebugEnabled()) {
            log.debug("reach end event {}, which timestamp is larger than end time {}.", event.getPosition(), endTime);
        }
        return result;
    }

    private boolean filterByFileName(DBMSEvent event) {
        String position = event.getPosition();
        String fileName = CommonUtil.parsePosition(position).get(0);
        boolean result = filesToConsume.contains(fileName);
        if (log.isDebugEnabled()) {
            log.debug("reach end event {}, which fileName is not in filesToConsume {}.", event.getPosition(),
                JSONObject.toJSONString(filesToConsume));
        }
        return result;
    }

    private boolean filterBySchemaAndTable(DefaultRowChange event) {
        String table = event.getTable().toLowerCase();
        return this.schema.equalsIgnoreCase(event.getSchema()) &&
            (tableSet == null || tableSet.contains(table));
    }

    private boolean filterBySqlType(DefaultRowChange rowChange) {
        return StringUtils.isEmpty(sqlType) || sqlType.contains(rowChange.getAction().name());
    }

    // hints format: /*DRDS /client ip/trace id/physical sql id/server id/ */
    // example: /*DRDS /127.0.0.1/12fb0789b6000000/0// */
    // 如果一个事务中有多条SQL，则对应trace-id中有小序号-1, -2, -3...
    private static String extractTraceId(String query) {
        String res;
        if (!query.startsWith(hintPrefix)) {
            log.info("This is not a drds hint, query: " + query);
            return null;
        }
        int hintEndIdx = query.indexOf(hintEnd);
        if (hintEndIdx == -1) {
            log.error("Not found hint end, query: " + query);
            return null;
        }

        String hint = query.substring(0, hintEndIdx);
        String[] splits = hint.split(hintDivision);
        if (splits.length != hintSplitNum) {
            log.error("Unexpected hint split num, hint " + hint);
            return null;
        }

        res = splits[hintTraceIdIdx];
        log.info("Find a trace id : " + res);

        return res;
    }

    private static boolean matchTraceId(String fromLog, String fromUser) {
        if (StringUtils.isEmpty(fromUser)) {
            throw new IllegalArgumentException("trace id from user is empty!");
        }
        if (StringUtils.isEmpty(fromLog)) {
            log.info("trace id from log is empty!");
            return false;
        }

        // 精确匹配某个逻辑SQL
        if (fromUser.contains(hintTraceIdLogicalSqlSpliter)) {
            return StringUtils.equals(fromLog, fromUser);
        }

        // 匹配某个事务内所有的逻辑SQL
        String txnId = fromLog;
        if (fromLog.contains(hintTraceIdLogicalSqlSpliter)) {
            String[] splits = fromLog.split(hintTraceIdLogicalSqlSpliter);
            if (splits.length != 2) {
                log.error("Unexpected trace id split num, trace id: " + fromLog);
                return false;
            }
            txnId = splits[0];
        }
        return StringUtils.equals(txnId, fromUser);
    }

    private DefaultRowChange fillFuzzyOption(DefaultRowChange rowChange) {
        if (rowChange == null) {
            return null;
        }
        rowChange.setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_CALLBACK_TYPE,
            isFuzzySearch ? "fuzzy" : "exact");
        rowChange.setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_QUERY_LOG, queryLog);
        return rowChange;
    }

    private DefaultRowChange generateEndFlag() {
        log.info("set end flag");
        DefaultRowChange
            defaultRowChange = new DefaultRowChange(DBMSAction.INSERT, "end_flag", "end_flag", new DefaultColumnSet(
            Collections.EMPTY_LIST), Collections.EMPTY_LIST, null);
        defaultRowChange.setDataSet(Lists.newArrayList());
        defaultRowChange.setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SHOULD_STOP, true);
        defaultRowChange
            .setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_CALLBACK_TYPE, isFuzzySearch ? "fuzzy" : "exact");
        return defaultRowChange;
    }

    private Timestamp getEventTimestamp(DBMSEvent event) {
        return new Timestamp(event.getSourceTimeStamp());
    }
}
