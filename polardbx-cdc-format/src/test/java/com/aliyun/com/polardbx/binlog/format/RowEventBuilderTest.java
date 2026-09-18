/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.SimpleField;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.Serializable;
import java.util.BitSet;

@Slf4j
public class RowEventBuilderTest {
    @Test
    public void testWriteEvent() {
        RowEventBuilder rowEventBuilder = Mockito.mock(RowEventBuilder.class, InvocationOnMock::callRealMethod);
        AutoExpandBuffer autoExpandBuffer = new AutoExpandBuffer(1024, 1024);
        byte[] data = new byte[0];
        Field value = new SimpleField(data, MySQLType.MYSQL_TYPE_JSON.getType(), 4);
        rowEventBuilder.writeBytes(autoExpandBuffer, value);
        Assert.assertEquals(4, autoExpandBuffer.size());
    }

    @Test
    @SneakyThrows
    public void testParseDml() {
        //dd if=binlog.000030 bs=1 skip=60987 count=106 2>/dev/null | hexdump -v -e '106/1 "%02x"'
        log.info("parse insert event with table map...");
        String insertHex =
            "71f4af6813cce02d183a00000075ee000000002100000000000100067a696d69616e000a746573745f6a736f6e33000303f5030104045f2ac0a471f4af681ecce02d1830000000a5ee00000000210000000000030002000307000200000000000000010000003fe8363c";
        parseDml(insertHex);
    }

    public void parseDml(String eventString) throws Exception {
        byte[] data = ByteBufUtil.decodeHexDump(eventString);
        parseDml(data);
    }

    public void parseDml(byte[] data) throws Exception {
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 0));
        logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        LogBuffer logBuffer = new LogBuffer(data, 0, data.length);
        TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) logDecoder.decode(logBuffer, logContext);
        RowsLogEvent event = (RowsLogEvent) logDecoder.decode(logBuffer, logContext);
        log.info("insert type:{}, length:{}, checksum:{}", event.getHeader().getType(), event.getHeader().getEventLen(),
            event.getHeader().getChecksumAlg());
        int columnCnt = event.getTable().getColumnCnt();
        TableMapLogEvent.ColumnInfo[] columnInfos = event.getTable().getColumnInfo();
        RowsLogBuffer rowsLogBuffer = event.getRowsBuf("utf-8");
        BitSet columns = event.getColumns();
        printRowValues(rowsLogBuffer, columnCnt, columns, columnInfos);
    }

    public void printRowValues(RowsLogBuffer rowsLogBuffer, int columnCount, BitSet columns,
                               TableMapLogEvent.ColumnInfo[] columnInfos) {
        String[] checkValues = new String[] {"2", "null", "1"};
        while (rowsLogBuffer.nextOneRow(columns)) {
            BitSet nullBits = rowsLogBuffer.getNullBits(); // 获取 NULL BITMAP
            for (int i = 0; i < columnCount; i++) {
                TableMapLogEvent.ColumnInfo info = columnInfos[i];
                if (nullBits.get(i)) {
                    log.info("Column {}: NULL", i);
                } else {
                    Serializable value = rowsLogBuffer.nextValue(info.type, info.meta);
                    Assert.assertEquals(checkValues[i], value.toString());
                    log.info("Column {}: {}", i, value);
                }
            }
        }
    }
}
