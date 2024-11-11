/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.rpl.applier.DdlApplyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
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
    private static String POSITION_LOGGER = "positionLogger";
    private static String META_LOGGER = "metaLogger";
    private static String STATISTIC_LOGGER = "statisticLogger";
    private static String CONSOLE_LOGGER = "consoleLogger";
    private static String RPL_LOGGER = "rplLogger";
    private static String ASYNC_DDL_LOGGER = "asyncDdlLogger";
    private static String SKIP_DDL_LOGGER = "skipDdlLogger";

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

    public static Logger getAsyncDdlLogger() {
        return LoggerFactory.getLogger(ASYNC_DDL_LOGGER);
    }

    public static Logger getSkipDdlLogger() {
        return LoggerFactory.getLogger(SKIP_DDL_LOGGER);
    }

    public static Logger getPositionLogger() {
        return LoggerFactory.getLogger(POSITION_LOGGER);
    }

    public static String generatePositionLog(String position) {
        return CommonUtil.getCurrentTime() + " " + position;
    }

    public static String generateStatisticLogV2(RplStatMetrics rplStatMetrics) {
        return CommonUtil.getCurrentTime()
            + " outRps:" + rplStatMetrics.getOutRps()
            + " applyCount:" + rplStatMetrics.getApplyCount()
            + " inEps:" + rplStatMetrics.getInEps()
            + " outBps:" + rplStatMetrics.getOutBps()
            + " inBps:" + rplStatMetrics.getInBps()
            + " outInsertRps:" + rplStatMetrics.getOutInsertRps()
            + " outUpdateRps:" + rplStatMetrics.getOutUpdateRps()
            + " outDeleteRps:" + rplStatMetrics.getOutDeleteRps()
            + " receiveDelay:" + rplStatMetrics.getReceiveDelay()
            + " processDelay:" + rplStatMetrics.getProcessDelay()
            + " mergeBatchSize:" + rplStatMetrics.getMergeBatchSize()
            + " rt:" + rplStatMetrics.getRt()
            + " skipCounter:" + rplStatMetrics.getSkipCounter()
            + " skipExceptionCounter:" + rplStatMetrics.getSkipExceptionCounter()
            + " persistMsgCounter:" + rplStatMetrics.getPersistMsgCounter()
            + " msgCacheSize:" + rplStatMetrics.getMsgCacheSize()
            + " cpuUseRatio:" + rplStatMetrics.getCpuUseRatio()
            + " memUseRatio:" + rplStatMetrics.getMemUseRatio()
            + " fullGcCount:" + rplStatMetrics.getFullGcCount()
            + " workerIp:" + rplStatMetrics.getWorkerIp()
            + " totalCommitCount" + rplStatMetrics.getTotalCommitCount()
            + " trueDelayMills" + rplStatMetrics.getTrueDelayMills();
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
        String position = event.getPosition();

        if (DdlApplyHelper.isDdl(event)) {
            String log = String.format("%s, DDL: schema: %s, action: %s, sql: %s, position: %s",
                timestamp,
                event.getSchema(),
                event.getAction().name(),
                ((DefaultQueryLog) event).getQuery(),
                position);
            logs.add(log);
        } else {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String sourceSchema = null;
            String sourceTable = null;
            String binlogTimestamp = new Timestamp(rowChange.getSourceTimeStamp()).toString();
            if (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA) != null &&
                rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA).getValue() != null) {
                sourceSchema =
                    rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA).getValue().toString();
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
        DefaultRowChange rowChange = (DefaultRowChange) dbMessage;
        int rowSize = rowChange.getRowSize();
        String[] pks = new String[rowSize];
        for (int i = 0; i < rowSize; i++) {
            List<? extends DBMSColumn> primaryKeys = rowChange.getPrimaryKey();
//            if (primaryKeys.isEmpty()) {
//                primaryKeys = rowChange.getColumns();
//            }
            StringBuilder pkSb = new StringBuilder("[");
            if (primaryKeys != null) {
                Iterator<? extends DBMSColumn> iterator = primaryKeys.iterator();
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
