/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.BitSet;

/**
 * 针对 RowsLogBuffer#fetchLength 和 #getNextOneValueLength 的单元测试，
 * 覆盖各种 MySQL 类型（含已弃用类型、边界 meta 值、NULL 值路径等）。
 */
public class RowsLogBufferFetchLengthTest extends BaseTest {

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    /**
     * 构建一个包含指定数据的 LogBuffer。
     */
    private LogBuffer buf(byte[] data) {
        return new LogBuffer(data, 0, data.length);
    }

    /**
     * 构造最小可用的 RowsLogBuffer（1列），内置一个全 0 的 nullBits。
     * nullBitIndex 从 0 开始，首次调用 getNextOneValueLength 时读取第 0 位。
     *
     * @param nullBit 该列是否为 NULL（true=NULL，false=有值）
     * @param payload 列的二进制载荷
     */
    private RowsLogBuffer makeBuffer(boolean nullBit, byte[] payload) {
        // nullBits 占 ceil(1/8)=1 字节，全 0 表示非 NULL
        // RowsLogBuffer 构造后需手动调 nextOneRow 来填充 nullBits，
        // 这里直接构造 buffer 并通过反射设置 nullBits 更简单；
        // 但为了避免反射，使用 nextOneRow 正常流程。
        // 将 nullBits 字节和 payload 拼在一起作为完整行数据
        byte nullByte = nullBit ? (byte) 0x01 : (byte) 0x00;
        byte[] full = new byte[1 + payload.length];
        full[0] = nullByte;
        System.arraycopy(payload, 0, full, 1, payload.length);

        LogBuffer logBuffer = new LogBuffer(full, 0, full.length);
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(logBuffer, 1, "utf8");
        // 调用 nextOneRow 填充 nullBits（读取 1 列的 null bitmap，占 1 字节）
        BitSet usedColumns = new BitSet(1);
        usedColumns.set(0);
        rowsLogBuffer.nextOneRow(usedColumns);
        return rowsLogBuffer;
    }

    /**
     * 直接在已定位好的 LogBuffer 上调用 fetchLength（绕过 nullBits）。
     */
    private int fetchLength(int type, int meta, int... data) {
        byte[] bytes = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            bytes[i] = (byte) data[i];
        }
        LogBuffer logBuffer = buf(bytes);
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(logBuffer, 0, "utf8");
        return rowsLogBuffer.fetchLength(type, meta, logBuffer);
    }

    // -----------------------------------------------------------------------
    // getNextOneValueLength — NULL 值路径（第 229 行）
    // -----------------------------------------------------------------------

    /**
     * 当列值为 NULL 时，getNextOneValueLength 应返回 0，buffer 不消费任何字节。
     */
    @Test
    public void testGetNextOneValueLength_null() {
        // payload 随便填一个字节，NULL 时不应被读取
        RowsLogBuffer buf = makeBuffer(true, new byte[] {0x42});
        int len = buf.getNextOneValueLength(LogEvent.MYSQL_TYPE_LONG, 0);
        Assert.assertEquals("NULL 列长度应为 0", 0, len);
    }

    /**
     * 当列值非 NULL 时，getNextOneValueLength 应正常返回 fetchLength 结果（LONG=4）。
     */
    @Test
    public void testGetNextOneValueLength_nonNull() {
        RowsLogBuffer buf = makeBuffer(false, new byte[] {0x01, 0x00, 0x00, 0x00});
        int len = buf.getNextOneValueLength(LogEvent.MYSQL_TYPE_LONG, 0);
        Assert.assertEquals("LONG 非 NULL 列长度应为 4", 4, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_DECIMAL（已弃用，length=0）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_decimal() {
        // MYSQL_TYPE_DECIMAL 直接返回 0，不读 buffer
        int len = fetchLength(LogEvent.MYSQL_TYPE_DECIMAL, 0);
        Assert.assertEquals("MYSQL_TYPE_DECIMAL length 应为 0", 0, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_TIMESTAMP（老格式，固定 4 字节）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_timestamp_legacy() {
        int len = fetchLength(LogEvent.MYSQL_TYPE_TIMESTAMP, 0,
            0x00, 0x00, 0x00, 0x00);
        Assert.assertEquals("MYSQL_TYPE_TIMESTAMP 应为 4 字节", 4, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_DATETIME（老格式，固定 8 字节）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_datetime_legacy() {
        int len = fetchLength(LogEvent.MYSQL_TYPE_DATETIME, 0,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        Assert.assertEquals("MYSQL_TYPE_DATETIME 应为 8 字节", 8, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_TIME（老格式，固定 3 字节）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_time_legacy() {
        int len = fetchLength(LogEvent.MYSQL_TYPE_TIME, 0,
            0x00, 0x00, 0x00);
        Assert.assertEquals("MYSQL_TYPE_TIME 应为 3 字节", 3, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_NEWDATE（内部枚举，不应出现在 binlog 中，length=0）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_newdate() {
        int len = fetchLength(LogEvent.MYSQL_TYPE_NEWDATE, 0);
        Assert.assertEquals("MYSQL_TYPE_NEWDATE length 应为 0", 0, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_TINY_BLOB / MEDIUM_BLOB / LONG_BLOB
    // 这些内部枚举在 binlog 中不应出现，switch 无 break 会 fall-through 到 BLOB。
    // fall-through 到 BLOB case 时会按 meta 读取长度。
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_tinyBlob_fallthrough() {
        // fall-through 到 BLOB，meta=1 => 读 1 字节作为长度
        // payload: 长度字节=3, 然后3个数据字节
        int len = fetchLength(LogEvent.MYSQL_TYPE_TINY_BLOB, 1,
            0x03, 0x61, 0x62, 0x63);
        // tmpOffset=1, length=3 => 总共4
        Assert.assertEquals("TINY_BLOB fall-through meta=1，总长度应为 4", 4, len);
    }

    @Test
    public void testFetchLength_mediumBlob_fallthrough() {
        // fall-through 到 BLOB，meta=1
        int len = fetchLength(LogEvent.MYSQL_TYPE_MEDIUM_BLOB, 1,
            0x02, 0x61, 0x62);
        Assert.assertEquals("MEDIUM_BLOB fall-through meta=1，总长度应为 3", 3, len);
    }

    @Test
    public void testFetchLength_longBlob_fallthrough() {
        // fall-through 到 BLOB，meta=1
        int len = fetchLength(LogEvent.MYSQL_TYPE_LONG_BLOB, 1,
            0x01, 0x61);
        Assert.assertEquals("LONG_BLOB fall-through meta=1，总长度应为 2", 2, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_BLOB — 覆盖所有 meta（1/2/3/4）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_blob_meta1() {
        // meta=1: 读 1 字节长度，值=5，再加 5 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_BLOB, 1,
            0x05, 0x01, 0x02, 0x03, 0x04, 0x05);
        Assert.assertEquals("BLOB meta=1，总长度应为 6", 6, len);
    }

    @Test
    public void testFetchLength_blob_meta2() {
        // meta=2: 读 2 字节 LE 长度，值=3，再加 3 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_BLOB, 2,
            0x03, 0x00, 0x61, 0x62, 0x63);
        Assert.assertEquals("BLOB meta=2，总长度应为 5", 5, len);
    }

    @Test
    public void testFetchLength_blob_meta3() {
        // meta=3: 读 3 字节 LE 长度，值=2，再加 2 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_BLOB, 3,
            0x02, 0x00, 0x00, 0x41, 0x42);
        Assert.assertEquals("BLOB meta=3，总长度应为 5", 5, len);
    }

    @Test
    public void testFetchLength_blob_meta4() {
        // meta=4: 读 4 字节 LE 长度，值=2，再加 2 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_BLOB, 4,
            0x02, 0x00, 0x00, 0x00, 0x41, 0x42);
        Assert.assertEquals("BLOB meta=4，总长度应为 6", 6, len);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFetchLength_blob_invalidMeta() {
        // meta=5 不合法，应抛出 IllegalArgumentException
        fetchLength(LogEvent.MYSQL_TYPE_BLOB, 5, 0x01);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_VARCHAR — meta < 256（1 字节长度）与 meta >= 256（2 字节长度）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_varchar_metaSmall() {
        // meta=100 < 256 => 读 1 字节长度=5，加 5 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_VARCHAR, 100,
            0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f);
        Assert.assertEquals("VARCHAR meta<256，总长度应为 6", 6, len);
    }

    @Test
    public void testFetchLength_varchar_metaLarge() {
        // meta=300 >= 256 => 读 2 字节 LE 长度=3，加 3 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_VARCHAR, 300,
            0x03, 0x00, 0x61, 0x62, 0x63);
        Assert.assertEquals("VARCHAR meta>=256，总长度应为 5", 5, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_STRING — meta < 256（简单 CHAR）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_string_metaSmall() {
        // meta=10 < 256 => len=meta=10, 然后进 STRING case，读 1 字节长度=5，加 5 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, 10,
            0x05, 0x68, 0x65, 0x6c, 0x6c, 0x6f);
        Assert.assertEquals("STRING meta<256，总长度应为 6", 6, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_STRING — meta >= 256，(byte0 & 0x30) == 0x30 分支
    // byte0 = MYSQL_TYPE_SET(0xf8) | 0x30 = 0xf8，不满足 != 0x30，走 switch
    // 取 byte0=MYSQL_TYPE_ENUM(0xf7)，(0xf7 & 0x30)=0x30 => 走 switch(ENUM 分支)
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_string_meta256_enumBranch() {
        // byte0=0xf7(MYSQL_TYPE_ENUM), byte1=1 => len=1，type=ENUM，length=1
        int meta = (LogEvent.MYSQL_TYPE_ENUM << 8) | 0x01;
        // ENUM case：length=len=1，data 1 字节
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, meta,
            0x61);
        Assert.assertEquals("STRING meta>=256 ENUM 分支，总长度应为 1", 1, len);
    }

    @Test
    public void testFetchLength_string_meta256_setBranch() {
        // byte0=0xf8(MYSQL_TYPE_SET), byte1=1 => len=1，type=SET，nbits=8，length=1
        int meta = (LogEvent.MYSQL_TYPE_SET << 8) | 0x01;
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, meta,
            0x01);
        Assert.assertEquals("STRING meta>=256 SET 分支，总长度应为 1", 1, len);
    }

    @Test
    public void testFetchLength_string_meta256_varStringBranch() {
        // byte0=0xfd(MYSQL_TYPE_VAR_STRING), byte1=5 => len=5，type=VAR_STRING
        // VAR_STRING: len=5 < 256 => 读 1 字节长度=3，加 3 字节数据
        int meta = (LogEvent.MYSQL_TYPE_VAR_STRING << 8) | 0x05;
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, meta,
            0x03, 0x61, 0x62, 0x63);
        Assert.assertEquals("STRING meta>=256 VAR_STRING 分支，总长度应为 4", 4, len);
    }

    @Test
    public void testFetchLength_string_meta256_stringBranch() {
        // byte0=0xfe(MYSQL_TYPE_STRING), byte1=5 => len=5，type=STRING
        // STRING: len=5 < 256 => 读 1 字节长度=3，加 3 字节数据
        int meta = (LogEvent.MYSQL_TYPE_STRING << 8) | 0x05;
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, meta,
            0x03, 0x61, 0x62, 0x63);
        Assert.assertEquals("STRING meta>=256 STRING 分支，总长度应为 4", 4, len);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFetchLength_string_meta256_unknownByte0() {
        // byte0=0x40，(0x40 & 0x30)=0x00 != 0x30，走长 CHAR 分支（type=byte0|0x30=0x70=112）
        // 112 不在任何 case 中，走 default => 打印 error，length=0
        // 不抛异常；但若 byte0 满足 0x30 且不在已知 case 中则抛出 IllegalArgumentException
        // byte0=0xfe+1=0xff，(0xff & 0x30)=0x30 => 走 switch，default 分支 => 抛出异常
        int meta = (0xff << 8) | 0x05;
        fetchLength(LogEvent.MYSQL_TYPE_STRING, meta, 0x03, 0x61, 0x62, 0x63);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_STRING — meta >= 256，(byte0 & 0x30) != 0x30 的长 CHAR 分支
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_string_meta256_longCharBranch() {
        // byte0=0x40, (0x40 & 0x30)=0x00 != 0x30 => 走长 CHAR 分支
        // len = byte1 | (((byte0 & 0x30) ^ 0x30) << 4) = 0x0a | (0x30 << 4) = 0x0a | 0x300 = 778?
        // 实际：len = 0x0a | ((0x00 ^ 0x30) << 4) = 0x0a | (0x30 << 4) = 10 | 768 = 778
        // 但随后 type = 0x40 | 0x30 = 0x70 = 112，不在 switch 任何 case，走 default（length=0）
        // 所以总长度 = 0（tmpOffset=0）
        int meta = (0x40 << 8) | 0x0a;
        int len = fetchLength(LogEvent.MYSQL_TYPE_STRING, meta);
        // 进入 default，length=0，tmpOffset=0 => 返回 0
        Assert.assertEquals("STRING meta>=256 长CHAR分支 type不在switch => default，总长度 0", 0, len);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_JSON — 覆盖 meta=1/3/4（meta=2 已被全类型测试覆盖）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_json_meta1() {
        // meta=1: 读 1 字节长度=7，加 7 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_JSON, 1,
            0x07, 0x01, 0x00, 0x01, 0x00, 0x0e, 0x00, 0x0b);
        Assert.assertEquals("JSON meta=1，总长度应为 8", 8, len);
    }

    @Test
    public void testFetchLength_json_meta3() {
        // meta=3: 读 3 字节 LE 长度=2，加 2 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_JSON, 3,
            0x02, 0x00, 0x00, 0x01, 0x02);
        Assert.assertEquals("JSON meta=3，总长度应为 5", 5, len);
    }

    @Test
    public void testFetchLength_json_meta4() {
        // meta=4: 读 4 字节 LE 长度=2，加 2 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_JSON, 4,
            0x02, 0x00, 0x00, 0x00, 0x01, 0x02);
        Assert.assertEquals("JSON meta=4，总长度应为 6", 6, len);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFetchLength_json_invalidMeta() {
        fetchLength(LogEvent.MYSQL_TYPE_JSON, 5, 0x01);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_GEOMETRY — 覆盖 meta=1/2/3（meta=4 已被全类型测试覆盖）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_geometry_meta1() {
        // meta=1: 读 1 字节长度=4，加 4 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_GEOMETRY, 1,
            0x04, 0x01, 0x02, 0x03, 0x04);
        Assert.assertEquals("GEOMETRY meta=1，总长度应为 5", 5, len);
    }

    @Test
    public void testFetchLength_geometry_meta2() {
        // meta=2: 读 2 字节 LE 长度=3，加 3 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_GEOMETRY, 2,
            0x03, 0x00, 0x01, 0x02, 0x03);
        Assert.assertEquals("GEOMETRY meta=2，总长度应为 5", 5, len);
    }

    @Test
    public void testFetchLength_geometry_meta3() {
        // meta=3: 读 3 字节 LE 长度=2，加 2 字节数据
        int len = fetchLength(LogEvent.MYSQL_TYPE_GEOMETRY, 3,
            0x02, 0x00, 0x00, 0x01, 0x02);
        Assert.assertEquals("GEOMETRY meta=3，总长度应为 5", 5, len);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFetchLength_geometry_invalidMeta() {
        fetchLength(LogEvent.MYSQL_TYPE_GEOMETRY, 5, 0x01);
    }

    // -----------------------------------------------------------------------
    // MYSQL_TYPE_BOOL / MYSQL_TYPE_INVALID / default（未知类型）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_bool_returnsZero() {
        // BOOL 走 default，打印 error，length=0
        int len = fetchLength(LogEvent.MYSQL_TYPE_BOOL, 0);
        Assert.assertEquals("MYSQL_TYPE_BOOL 应返回 0", 0, len);
    }

    @Test
    public void testFetchLength_invalid_returnsZero() {
        // INVALID 走 default，length=0
        int len = fetchLength(LogEvent.MYSQL_TYPE_INVALID, 0);
        Assert.assertEquals("MYSQL_TYPE_INVALID 应返回 0", 0, len);
    }

    @Test
    public void testFetchLength_unknownType_returnsZero() {
        // 完全未知的 type，走 default，length=0
        int len = fetchLength(99, 0);
        Assert.assertEquals("未知 type 应返回 0", 0, len);
    }

    // -----------------------------------------------------------------------
    // SET — nbits=1 时 length=1（走非大值分支）
    // -----------------------------------------------------------------------

    @Test
    public void testFetchLength_set_singleBit() {
        // meta & 0xFF = 0 => nbits=0，(0+7)/8=0，nbits<=1 => length=1
        int len = fetchLength(LogEvent.MYSQL_TYPE_SET, 0, 0x01);
        Assert.assertEquals("SET nbits<=1，length 应为 1", 1, len);
    }
}
