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
package com.aliyun.polardbx.rpl.filter;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
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

    public DBMSRowChange convert(DBMSEvent event) {
        if (event == null) {
            return null;
        }

        if (filterByBeginTimestamp(event)) {
            if (log.isDebugEnabled()) {
                log.debug("skip event {}, which timestamp is less than begin time {}.", getPosition(event), beginTime);
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

        if (!filterByEventType(event)) {
            return null;
        }

        if (event instanceof DBMSQueryLog) {
            if (!isFuzzySearch) {
                String traceId = findTraceId((DBMSQueryLog) event);
                if (traceId != null) {
                    match = traceId.equalsIgnoreCase(this.traceId);
                } else {
                    match = false;
                }

                log.info("match: " + match);
            }
        } else {
            if (!filterBySchemaAndTable((DBMSRowChange) event)) {
                return null;
            }

            if (isFuzzySearch) {
                if (filterBySqlType((DBMSRowChange) event)) {
                    return fillFuzzyOption((DBMSRowChange) event);
                }
            } else {
                if (match) {
                    return fillFuzzyOption((DBMSRowChange) event);
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
            log.debug("reach end event {}, which timestamp is larger than end time {}.", getPosition(event), endTime);
        }
        return result;
    }

    private boolean filterByFileName(DBMSEvent event) {
        String position = getPosition(event);
        String fileName = CommonUtil.parsePosition(position).get(0);
        boolean result = filesToConsume.contains(fileName);
        if (log.isDebugEnabled()) {
            log.debug("reach end event {}, which fileName is not in filesToConsume {}.", getPosition(event),
                JSONObject.toJSONString(filesToConsume));
        }
        return result;
    }

    private boolean filterByEventType(DBMSEvent event) {
        return event instanceof DBMSQueryLog || event instanceof DBMSRowChange;
    }

    private boolean filterBySchemaAndTable(DBMSRowChange event) {
        String table = event.getTable().toLowerCase();
        return this.schema.equalsIgnoreCase(event.getSchema()) &&
            (tableSet == null || tableSet.contains(table));
    }

    private boolean filterBySqlType(DBMSRowChange rowChange) {
        return StringUtils.isEmpty(sqlType) || sqlType.contains(rowChange.getAction().name());
    }

    /**
     * queryLog example: /*DRDS /127.0.0.1/12fb0789b6000000/0// * /
     */
    private String findTraceId(DBMSQueryLog queryLog) {
        String query = queryLog.getQuery();
        int hintBegin = query.indexOf("/*");
        if (hintBegin == -1) {
            return null;
        }
        int hintEnd = query.indexOf("*/");
        String hint = query.substring(hintBegin + 2, hintEnd);
        String[] hintKeyArray = hint.split("/");
        if (!hintKeyArray[RplConstants.KEY_DRDS_IDX].trim().equals(RplConstants.DRDS_HINT_PREFIX)) {
            return null;
        }
        String findTrace = hintKeyArray[RplConstants.KEY_TRACE_IDX];
        log.info("find trace : " + findTrace);
        this.queryLog = query;
        return findTrace;
    }

    private DBMSRowChange fillFuzzyOption(DBMSRowChange rowChange) {
        if (rowChange == null) {
            return null;
        }
        rowChange.setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_CALLBACK_TYPE,
            isFuzzySearch ? "fuzzy" : "exact");
        rowChange.setOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_QUERY_LOG, queryLog);
        return rowChange;
    }

    private DBMSRowChange generateEndFlag() {
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
        DefaultOption option = (DefaultOption) event.getOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP);
        return (Timestamp) option.getValue();
    }

    private String getPosition(DBMSEvent event) {
        DefaultOption option = (DefaultOption) event.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION);
        return (String) option.getValue();
    }
}
