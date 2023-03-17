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
package com.aliyun.polardbx.binlog.format.utils;

public enum BinlogEventType {
    UNKNOWN_EVENT(0, 0), START_EVENT_V3(1, 2 + 50 + 4), QUERY_EVENT(2, 4 + 4 + 1 + 2 + 2), STOP_EVENT(3,
        0), ROTATE_EVENT(4, 8), INTVAR_EVENT(5, 0), LOAD_EVENT(6, 4 + 4 + 4 + 1 + 1 + 4), SLAVE_EVENT(7,
        0), CREATE_FILE_EVENT(8, 4), APPEND_BLOCK_EVENT(9, 4), EXEC_LOAD_EVENT(10, 4), DELETE_FILE_EVENT(11,
        4), NEW_LOAD_EVENT(12, 4 + 4 + 4 + 1 + 1 + 4), RAND_EVENT(13, 0), USER_VAR_EVENT(14,
        0), FORMAT_DESCRIPTION_EVENT(15, 2 + 50 + 4 + 1), XID_EVENT(16, 0), BEGIN_LOAD_QUERY_EVENT(17,
        4), EXECUTE_LOAD_QUERY_EVENT(18, 4 + 4 + 1 + 2 + 2 + 4 + 4 + 4 + 1), TABLE_MAP_EVENT(19,
        6 + 2), PRE_GA_WRITE_ROWS_EVENT(20, 6 + 2), PRE_GA_UPDATE_ROWS_EVENT(21,
        6 + 2), PRE_GA_DELETE_ROWS_EVENT(22, 6 + 2), WRITE_ROWS_EVENT_V1(23, 6 + 2), UPDATE_ROWS_EVENT_V1(24,
        6 + 2), DELETE_ROWS_EVENT_V1(25, 6 + 2), INCIDENT_EVENT(26, 1 + 1), HEARTBEAT_LOG_EVENT(27,
        0), IGNORABLE_LOG_EVENT(28, 0), ROWS_QUERY_LOG_EVENT(29, 0), WRITE_ROWS_EVENT(30, 6 + 2), UPDATE_ROWS_EVENT(
        31,
        6 + 2), DELETE_ROWS_EVENT(32, 6 + 2), GTID_LOG_EVENT(33, 0), ANONYMOUS_GTID_LOG_EVENT(34,
        0), PREVIOUS_GTIDS_LOG_EVENT(35, 0), TRANSACTION_CONTEXT_EVENT(36, 0), //
    VIEW_CHANGE_EVENT(37, 0), //
    //    /* Prepared XA transaction terminal event similar to Xid */
    XA_PREPARE_LOG_EVENT(38, 0),
    //
    //    /**
    //     * Extension of UPDATE_ROWS_EVENT, allowing partial values according
    //     * to binlog_row_value_options.
    //     */
    //    PARTIAL_UPDATE_ROWS_EVENT(39),
    //
    //    TRANSACTION_PAYLOAD_EVENT(40)
    ;

    private byte type;
    private int len;

    BinlogEventType(int type, int len) {
        this.type = (byte) type;
        this.len = len;
    }

    public static BinlogEventType valueOf(int typeValue) {
        for (BinlogEventType type : values()) {
            if (type.getType() == typeValue) {
                return type;
            }
        }
        return null;
    }

    public byte getType() {
        return type;
    }

    public int getLen() {
        return len;
    }
}
