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
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

import java.util.BitSet;

/**
 * Common base class for all row-containing log events.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class RowsLogEvent extends LogEvent {

    /**
     * Last event of a statement
     */
    public static final int STMT_END_F = 1;
    /**
     * Value of the OPTION_NO_FOREIGN_KEY_CHECKS flag in thd->options
     */
    public static final int NO_FOREIGN_KEY_CHECKS_F = (1 << 1);
    /**
     * Value of the OPTION_RELAXED_UNIQUE_CHECKS flag in thd->options
     */
    public static final int RELAXED_UNIQUE_CHECKS_F = (1 << 2);
    /**
     * Indicates that rows in this event are complete, that is contain values for
     * all columns of the table.
     */
    public static final int COMPLETE_ROWS_F = (1 << 3);
    /* RW = "RoWs" */
    public static final int RW_MAPID_OFFSET = 0;
    public static final int RW_FLAGS_OFFSET = 6;
    public static final int RW_VHLEN_OFFSET = 8;
    public static final int RW_V_TAG_LEN = 1;
    public static final int RW_V_EXTRAINFO_TAG = 0;
    /**
     * Bitmap denoting columns availablen
     */
    protected final int columnLen;
    protected final BitSet columns;
    /**
     * Bitmap for columns available in the after image, if present. These fields are
     * only available for Update_rows events. Observe that the width of both the
     * before image COLS vector and the after image COLS vector is the same: the
     * number of columns of the table on the master.
     */
    protected final BitSet changeColumns;
    /**
     * Fixed data part:
     * <ul>
     * <li>6 bytes. The table ID.</li>
     * <li>2 bytes. Reserved for future use.</li>
     * </ul>
     * <p>
     * Variable data part:
     * <ul>
     * <li>Packed integer. The number of columns in the table.</li>
     * <li>Variable-sized. Bit-field indicating whether each column is used, one bit per column. For this field, the
     * amount of storage required for N columns is INT((N+7)/8) bytes.</li>
     * <li>Variable-sized (for UPDATE_ROWS_LOG_EVENT only). Bit-field indicating whether each column is used in the
     * UPDATE_ROWS_LOG_EVENT after-image; one bit per column. For this field, the amount of storage required for N
     * columns is INT((N+7)/8) bytes.</li>
     * <li>Variable-sized. A sequence of zero or more rows. The end is determined by the size of the event. Each row has
     * the following format:
     * <ul>
     * <li>Variable-sized. Bit-field indicating whether each field in the row is NULL. Only columns that are "used"
     * according to the second field in the variable data part are listed here. If the second field in the variable data
     * part has N one-bits, the amount of storage required for this field is INT((N+7)/8) bytes.</li>
     * <li>Variable-sized. The row-image, containing values of all table fields. This only lists table fields that are
     * used (according to the second field of the variable data part) and non-NULL (according to the previous field). In
     * other words, the number of values listed here is equal to the number of zero bits in the previous field (not
     * counting padding bits in the last byte). The format of each value is described in the log_event_print_value()
     * function in log_event.cc.</li>
     * <li>(for UPDATE_ROWS_EVENT only) the previous two fields are repeated, representing a second table row.</li>
     * </ul>
     * </ul>
     * Source : http://forge.mysql.com/wiki/MySQL_Internals_Binary_Log
     */
    private final long tableId; /* Table ID */
    /**
     * XXX: Don't handle buffer in another thread.
     */
    private final LogBuffer rowsBuf; /*
     * The rows in packed format
     */
    /**
     * enum enum_flag These definitions allow you to combine the flags into an appropriate flag set using the normal
     * bitwise operators. The implicit conversion from an enum-constant to an integer is accepted by the compiler, which
     * is then used to set the real set of flags.
     */
    private final int flags;
    private String commitKey;
    private TableMapLogEvent table;                             /*
     * The table the rows belong to
     */

    public RowsLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        final int postHeaderLen = descriptionEvent.postHeaderLen[header.type - 1];
        int headerLen = 0;
        buffer.position(commonHeaderLen + RW_MAPID_OFFSET);
        if (postHeaderLen == 6) {
            /*
             * Master is of an intermediate source tree before 5.1.4. Id is 4 bytes
             */
            tableId = buffer.getUint32();
        } else {
            tableId = buffer.getUlong48(); // RW_FLAGS_OFFSET
        }
        flags = buffer.getUint16();

        if (postHeaderLen == FormatDescriptionLogEvent.ROWS_HEADER_LEN_V2) {
            headerLen = buffer.getUint16();
            headerLen -= 2;
            int start = buffer.position();
            int end = start + headerLen;
            for (int i = start; i < end; ) {
                switch (buffer.getUint8(i++)) {
                case RW_V_EXTRAINFO_TAG:
                    // int infoLen = buffer.getUint8();
                    buffer.position(i + EXTRA_ROW_INFO_LEN_OFFSET);
                    int checkLen = buffer.getUint8(); // EXTRA_ROW_INFO_LEN_OFFSET
                    int val = checkLen - EXTRA_ROW_INFO_HDR_BYTES;
                    assert (buffer.getUint8() == val); // EXTRA_ROW_INFO_FORMAT_OFFSET
                    for (int j = 0; j < val; j++) {
                        assert (buffer.getUint8() == val); // EXTRA_ROW_INFO_HDR_BYTES
                        // + i
                    }
                    break;
                default:
                    i = end;
                    break;
                }
            }
        }

        buffer.position(commonHeaderLen + postHeaderLen + headerLen);
        columnLen = (int) buffer.getPackedLong();
        columns = buffer.getBitmap(columnLen);

        if (header.type == UPDATE_ROWS_EVENT_V1 || header.type == UPDATE_ROWS_EVENT) {
            changeColumns = buffer.getBitmap(columnLen);
        } else {
            changeColumns = columns;
        }

        // XXX: Don't handle buffer in another thread.
        int dataSize = buffer.limit() - buffer.position();
        rowsBuf = buffer.duplicate(dataSize);
    }

    public final void fillTable(LogContext context) {
        table = context.getTable(tableId);

        // end of statement check:
        if ((flags & RowsLogEvent.STMT_END_F) != 0) {
            // Now is safe to clear ignored map (clear_tables will also
            // delete original table map events stored in the map).
            context.clearAllTables();
        }
    }

    public void setCommitKey(String commitKey) {
        this.commitKey = commitKey;
    }

    public final long getTableId() {
        return tableId;
    }

    public final TableMapLogEvent getTable() {
        return table;
    }

    public void setTable(TableMapLogEvent table) {
        this.table = table;
    }

    public final BitSet getColumns() {
        return columns;
    }

    public final BitSet getChangeColumns() {
        return changeColumns;
    }

    public final RowsLogBuffer getRowsBuf(String charsetName) {
        return new RowsLogBuffer(rowsBuf.duplicate(), columnLen, charsetName);
    }

    public final int getFlags(final int flags) {
        return this.flags & flags;
    }

    public int getFlags() {
        return flags;
    }

    public int getColumnLen() {
        return columnLen;
    }

    @Override
    public String getCommitLogInfo() {
        return commitKey;
    }
}
