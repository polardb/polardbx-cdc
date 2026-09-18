/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.io.Serializable;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reserved staging-table recognition and fixed-schema row decoding.
 */
public final class ExternalColumnStagingEvent {

    static final String STAGING_SCHEMA = "__polarx_ext_staging";
    private static final Pattern TABLE_PATTERN = Pattern.compile("^polarx_ext_staging_([1-9][0-9]*)$",
        Pattern.CASE_INSENSITIVE);
    private static final int COLUMN_COUNT = 4;

    private ExternalColumnStagingEvent() {
    }

    static boolean isStagingEvent(LogEvent event) {
        if (event instanceof TableMapLogEvent) {
            return isStagingTable((TableMapLogEvent) event);
        }
        if (event instanceof RowsLogEvent) {
            return isStagingTable(((RowsLogEvent) event).getTable());
        }
        return false;
    }

    static int validateAndGetSeqId(TableMapLogEvent table) {
        if (!isStagingTable(table)) {
            throw new PolardbxException("event is not from the reserved external-column staging table");
        }
        Matcher matcher = TABLE_PATTERN.matcher(table.getTableName());
        if (!matcher.matches()) {
            throw new PolardbxException("invalid external-column staging table name " + table.getTableName());
        }
        final int seqId;
        try {
            seqId = Integer.parseInt(matcher.group(1));
        } catch (RuntimeException e) {
            throw new PolardbxException("invalid external-column staging seqId in table " + table.getTableName(), e);
        }
        validateSchema(table);
        return seqId;
    }

    static void consumeWriteRows(RowsLogEvent event, StagingRowConsumer consumer) {
        int seqId = validateAndGetSeqId(event.getTable());
        int type = event.getHeader().getType();
        if (type != LogEvent.WRITE_ROWS_EVENT && type != LogEvent.WRITE_ROWS_EVENT_V1) {
            throw new PolardbxException("only WRITE_ROWS is valid for external-column staging inserts");
        }

        BitSet columns = event.getColumns();
        if (!columns.get(0) || !columns.get(1) || !columns.get(2)) {
            throw new PolardbxException("external-column staging WRITE_ROWS misses a required column");
        }
        RowsLogBuffer buffer = event.getRowsBuf("utf8");
        TableMapLogEvent.ColumnInfo[] columnInfos = event.getTable().getColumnInfo();
        while (buffer.nextOneRow(columns)) {
            Serializable blobAddr = null;
            Serializable tableId = null;
            Serializable raw = null;
            for (int i = 0; i < COLUMN_COUNT; i++) {
                if (!columns.get(i)) {
                    continue;
                }
                TableMapLogEvent.ColumnInfo columnInfo = columnInfos[i];
                Serializable value = buffer.nextValue(columnInfo.type, columnInfo.meta, i == 2);
                if (i == 0) {
                    blobAddr = value;
                } else if (i == 1) {
                    tableId = value;
                } else if (i == 2) {
                    raw = value;
                }
            }
            if (!(blobAddr instanceof Number) || !(tableId instanceof Number) || !(raw instanceof byte[])) {
                throw new PolardbxException("invalid external-column staging WRITE_ROWS value types");
            }
            consumer.accept(seqId, ((Number) blobAddr).longValue(), ((Number) tableId).longValue(), (byte[]) raw);
        }
    }

    public static boolean isStagingTable(String schema, String table) {
        return schema != null && table != null && STAGING_SCHEMA.equalsIgnoreCase(schema)
            && TABLE_PATTERN.matcher(table).matches();
    }

    private static boolean isStagingTable(TableMapLogEvent table) {
        return table != null && isStagingTable(table.getDbName(), table.getTableName());
    }

    private static void validateSchema(TableMapLogEvent table) {
        if (table.getColumnCnt() != COLUMN_COUNT || table.getColumnInfo().length != COLUMN_COUNT) {
            throw new PolardbxException("invalid external-column staging column count for " + table.info());
        }
        TableMapLogEvent.ColumnInfo[] columns = table.getColumnInfo();
        if (columns[0].type != LogEvent.MYSQL_TYPE_LONGLONG
            || columns[1].type != LogEvent.MYSQL_TYPE_LONGLONG
            || columns[2].type != LogEvent.MYSQL_TYPE_BLOB || columns[2].meta != 4
            || (columns[3].type != LogEvent.MYSQL_TYPE_TIMESTAMP
            && columns[3].type != LogEvent.MYSQL_TYPE_TIMESTAMP2)
            || table.getNullBits() == null || !table.getNullBits().isEmpty()) {
            throw new PolardbxException("invalid external-column staging TABLE_MAP schema for " + table.info());
        }
    }

    @FunctionalInterface
    interface StagingRowConsumer {
        void accept(int seqId, long slotAddr, long tableId, byte[] raw);
    }
}
