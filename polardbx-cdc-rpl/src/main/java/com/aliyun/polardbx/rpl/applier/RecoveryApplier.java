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
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.filter.RecoveryFilter;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.RecoverySearchTaskResultMeta;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.FLASHBACK_BINLOG_WRITE_BUFFER_BYTE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.FLASHBACK_BINLOG_WRITE_BUFFER_SQL_SIZE;
import static com.aliyun.polardbx.rpl.common.RplConstants.FLASH_BACK_PARTIAL_PREFIX;
import static com.aliyun.polardbx.rpl.common.RplConstants.FLASH_BACK_PARTIAL_RESULT_FILE;

/**
 * @author yudong
 */
@Slf4j
public class RecoveryApplier extends BaseApplier {

    private final RecoveryFilter filter;
    private final boolean isMirror;
    private final String schema;

    private final long bufferSqlSize;

    private final long bufferByteSize;

    private final String filePrefix;
    private final String sequence;
    private final List<TransactionBucket> transactionBucketList = Lists.newArrayList();
    private BaseExtractor extractor;
    private boolean isFuzzy = false;
    private Map<String, Long> fileSizeMap;

    private Map<String, String> fileMd5Map;

    private long sqlCounter = 0;

    private long sqlByteOfThisBatch = 0;

    private long alreadyFlushSqlCounter = 0;

    private long taskId;

    private int fileSuffixNumber = -1;

    private String currentOutputFile;

    private RecoverySearchTaskResultMeta previousResultMeta;

    public RecoveryApplier(RecoveryApplierConfig applierConfig) {
        super(applierConfig);
        this.schema = applierConfig.getSchema();
        this.isMirror = applierConfig.isMirror();
        this.sequence = buildFixedLengthNumber(applierConfig.getSequence());
        this.filePrefix = MessageFormat.format(FLASH_BACK_PARTIAL_PREFIX, applierConfig.getRandomUUID(), sequence);
        this.filter = new RecoveryFilter(applierConfig);
        this.bufferSqlSize = DynamicApplicationConfig.getInt(FLASHBACK_BINLOG_WRITE_BUFFER_SQL_SIZE);
        this.bufferByteSize = DynamicApplicationConfig.getInt(FLASHBACK_BINLOG_WRITE_BUFFER_BYTE_SIZE);
        this.fileSizeMap = new HashMap<>();
        this.fileMd5Map = new HashMap<>();
        this.buildNextResultFileName();
    }

    @Override
    public boolean init() {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task.getStatus() != null && task.getStatus() == TaskStatus.FINISHED.getValue()) {
            log.info("task {} has already finished, sikp execute.", taskId);
            System.exit(1);
        }

        if (StringUtils.isNotBlank(task.getExtra()) && task.getExtra().contains("injectTroubleCount")) {
            previousResultMeta = JSONObject.parseObject(task.getExtra(), RecoverySearchTaskResultMeta.class);
        }

        RemoteBinlogProxy.getInstance().deleteAll(filePrefix);
        boolean result = RemoteBinlogProxy.getInstance().isObjectsExistForPrefix(filePrefix);
        if (result) {
            throw new PolardbxException("found dirty files for task " + taskId + " with prefix " + filePrefix);
        }
        return true;
    }

    @Override
    public boolean apply(List<DBMSEvent> dbmsEvents) {
        if (CollectionUtils.isEmpty(dbmsEvents)) {
            return true;
        }

        for (DBMSEvent event : dbmsEvents) {
            event = filter.convert(event);

            if (event == null) {
                continue;
            }

            Boolean shouldStopFlag = (Boolean) event.getOptionValue(RplConstants.BINLOG_EVENT_OPTION_SHOULD_STOP);
            String queryLog = (String) event.getOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_QUERY_LOG);
            String newTid = (String) event.getOptionValue(RplConstants.BINLOG_EVENT_OPTION_T_ID);
            Serializable type = event.getOptionValue(RplConstants.BINLOG_EVENT_OPTION_SQL_CALLBACK_TYPE);
            if (type != null && "fuzzy".equals(type.toString())) {
                isFuzzy = true;
            }

            if (StringUtils.isNotEmpty(queryLog)) {
                queryLog = queryLog.replaceAll("\n", " ").replaceAll("\r", " ");
            }

            if (shouldStopFlag == null) {
                TransactionBucket currentTransaction;
                if (transactionBucketList.isEmpty()) {
                    currentTransaction = new TransactionBucket(newTid, queryLog);
                    transactionBucketList.add(currentTransaction);
                } else {
                    currentTransaction = transactionBucketList.get(transactionBucketList.size() - 1);
                    if (!currentTransaction.isCurrentTid(newTid)) {
                        currentTransaction = new TransactionBucket(newTid, queryLog);
                        transactionBucketList.add(currentTransaction);
                    }
                }

                String sql;
                if (isMirror) {
                    sql = mirrorSQL(event);
                } else {
                    sql = reverseSQL(event);
                }

                if (StringUtils.isNotEmpty(sql)) {
                    currentTransaction.addSql(sql);
                    // 粗略计算下字节数
                    sqlByteOfThisBatch += sql.length() * 2L;
                    sqlCounter++;
                }

            } else {
                log.warn("receive stop flag!");
                try {
                    flush();
                    recordTaskExecuteInfo();

                    //注入故障
                    tryInjectTrouble();

                    FSMMetaManager.setTaskFinish(taskId);
                    // todo by yudong 上传日志至oss
                    System.exit(0);
                } catch (Throwable e) {
                    log.error("flush data occur exception", e);
                    MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_FLASHBACK_ERROR,
                        TaskContext.getInstance().getTaskId(), "flush data occur exception: " + e.getMessage());
                    throw e;
                } finally {
                    extractor.stop();
                }
                return true;
            }
        }

        try {
            if (sqlCounter - alreadyFlushSqlCounter > bufferSqlSize
                || sqlByteOfThisBatch > bufferByteSize) {
                alreadyFlushSqlCounter = sqlCounter;
                sqlByteOfThisBatch = 0;
                flush();
            }
        } catch (Throwable e) {
            log.error("flush data occur exception ", e);
            MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_FLASHBACK_ERROR,
                TaskContext.getInstance().getTaskId(), "flush data occur exception: " + e.getMessage());
            throw e;
        }

        return true;
    }

    @SneakyThrows
    public void flush() {
        if (sqlCounter == 0) {
            log.error("start to flush, but sql counter is 0!");
            return;
        }
        final StringBuilder stringBuilder = new StringBuilder();
        if (isMirror) {
            for (TransactionBucket bucket : transactionBucketList) {
                bucket.appendBuffer(stringBuilder);
            }
        } else {
            for (int i = transactionBucketList.size() - 1; i >= 0; i--) {
                TransactionBucket bucket = transactionBucketList.get(i);
                bucket.reverseBuffer(stringBuilder);
            }
        }

        transactionBucketList.clear();

        final long size = sqlCounter;
        if (stringBuilder.length() > 0) {
            log.info("start flush sql size: " + size);
            long nextPosition = 0;
            final Appender appender = RemoteBinlogProxy.getInstance().providerAppender(currentOutputFile);
            try {
                appender.begin();
                byte[] bytes = stringBuilder.toString().getBytes("UTF-8");
                nextPosition = appender.append(bytes, bytes.length);
                appender.end();
            } catch (Throwable e) {
                log.info("oss put to target failed!");
                throw e;
            }

            String md5 = RemoteBinlogProxy.getInstance().getMd5(currentOutputFile);
            fileSizeMap.put(StringUtils.substringAfterLast(currentOutputFile, "/"), nextPosition);
            fileMd5Map.put(StringUtils.substringAfterLast(currentOutputFile, "/"), md5);
            buildNextResultFileName();
        }
    }

    private String valueWrapper(Serializable data, int sqlType) {
        if (data == null) {
            return "NULL";
        }
        if (sqlType == Types.BIT) {
            long a = 0;
            if (data instanceof byte[]) {
                byte[] array = (byte[]) data;
                for (int i = 0; i < array.length; i++) {
                    long b = array[i];
                    a |= ((0xFF & b) << (i << 3));
                }
                return a + "";
            } else if (data instanceof Integer) {
                Integer intValue = (Integer) data;
                return intValue + "";

            } else if (data instanceof Long) {
                Long longValue = (Long) data;
                return longValue + "";
            }
            throw new PolardbxException("unsupported type for bit : " + data);
        }
        if (data instanceof byte[]) {
            byte[] array = (byte[]) data;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("0x");
            for (byte b : array) {
                stringBuilder.append(String.format("%02x", b).toUpperCase());
            }
            return stringBuilder.toString();
        }

        return ("'" + data.toString().replaceAll("'", "\\\\'").replaceAll("\n", "\\\\n") + "'");
    }

    private String whereClause(DBMSRowChange event, DBMSRowData data) {
        List<DBMSColumn> keys = event.getPrimaryKey();
        if (keys.isEmpty()) {
            keys = event.getUniqueKey();
            if (keys.isEmpty()) {
                throw new PolardbxException("No primary or unique key found");
            }
        }
        StringBuilder str = new StringBuilder(
            "t.`" + keys.get(0).getName() + "` =  " + valueWrapper(data.getRowValue(keys.get(0)),
                keys.get(0).getSqlType()));
        for (int i = 1; i < keys.size(); i++) {
            str.append(" and ");
            str.append("t.`").
                append(keys.get(i).getName()).
                append("` =  ").
                append(valueWrapper(data.getRowValue(keys.get(i)), keys.get(i).getSqlType()));
        }
        str.append(";");
        return str.toString();
    }

    private String setClause(DBMSRowChange event, DBMSRowData data) {
        StringBuilder str = new StringBuilder();
        List<? extends DBMSColumn> cols = event.getColumns();
        if (cols.isEmpty()) {
            throw new PolardbxException("No cols found");
        }

        for (DBMSColumn column : cols) {
            if (!event.hasChangeColumn(column.getColumnIndex())) {
                continue;
            }
            if (str.length() > 0) {
                str.append(" , ");
            }
            str.append("t.`").
                append(column.getName()).
                append("` =  ").
                append(valueWrapper(data.getRowValue(column), column.getSqlType()));
        }
        return str.toString();
    }

    private String insertClause(DBMSRowChange event, DBMSRowData data) {
        StringBuilder str = new StringBuilder("(");
        List<? extends DBMSColumn> cols = event.getColumns();
        if (cols.isEmpty()) {
            throw new PolardbxException("No cols found");
        }
        str.append('`').
            append(cols.get(0).getName()).
            append('`');

        for (int i = 1; i < cols.size(); i++) {
            str.append(" , ").
                append('`').
                append(cols.get(i).getName()).
                append('`');
        }
        str.append(") VALUES (");

        str.append(valueWrapper(data.getRowValue(cols.get(0)), cols.get(0).getSqlType()));
        for (int i = 1; i < cols.size(); i++) {
            str.append(" , ").
                append(valueWrapper(data.getRowValue(cols.get(i)), cols.get(i).getSqlType()));
        }
        str.append(");");
        return str.toString();
    }

    private String reverseSQL(DBMSEvent event) {
        if (event instanceof DBMSRowChange) {
            String schema = this.schema;
            DefaultRowChange defaultRowChange = (DefaultRowChange) event;
            String table = defaultRowChange.getTable();

            if (hasPrimaryOrUniqueKey(defaultRowChange)) {
                for (int i = 1; i <= defaultRowChange.getRowSize(); i++) {
                    if (event.getAction() == DBMSAction.UPDATE) {
                        return "UPDATE `" + schema + "`.`" + table + "` t SET " +
                            setClause(defaultRowChange, defaultRowChange.getRowData(i)) + " WHERE "
                            + whereClause(defaultRowChange, defaultRowChange.getChangeData(i));
                    } else if (event.getAction() == DBMSAction.DELETE) {
                        return "REPLACE INTO `" + schema + "`.`" + table + "` " +
                            insertClause(defaultRowChange, defaultRowChange.getRowData(i));
                    } else if (event.getAction() == DBMSAction.INSERT) {
                        return "DELETE FROM `" + schema + "`.`" + table + "` t WHERE "
                            + whereClause(defaultRowChange, defaultRowChange.getRowData(i));
                    }
                }
            } else {
                log.warn("skip table {}:{}, because primary key and unique key both not exist.", schema, table);
            }
        }
        return null;
    }

    private boolean hasPrimaryOrUniqueKey(DBMSRowChange event) {
        List<DBMSColumn> keys = event.getPrimaryKey();
        if (keys.isEmpty()) {
            keys = event.getUniqueKey();
            if (keys.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String mirrorSQL(DBMSEvent event) {
        if (event instanceof DBMSRowChange) {
            String schema = this.schema;
            DefaultRowChange defaultRowChange = (DefaultRowChange) event;
            String table = defaultRowChange.getTable();

            if (hasPrimaryOrUniqueKey(defaultRowChange)) {
                for (int i = 1; i <= defaultRowChange.getRowSize(); i++) {
                    if (event.getAction() == DBMSAction.UPDATE) {
                        return "INSERT INTO `" + schema + "`.`" + table + "` " +
                            insertClause(defaultRowChange, defaultRowChange.getRowData(i));
                    } else if (event.getAction() == DBMSAction.DELETE) {
                        return "INSERT INTO `" + schema + "`.`" + table + "` " +
                            insertClause(defaultRowChange, defaultRowChange.getRowData(i));
                    } else if (event.getAction() == DBMSAction.INSERT) {
                        return "INSERT INTO `" + schema + "`.`" + table + "` " +
                            insertClause(defaultRowChange, defaultRowChange.getRowData(i));
                    }
                }
            } else {
                log.warn("skip table {}:{}, because primary key and unique key both not exist.", schema, table);
            }
        }
        return null;
    }

    public void setExtractor(BaseExtractor extractor) {
        this.extractor = extractor;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    private String buildFixedLengthNumber(int num) {
        return StringUtils.leftPad(String.valueOf(num), 6, "0");
    }

    private void buildNextResultFileName() {
        fileSuffixNumber++;
        currentOutputFile = MessageFormat.format(FLASH_BACK_PARTIAL_RESULT_FILE,
            ((RecoveryApplierConfig) applierConfig).getRandomUUID(), sequence,
            buildFixedLengthNumber(fileSuffixNumber));
    }

    private void tryInjectTrouble() {
        RecoveryApplierConfig config = (RecoveryApplierConfig) applierConfig;
        if (config.isInjectTrouble()) {
            if (previousResultMeta == null || previousResultMeta.getInjectTroubleCount() < 5) {
                log.info("trigger trouble for task " + taskId);
                throw new PolardbxException("trouble inject for task " + taskId);
            }
        }
    }

    private void recordTaskExecuteInfo() {
        log.info("record task result meta info to db :" + sqlCounter);
        RecoverySearchTaskResultMeta resultMeta = new RecoverySearchTaskResultMeta();
        resultMeta.setSqlCounter(sqlCounter);
        resultMeta.setFileSizeMap(fileSizeMap);
        resultMeta.setFileMd5Map(fileMd5Map);
        resultMeta.setInjectTroubleCount(previousResultMeta != null ?
            previousResultMeta.getInjectTroubleCount() + 1 : 0);
        DbTaskMetaManager.updateExtra(taskId, JSONObject.toJSONString(resultMeta));
    }

    private static class TransactionBucket {
        private final String tid;
        private final String startQueryLog;
        private final List<String> sqlList = Lists.newArrayList();

        public TransactionBucket(String tid, String startQueryLog) {
            this.tid = tid;
            this.startQueryLog = startQueryLog;
        }

        public boolean isCurrentTid(String newTid) {
            return tid.equalsIgnoreCase(newTid);
        }

        public void addSql(String newSql) {
            this.sqlList.add(newSql);
        }

        public void appendBuffer(StringBuilder sb) {
            String startTransaction = String.format("\n-- Transaction start, threadId %s;\n", tid);
            if (StringUtils.isNotEmpty(startQueryLog)) {
                startTransaction += String.format("-- %s\n", startQueryLog);
            }

            sb.append(startTransaction);
            for (String s : sqlList) {
                sb.append(s).append("\n");
            }
            sb.append(RplConstants.TRANSACTION_END_COMMENT);
        }

        public void reverseBuffer(StringBuilder sb) {
            String startTransaction = String.format("\n-- Transaction start, threadId %s;\n", tid);
            if (StringUtils.isNotEmpty(startQueryLog)) {
                startTransaction += String.format("-- %s\n", startQueryLog);
            }

            sb.append(startTransaction);
            for (int i = sqlList.size() - 1; i >= 0; i--) {
                sb.append(sqlList.get(i)).append("\n");
            }
            sb.append(RplConstants.TRANSACTION_END_COMMENT);
        }
    }
}
