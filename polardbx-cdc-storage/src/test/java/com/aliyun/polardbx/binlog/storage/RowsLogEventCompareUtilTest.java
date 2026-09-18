/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.canal.binlog.DecodeMode;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.storage.util.RowsLogEventCompareCode;
import com.aliyun.polardbx.binlog.storage.util.RowsLogEventCompareUtil;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import static java.util.Arrays.copyOfRange;

@Slf4j
public class RowsLogEventCompareUtilTest extends BaseTest {
    /**
     * | binlog.000010 | 2291 | Table_map      | 1627      | 2347        | table_id: 98 (zimian.simplet)   length: 56
     * |
     * | binlog.000010 | 2347 | Delete_rows    | 1627      | 2400        | table_id: 98 flags: STMT_END_F  length: 53
     * <p>
     * | binlog.000010 | 1995 | Table_map      | 1627      | 2051        | table_id: 98 (zimian.simplet)   length: 56
     * |
     * | binlog.000010 | 2051 | Write_rows     | 1627      | 2104        | table_id: 98 flags: STMT_END_F  length: 53
     */
    @Test
    @SneakyThrows
    public void testPayloadPartEquals() {
        String deleteHex =
            "00a87868135b060000380000002b09000000006200000000000100067a696d69616e000773696d706c6574000203030002010100d6e955c500a87868205b060000350000006009000000006200000000000100020002ff0003000000030000000004000000040000005a49b458";
        String insertHex =
            "e3a77868135b060000380000000308000000006200000000000100067a696d69616e000773696d706c6574000203030002010100f471ee05e3a778681e5b060000350000003808000000006200000000000100020002ff0003000000030000000004000000040000006ee3c12b";
        testCompareEventAndRemoveEqualPart(deleteHex, insertHex, 56, RowsLogEventCompareCode.TOTALLY_EQUAL);

    }

    @SneakyThrows
    private void testCompareEventAndRemoveEqualPart(String hex1, String hex2, int tableMapLength,
                                                    RowsLogEventCompareCode expectedCode) {
        TxnItemRef delete = new TxnItemRef();
        TxnItemRef insert = new TxnItemRef();
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT, DecodeMode.PART_RETURNING);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 4));
        logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        byte[] deleteData = ByteBufUtil.decodeHexDump(hex1);
        byte[] insertData = ByteBufUtil.decodeHexDump(hex2);
        LogBuffer logBuffer = new LogBuffer(deleteData, 0, deleteData.length);
        // 先解析一个table map
        logDecoder.decode(logBuffer, logContext);
        deleteData = copyOfRange(deleteData, tableMapLength, deleteData.length);
        insertData = copyOfRange(insertData, tableMapLength, insertData.length);
        delete.setRawPayload(deleteData);
        insert.setRawPayload(insertData);
        RowsLogEventCompareCode code =
            RowsLogEventCompareUtil.compareEventAndRemoveEqualPart(delete, insert, logDecoder, logContext);
        log.info(code.toString());
        if (expectedCode != null) {
            Assert.assertEquals(code, expectedCode);
        }
    }

    @Test
    @SneakyThrows
    public void testPayloadPartEqualsAndGenerateDeleteEvent() {

    }
}
