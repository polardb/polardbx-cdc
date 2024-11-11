/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.field.Field;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_BIT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_BLOB;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_DATETIME2;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_DOUBLE;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_ENUM;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_FLOAT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_GEOMETRY;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_JSON;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_LONG_BLOB;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_MEDIUM_BLOB;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_NEWDECIMAL;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_SET;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_STRING;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_TIME2;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_TIMESTAMP2;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_TINY_BLOB;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_TYPED_ARRAY;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.MYSQL_TYPE_VARCHAR;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT_V1;

public class RowDataRebuildLogger {
    private static final Logger logger = LoggerFactory.getLogger("reformatLogger");
    private final boolean isLogRecord;
    private final RowsLogBuffer logBuffer = new RowsLogBuffer(null, 0, "utf8");
    private RebuildFieldRowLog rebuildFieldRowLog;

    public RowDataRebuildLogger() {
        isLogRecord = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG);
    }

    private static int unSign(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    private static int decodeMeta(int binlogType, byte[] metaBuff) {
        int position = 0;
        if (binlogType == MYSQL_TYPE_TYPED_ARRAY) {
            binlogType = unSign(metaBuff, position++);
        }

        switch (binlogType) {
        case MYSQL_TYPE_TINY_BLOB:
        case MYSQL_TYPE_BLOB:
        case MYSQL_TYPE_MEDIUM_BLOB:
        case MYSQL_TYPE_LONG_BLOB:
        case MYSQL_TYPE_DOUBLE:
        case MYSQL_TYPE_FLOAT:
        case MYSQL_TYPE_GEOMETRY:
        case MYSQL_TYPE_JSON:
            /*
             * These types store a single byte.
             */
            return unSign(metaBuff, position);
        case MYSQL_TYPE_SET:
        case MYSQL_TYPE_ENUM:
        case MYSQL_TYPE_STRING:
        case MYSQL_TYPE_NEWDECIMAL: {
            /*
             * log_event.h : The first byte is always MYSQL_TYPE_VAR_STRING (i.e., 253). The second byte is the
             * field size, i.e., the number of bytes in the representation of size of the string: 3 or 4.
             */
            return unSign(metaBuff, position++) << 8 | unSign(metaBuff, position);
        }
        case MYSQL_TYPE_BIT:
        case MYSQL_TYPE_VARCHAR:
            return unSign(metaBuff, position++) | (unSign(metaBuff, position) << 8);
        case MYSQL_TYPE_TIME2:
        case MYSQL_TYPE_DATETIME2:
        case MYSQL_TYPE_TIMESTAMP2: {
            return unSign(metaBuff, position);
        }
        default:
            return 0;
        }
    }

    public void logRowBegin(LogicTableMeta tableMeta, TableMapLogEvent tableMap, RowData rowData, int eventType) {
        if (!isLogRecord) {
            return;
        }

        List<LogicTableMeta.FieldMetaExt> pkList = tableMeta.getPkList();
        TableMapLogEvent.ColumnInfo[] columnInfos = tableMap.getColumnInfo();
        rebuildFieldRowLog = new RebuildFieldRowLog(tableMap.getDbName(), tableMap.getTableName(), eventType
        );

        if (!CollectionUtils.isEmpty(pkList)) {
            for (LogicTableMeta.FieldMetaExt fieldMeta : pkList) {
                Field field = rowData.getBiFieldList().get(fieldMeta.getPhyIndex());
                String charset = fieldMeta.getCharset();
                String javaCharset = CharsetConversion.getJavaCharset(charset);
                TableMapLogEvent.ColumnInfo columnInfo = columnInfos[fieldMeta.getPhyIndex()];
                byte[] data = field.encode();
                Serializable pk = logBuffer
                    .fetchValue(columnInfo.type,
                        columnInfo.meta,
                        false,
                        new LogBuffer(data, 0, data.length),
                        javaCharset,
                        fieldMeta.isUnsigned());
                rebuildFieldRowLog.addPk(pk);
            }
        }
    }

    public void logEnd() {
        if (!isLogRecord) {
            return;
        }
        if (rebuildFieldRowLog.ignore ||
            rebuildFieldRowLog.records.isEmpty()) {
            return;
        }
        logger.info(rebuildFieldRowLog.toString());
    }

    public void logAddField(LogicTableMeta.FieldMetaExt afterFieldMeta,
                            int type, byte[] meta, byte[] dataAfter) {
        if (!isLogRecord) {
            return;
        }
        try {
            Serializable value = null;
            if (dataAfter.length > 0) {
                value = decodeValue(afterFieldMeta, type, meta, dataAfter);
            }
            rebuildFieldRowLog.addLog(
                "add " + afterFieldMeta.getColumnName() + " with " + JSON.toJSONString(value) + " [" + type + "]");
        } catch (Exception e) {
            logger.error(
                "decode data error , colName : " + afterFieldMeta.getColumnName() + " type : "
                    + afterFieldMeta.getColumnType() + ", meta : " + Arrays.toString(meta) +
                    ", data : " + Arrays.toString(dataAfter));
            throw new PolardbxException(e);
        }
    }

    public Serializable decodeValue(LogicTableMeta.FieldMetaExt afterFieldMeta,
                                    int type, byte[] meta, byte[] data) {
        String charset = afterFieldMeta.getCharset();
        return doDecode(type, meta, data, charset, afterFieldMeta.isUnsigned());
    }

    public Serializable doDecode(int type, byte[] meta, byte[] data, String charset, boolean isUnsigned) {
        int m = decodeMeta(type, meta);
        String javaCharset = CharsetConversion.getJavaCharset(charset);
        return logBuffer
            .fetchValue(type, m, false,
                new LogBuffer(data, 0, data.length),
                javaCharset,
                isUnsigned);
    }

    public void logRemoveField() {
        if (!isLogRecord) {
            return;
        }
        rebuildFieldRowLog.setIgnore(true);
        rebuildFieldRowLog.addLog("remove column");
    }

    public void logData(int typeBefore, Serializable before, LogicTableMeta.FieldMetaExt afterFieldMeta,
                        int type, byte[] meta, byte[] dataAfter) {
        if (!isLogRecord) {
            return;
        }
        try {
            Serializable value = null;
            if (dataAfter.length > 0) {
                value = decodeValue(afterFieldMeta, type, meta, dataAfter);
            }

            rebuildFieldRowLog.addRecord(afterFieldMeta.getColumnName(), typeBefore, type, before, value);
        } catch (Exception e) {
            logger.error(
                "decode data error , colName : " + afterFieldMeta.getColumnName() + " before value : " + before
                    + ", type : "
                    + afterFieldMeta.getColumnType() + ", meta : " + Arrays.toString(meta) +
                    ", data : " + Arrays.toString(dataAfter));
            throw new PolardbxException(e);
        }
    }

    private static class RebuildFieldRowLog {

        private boolean ignore = false;
        private final String table;
        private final int eventType;
        private final List<Serializable> pkList = new ArrayList<>();
        private final List<Object> records = new ArrayList<>();

        public RebuildFieldRowLog(String db, String table, int eventType) {
            this.table = db + "." + table;
            this.eventType = eventType;
        }

        void addPk(Serializable pk) {
            pkList.add(pk);
        }

        void addRecord(String columnName, int typeBefore, int typeAfter, Serializable before, Serializable after) {
            RecordLog log = new RecordLog(columnName, typeBefore, typeAfter, before, after);
            records.add(log);
        }

        void addLog(String log) {
            this.records.add(log);
        }

        public void setIgnore(boolean ignore) {
            this.ignore = ignore;
        }

        @Override
        public String toString() {
            if (ignore) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            String eventName;
            switch (eventType) {
            case WRITE_ROWS_EVENT:
            case WRITE_ROWS_EVENT_V1:
                eventName = "INSERT";
                break;
            case UPDATE_ROWS_EVENT:
            case UPDATE_ROWS_EVENT_V1:
                eventName = "UPDATE";
                break;
            case DELETE_ROWS_EVENT:
            case DELETE_ROWS_EVENT_V1:
                eventName = "DELETE";
                break;
            default:
                eventName = eventType + "";
            }
            sb.append("\n").append(table).append(":").append(eventName);
            if (CollectionUtils.isNotEmpty(pkList)) {
                sb.append("(").append(StringUtils.join(pkList, ",")).append(")");
            }
            sb.append("\n");
            for (Object log : records) {
                sb.append("\t").append(log).append("\n");
            }
            return sb.toString();
        }
    }

    private static class RecordLog {

        private final String columnName;
        private final int typeBefore;
        private final int typeAfter;
        private Serializable before;
        private Serializable after;

        public RecordLog(String columnName, int typeBefore, int typeAfter, Serializable before, Serializable after) {
            this.columnName = columnName;
            this.typeBefore = typeBefore;
            this.typeAfter = typeAfter;
            this.before = before;
            this.after = after;
        }

        @Override
        public String toString() {
            if (before instanceof byte[]) {
                before = Hex.encodeHexString((byte[]) before);
            }
            if (after instanceof byte[]) {
                after = Hex.encodeHexString((byte[]) after);
            }
            return "{" + columnName + "=>" +
                "b=[" + typeBefore + "]:" + JSON.toJSONString(before) +
                ",a=[" + typeAfter + "]:" + JSON.toJSONString(after) +
                '}';
        }
    }
}
