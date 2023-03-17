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
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogChecksumAlgConsts;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.APPEND_BLOCK_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.BEGIN_LOAD_QUERY_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.DELETE_FILE_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.EXECUTE_LOAD_QUERY_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.FORMAT_DESCRIPTION_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.IGNORABLE_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.INCIDENT_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.INTVAR_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.QUERY_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.RAND_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.ROTATE_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.ROWS_HEADER_LEN_V1;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.ROWS_HEADER_LEN_V2;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.STOP_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.TABLE_MAP_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.TRANSACTION_CONTEXT_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.USER_VAR_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.VIEW_CHANGE_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.XA_PREPARE_HEADER_LEN;
import static com.aliyun.polardbx.binlog.format.EnumPostHeaderLength.XID_HEADER_LEN;

public class FormatDescriptionEvent extends BinlogBuilder {

    public static final byte[] EVENT_HEADER_LENGTH = {
        0, (byte) QUERY_HEADER_LEN.getLength(),
        (byte) STOP_HEADER_LEN.getLength(),
        (byte) ROTATE_HEADER_LEN.getLength(),
        (byte) INTVAR_HEADER_LEN.getLength(), 0,
        /*
          Unused because the code for Slave log event was removed.
          (15th Oct. 2010)
        */
        0, 0, (byte) APPEND_BLOCK_HEADER_LEN.getLength(), 0,
        (byte) DELETE_FILE_HEADER_LEN.getLength(), 0,
        (byte) RAND_HEADER_LEN.getLength(),
        (byte) USER_VAR_HEADER_LEN.getLength(),
        (byte) FORMAT_DESCRIPTION_HEADER_LEN.getLength(),
        (byte) XID_HEADER_LEN.getLength(),
        (byte) BEGIN_LOAD_QUERY_HEADER_LEN.getLength(),
        (byte) EXECUTE_LOAD_QUERY_HEADER_LEN.getLength(),
        (byte) TABLE_MAP_HEADER_LEN.getLength(),
        /*The PRE_GA events are never be written to any binlog, but
             their lengths are included in Format_description_log_event.
             Hence, we need to be assign some value here, to avoid reading
             uninitialized memory when the array is written to disk.
            */
        0, 0, 0, (byte) ROWS_HEADER_LEN_V1.getLength(),
        /* WRITE_ROWS_EVENT_V1*/
        (byte) ROWS_HEADER_LEN_V1.getLength(),
        /* UPDATE_ROWS_EVENT_V1*/
        (byte) ROWS_HEADER_LEN_V1.getLength(),
        /* DELETE_ROWS_EVENT_V1*/
        (byte) INCIDENT_HEADER_LEN.getLength(), 0,
        /* HEARTBEAT_LOG_EVENT*/
        (byte) IGNORABLE_HEADER_LEN.getLength(),
        (byte) IGNORABLE_HEADER_LEN.getLength(),
        (byte) ROWS_HEADER_LEN_V2.getLength(),
        (byte) ROWS_HEADER_LEN_V2.getLength(),
        (byte) ROWS_HEADER_LEN_V2.getLength(),
        (byte) GridEvent.POST_HEADER_LENGTH, /*GTID_EVENT*/
        (byte) GridEvent.POST_HEADER_LENGTH,
        /*ANONYMOUS_GTID_EVENT*/
        (byte) IGNORABLE_HEADER_LEN.getLength(),
        (byte) TRANSACTION_CONTEXT_HEADER_LEN.getLength(),
        (byte) VIEW_CHANGE_HEADER_LEN.getLength(),
        (byte) XA_PREPARE_HEADER_LEN.getLength()};
    /**
     * 用于指示二进制日志文件是否已正确关闭。此标志仅对有意义 FORMAT_DESCRIPTION_EVENT。将事件写入日志文件时设置。以后关闭日志文件时，将清除该标志。（这是MySQL修改二进制日志文件中已写入部分的唯一情况）。
     */
    private static final int LOG_EVENT_BINLOG_IN_USE_F = 0x1;
    /**
     * Payload begin
     */
    private short binlogVersion;
    private String mysqlServerVersion;
    private int commonHeaderLength = 19;
    /**
     * 0 is off checkAlg
     * > 5.6.1 会有校验
     * 我们默认产生5.7 binlog，所以磁开关一定会有
     */
    private int checkSumAlg = BinlogChecksumAlgConsts.BINLOG_CHECKSUM_ALG_CRC32;

    /**
     * binlogVersion: 4
     * mysqlServerVersion: Splits server 'version' string into three numeric pieces stored
     * into 'split_versions':
     * X.Y.Zabc (X,Y,Z numbers, a not a digit) -> {X,Y,Z}
     * X.Yabc -> {X,Y,0}
     */
    public FormatDescriptionEvent(short binlogVersion, String mysqlServerVersion, long serverId) {
        this.binlogVersion = binlogVersion;
        this.serverId = serverId;
        this.mysqlServerVersion = mysqlServerVersion;
        this.timestamp = (int) (System.currentTimeMillis() / 1000);
        this.eventType = BinlogEventType.FORMAT_DESCRIPTION_EVENT.getType();
//        this.flags = LOG_EVENT_BINLOG_IN_USE_F;//去掉flag配置
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, checkSumAlg, INT8);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, binlogVersion, INT16);
        writeFixString(outputData, mysqlServerVersion, 50, ISO_8859_1);
        numberToBytes(outputData, timestamp, INT32);
        numberToBytes(outputData, commonHeaderLength, INT8);
        writeBytes(outputData, EVENT_HEADER_LENGTH);
    }
}
