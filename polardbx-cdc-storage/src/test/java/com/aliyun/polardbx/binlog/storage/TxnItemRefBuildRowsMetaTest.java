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
import com.aliyun.polardbx.binlog.canal.binlog.event.RowDataHashCode;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEventMeta;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * 用于调试 TxnItemRef#buildRowsMeta 方法。
 * <p>
 * 使用姿势：将完整的 hex 字符串（TableMap event + RowsLog event 拼接）作为入参传入。
 * tableMapLength 指明 TableMap event 占用的字节数，用于将 RowsLog event 部分切出来赋给 TxnItemRef。
 * </p>
 */
@Slf4j
public class TxnItemRefBuildRowsMetaTest extends BaseTest {

    /**
     * 构造 LogContext（含 FormatDescription 与 ServerCharactorSet），解析 TableMap，
     * 再用 RowsLog 字节构造 TxnItemRef 并调用 buildRowsMeta，打印每行的 hashCode 信息。
     *
     * @param tableMapHex TableMap event 的十六进制字符串
     * @param rowsHex RowsLog event 的十六进制字符串
     * @return buildRowsMeta 返回的 RowsLogEventMeta
     */
    @SneakyThrows
    private RowsLogEventMeta buildRowsMetaFromHex(String tableMapHex, String rowsHex) {
        byte[] tableMapData = ByteBufUtil.decodeHexDump(tableMapHex);
        byte[] rowsPayload = ByteBufUtil.decodeHexDump(rowsHex);

        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT, DecodeMode.PART_RETURNING);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 4));
        logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        // 先解析 TableMap event，将 table 注册到 logContext
        LogBuffer tableMapBuffer = new LogBuffer(tableMapData, 0, tableMapData.length);
        logDecoder.decode(tableMapBuffer, logContext);

        // 构造 TxnItemRef 并写入 rawPayload
        TxnItemRef txnItemRef = new TxnItemRef();
        txnItemRef.setRawPayload(rowsPayload);

        // 解析 RowsLog event
        LogBuffer rowsBuffer = new LogBuffer(rowsPayload, 0, rowsPayload.length);
        RowsLogEvent rowsLogEvent = (RowsLogEvent) logDecoder.decode(rowsBuffer, logContext);
        Assert.assertNotNull("RowsLogEvent 解析结果不应为 null，请检查 rowsHex 数据是否正确", rowsLogEvent);

        // 调用被测方法
        RowsLogEventMeta meta = txnItemRef.buildRowsMeta(rowsLogEvent);

        // 打印调试信息
        List<RowDataHashCode> hashCodes = meta.getRowDataHashCodes();
        log.info("tableId={}, payloadOffset={}, rowCount={}",
            meta.getTableId(), meta.getPayloadOffset(), hashCodes.size());
        for (int i = 0; i < hashCodes.size(); i++) {
            RowDataHashCode hc = hashCodes.get(i);
            log.info("  row[{}]: offset={}, length={}, hashCode={}",
                i, hc.getRowOffset(), hc.getLength(), hc.getHashCode());
            if (hc.getAfter() != null) {
                RowDataHashCode after = hc.getAfter();
                log.info("    after: offset={}, length={}, hashCode={}",
                    after.getRowOffset(), after.getLength(), after.getHashCode());
            }
        }

        return meta;
    }

    /**
     * 使用 RowsLogEventCompareUtilTest 中现有的 delete 事件数据验证 buildRowsMeta 方法的正确性。
     * <p>
     * hex 数据来自：
     * binlog.000010 pos=2291 Table_map (zimian.simplet)  length=56
     * binlog.000010 pos=2347 Delete_rows flags: STMT_END_F  length=53
     * </p>
     */
    @Test
    public void testBuildRowsMetaWithDeleteEvent() {
        String tableMapHex =
            "00a87868135b060000380000002b09000000006200000000000100067a696d69616e000773696d706c6574000203030002010100d6e955c5";
        String rowsHex =
            "00a87868205b060000350000006009000000006200000000000100020002ff0003000000030000000004000000040000005a49b458";

        RowsLogEventMeta meta = buildRowsMetaFromHex(tableMapHex, rowsHex);

        // 基本断言
        Assert.assertNotNull(meta);
        Assert.assertEquals("tableId 应为 98", 98L, meta.getTableId());
        Assert.assertFalse("至少应解析出一行数据", meta.getRowDataHashCodes().isEmpty());
    }

    /**
     * 使用 RowsLogEventCompareUtilTest 中现有的 insert 事件数据验证 buildRowsMeta 方法的正确性。
     * <p>
     * hex 数据来自：
     * binlog.000010 pos=1995 Table_map (zimian.simplet)  length=56
     * binlog.000010 pos=2051 Write_rows flags: STMT_END_F  length=53
     * </p>
     */
    @Test
    public void testBuildRowsMetaWithInsertEvent() {
        String tableMapHex =
            "e3a77868135b060000380000000308000000006200000000000100067a696d69616e000773696d706c6574000203030002010100f471ee05";
        String rowsHex =
            "e3a778681e5b060000350000003808000000006200000000000100020002ff0003000000030000000004000000040000006ee3c12b";

        RowsLogEventMeta meta = buildRowsMetaFromHex(tableMapHex, rowsHex);

        Assert.assertNotNull(meta);
        Assert.assertEquals("tableId 应为 98", 98L, meta.getTableId());
        Assert.assertFalse("至少应解析出一行数据", meta.getRowDataHashCodes().isEmpty());
    }

    /**
     * 覆盖 fetchLength 中所有有效类型的全类型表 INSERT 事件验证。
     * <p>
     * 建表语句：
     * <pre>
     * CREATE TABLE test.all_types_test (
     *   id            INT AUTO_INCREMENT PRIMARY KEY,
     *   col_tinyint   TINYINT,          -- MYSQL_TYPE_TINY
     *   col_smallint  SMALLINT,         -- MYSQL_TYPE_SHORT
     *   col_mediumint MEDIUMINT,        -- MYSQL_TYPE_INT24
     *   col_int       INT,              -- MYSQL_TYPE_LONG
     *   col_bigint    BIGINT,           -- MYSQL_TYPE_LONGLONG
     *   col_decimal   DECIMAL(10,2),    -- MYSQL_TYPE_NEWDECIMAL
     *   col_float     FLOAT,            -- MYSQL_TYPE_FLOAT
     *   col_double    DOUBLE,           -- MYSQL_TYPE_DOUBLE
     *   col_bit       BIT(8),           -- MYSQL_TYPE_BIT
     *   col_timestamp TIMESTAMP NULL,   -- MYSQL_TYPE_TIMESTAMP2
     *   col_datetime  DATETIME,         -- MYSQL_TYPE_DATETIME2
     *   col_time      TIME,             -- MYSQL_TYPE_TIME2
     *   col_date      DATE,             -- MYSQL_TYPE_DATE
     *   col_year      YEAR,             -- MYSQL_TYPE_YEAR
     *   col_char      CHAR(10),         -- MYSQL_TYPE_STRING
     *   col_varchar   VARCHAR(100),     -- MYSQL_TYPE_VARCHAR
     *   col_tinytext  TINYTEXT,         -- MYSQL_TYPE_BLOB meta=1
     *   col_text      TEXT,             -- MYSQL_TYPE_BLOB meta=2
     *   col_mediumtext MEDIUMTEXT,      -- MYSQL_TYPE_BLOB meta=3
     *   col_longtext  LONGTEXT,         -- MYSQL_TYPE_BLOB meta=4
     *   col_tinyblob  TINYBLOB,         -- MYSQL_TYPE_BLOB meta=1
     *   col_blob      BLOB,             -- MYSQL_TYPE_BLOB meta=2
     *   col_enum      ENUM('a','b','c'),-- MYSQL_TYPE_ENUM
     *   col_set       SET('x','y','z'), -- MYSQL_TYPE_SET
     *   col_json      JSON,             -- MYSQL_TYPE_JSON
     *   col_geometry  GEOMETRY NOT NULL SRID 0 -- MYSQL_TYPE_GEOMETRY
     * );
     * </pre>
     * 插入值：(1, 100, 1000, 100000, 9999999, 123.45, 1.1, 2.2, b'10101010',
     * '2024-01-15 12:00:00', '2024-01-15 12:00:00', '12:00:00',
     * '2024-01-15', 2024, 'hello', 'world varchar', 'tiny text val',
     * 'text val', 'medium text val', 'long text val', 'tblob', 'blob val',
     * 'a', 'x,y', '{"k":1}', ST_GeomFromText('POINT(1 1)'))
     * </p>
     * <p>
     * hex 数据来自：binlog.000169 pos=1516 Table_map (test.all_types_test) length=127
     * pos=1643 Write_rows flags: STMT_END_F   length=242
     * </p>
     */
    @Test
    public void testBuildRowsMetaWithAllTypes() {
        String tableMapHex =
            "ca66ae69131d0900007f0000006b06000000005d000000000001000474657374000e616c6c5f74797065735f74657374001b030102090308f60405101112130a0dfe0ffcfcfcfcfcfcfefef5ff190a0204080001000000fe289001010203040102f701f8010404feffff03010200400207fcff00063f073f0701005c358421";
        String rowsHex =
            "ca66ae691e1d090000f20000005d07000000005d0000000000010002001bffffffff0000000001000000016400e80300a08601007f969800000000008000007b2dcdcc8c3f9a99999999990140aa65a4adc099b25ec00080c0002fd00f7c0568656c6c6f0d00776f726c6420766172636861720d74696e6920746578742076616c0800746578742076616c0f00006d656469756d20746578742076616c0d0000006c6f6e6720746578742076616c0574626c6f620800626c6f622076616c01030d0000000001000c000b0001000501006b19000000000000000101000000000000000000f03f000000000000f03fae82c25b";

        RowsLogEventMeta meta = buildRowsMetaFromHex(tableMapHex, rowsHex);

        Assert.assertNotNull(meta);
        Assert.assertEquals("tableId 应为 93", 93L, meta.getTableId());
        Assert.assertFalse("至少应解析出一行数据", meta.getRowDataHashCodes().isEmpty());
        Assert.assertEquals("应解析出 1 行", 1, meta.getRowDataHashCodes().size());
        log.info("全类型表解析完成：共解析 {} 行", meta.getRowDataHashCodes().size());
    }

    /**
     * 自定义 hex 入口，用于传入任意 hex 字符串进行 debug。
     * <p>
     * 使用方式：将真实抓包或 mysqlbinlog 导出的 tableMapHex 和 rowsHex 分别填入，然后直接运行此测试。
     * </p>
     */
    @Test
    public void testBuildRowsMetaDebug() {
        // ===== 在此处分别替换 TableMap 和 RowsLog 的 hex 字符串 =====
        String tableMapHex =
            "a9d5a769137368d85d71000000419f94150000aa000000000001000a6d63315f70303030303000227265706c6163655f6669785f746573745f73696d706c655f653238325f3030303036000c0808030801f6f6010f11110f0a2010201000010303000100050101000203fcff000022b814";
        String rowsHex =
            "a9d5a769197368d85d97000000d89f94150000aa000000000001000cffff000085bf1b0000000000bcc7040000000000130000004111000000000000018000000000000353000000000000000080000000000001c700000000000000000220006a62666b67337770396c7371686a7062796a7771656474326f39746a31776c6867831c411aae67831c411aae05006773695f318a1a56eb";
        // ============================================================

        RowsLogEventMeta meta = buildRowsMetaFromHex(tableMapHex, rowsHex);
        Assert.assertNotNull(meta);

        List<RowDataHashCode> hashCodes = meta.getRowDataHashCodes();
        log.info("Debug 完成：共解析 {} 行", hashCodes.size());
    }
}
