/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.rpl.applier.ApplyHelper;
import com.aliyun.polardbx.rpl.applier.StatisticUnit;
import com.aliyun.polardbx.rpl.dbmeta.ColumnValue;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2020/12/21 10:50
 * @since 5.0.0.0
 */
public class LogUtil {

    private static final ThreadLocal<Map<String, Logger>> loggers = new ThreadLocal<>();
    /**
     * Log4j is thread safe
     */

    private static String COMMIT_LOGGER = "commitLogger";
    private static String CHECK_RESULT_LOGGER = "checkResultLogger";
    private static String POSITION_LOGGER = "positionLogger";
    private static String META_LOGGER = "metaLogger";
    private static String STATISTIC_LOGGER = "statisticLogger";
    private static String CONSOLE_LOGGER = "consoleLogger";
    private static String RPL_LOGGER = "rplLogger";

    static {
        loggers.set(new HashMap<>());
    }

    public static Logger getStatisticLogger() {
        return LoggerFactory.getLogger(STATISTIC_LOGGER);
    }

    public static Logger getCommitLogger() {
        return LoggerFactory.getLogger(COMMIT_LOGGER);
    }

    public static Logger getMetaLogger() {
        return LoggerFactory.getLogger(META_LOGGER);
    }

    public static Logger getPositionLogger() {
        return LoggerFactory.getLogger(POSITION_LOGGER);
    }

    public static Logger getConsoleLogger() {
        return LoggerFactory.getLogger(CONSOLE_LOGGER);
    }

    public static String generatePositionLog(String position) {
        return CommonUtil.getCurrentTime() + " " + position;
    }

    public static Logger getRplLogger() {
        return LoggerFactory.getLogger(RPL_LOGGER);
    }

    public static String generateStatisticLog(StatisticUnit unit, String intervalItem, long totalInCache) {
        StringBuilder logSb = new StringBuilder();
        logSb.append(CommonUtil.getCurrentTime())
            .append(" totalConsumeMessageCount: ").append(unit.getTotalConsumeMessageCount().get(intervalItem))
            .append(" messageRps: ").append(unit.getMessageRps().get(intervalItem))
            .append(" applyQps: ").append(unit.getApplyQps().get(intervalItem))
            .append(" applyRt: ").append(unit.getApplyRt().get(intervalItem))
            .append(" avgMergeBatchSize: ").append(unit.getAvgMergeBatchSize().get(intervalItem))
            .append(" skipCount: ").append(unit.getSkipCounter())
            .append(" totalDealWithCount: ").append(unit.getPersistentMessageCounter())
            .append(" skipExceptionCount: ").append(unit.getSkipExceptionCounter())
            .append(" totalInCache: ").append(totalInCache);
        return logSb.toString();
    }

    public static void logFullCommitInfo(List<DBMSEvent> dbmsEvents, String physicalInfo) {
        List<String> logs = new ArrayList<>();
        for (DBMSEvent event : dbmsEvents) {
            logs.addAll(LogUtil.generateCommitLog(event, physicalInfo));
        }
        LogUtil.writeBatchLogs(logs, LogUtil.getCommitLogger());
    }

    public static List<String> generateCommitLog(DBMSEvent event, String physicalInfo) {
        List<String> logs = new ArrayList<>();
        String timestamp = CommonUtil.getCurrentTime();

        if (ApplyHelper.isDdl(event)) {
            String position = (String) (event.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
            String log = String.format("%s, DDL: schema: %s, action: %s, sql: %s, position: %s",
                timestamp,
                event.getSchema(),
                event.getAction().name(),
                ((DefaultQueryLog) event).getQuery(),
                position);
            logs.add(log);
        } else {
            DBMSRowChange rowChange = (DBMSRowChange) event;
            String position = null;
            String binlogTimestamp = null;
            String sourceSchema = null;
            String sourceTable = null;
            if (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION) != null &&
                rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue() != null) {
                position = (String) (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
            }
            if (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP) != null &&
                rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP).getValue() != null) {
                binlogTimestamp = rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP).getValue().toString();
            }
            if (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA) != null &&
                rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA).getValue() != null) {
                sourceSchema = rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA).getValue().toString();
            }
            if (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_TABLE) != null &&
                rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_TABLE).getValue() != null) {
                sourceTable = rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_TABLE).getValue().toString();
            }
            String[] pks = getPks(event);
            StringBuilder logSb = new StringBuilder();
            logSb.append(timestamp).append(RplConstants.COMMA);
            logSb.append(event.getAction().name()).append(RplConstants.COMMA);
            logSb.append(rowChange.getSchema()).append(".");
            logSb.append(rowChange.getTable()).append(RplConstants.COMMA);
            logSb.append(sourceSchema).append(".");
            logSb.append(sourceTable).append(RplConstants.COMMA);

            for (String str : pks) {
                logSb.append(str);
            }
            logSb.append(RplConstants.COMMA);
            logSb.append(position).append(RplConstants.COMMA);
            logSb.append(binlogTimestamp);
            logs.add(logSb.toString());
        }

        return logs;
    }

    public static String[] getPks(DBMSEvent dbMessage) {
        DBMSRowChange rowChange = (DBMSRowChange) dbMessage;
        int rowSize = rowChange.getRowSize();
        String[] pks = new String[rowSize];
        for (int i = 0; i < rowSize; i++) {
            List<DBMSColumn> primaryKeys = rowChange.getPrimaryKey();
            StringBuilder pkSb = new StringBuilder("[");
            if (primaryKeys != null) {
                Iterator<DBMSColumn> iterator = primaryKeys.iterator();
                while (iterator.hasNext()) {
                    DBMSColumn column = iterator.next();
                    pkSb.append(rowChange.getRowValue(i + 1, column));
                    if (iterator.hasNext()) {
                        pkSb.append(", ");
                    }
                }
            }
            pkSb.append("]");
            pks[i] = pkSb.toString();
        }
        return pks;
    }

    public static void writeBatchLogs(List<String> logs, Logger logger) {
        StringBuilder logsSb = new StringBuilder();
        for (int i = 0; i < logs.size(); i++) {
            String log = logs.get(i);
            logsSb.append(log);
            if (logsSb.length() > RplConstants.LOG_MAX_LENGTH || i == logs.size() - 1) {
                logger.info(logsSb.toString());
                logsSb.setLength(0);
            } else {
                logsSb.append(RplConstants.LINE_SEPERATOR);
            }
        }
    }
}
