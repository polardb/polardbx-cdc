/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;

@Slf4j
public class TxnBufferTest extends BaseTest {

    @Test
    public void testMerge() {
        int size = 10;
        int count = 2;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        List<TxnBuffer> buffers = buildBufferList(size, count);

        // 验证个数是否一致
        buffers.forEach(txnBuffer::merge);
        Assert.assertEquals(size * (count + 1), txnBuffer.itemSize());

        // 验证是否有序
        final TxnItemRef lastRef = new TxnItemRef(txnBuffer, "", "", 19, new byte[10],
            null, null, 0, null);
        lastRef.setPartitionId("");
        txnBuffer.iterator().forEachRemaining(i -> {
            i.setPartitionId("");
            int result = i.compareTo(lastRef);
            Assert.assertTrue(result > 0);
        });
    }

    @Test
    public void testSeek() {
        int size = 1001;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        int index = (int) (Math.random() * (size - 1));
        TxnItemRef seed = txnBuffer.getItemRef(index);

        boolean result = txnBuffer.seek(seed);
        Assert.assertTrue(result);
        Assert.assertFalse(txnBuffer.seek(
            new TxnItemRef(txnBuffer, UUID.randomUUID().toString(), "", 19, new byte[10],
                null, null, 0, null)));
    }

    @Test
    public void testMergePerformance() {
        int size = 100;
        int count = 1024;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        List<TxnBuffer> buffers = buildBufferList(size, count);

        long startTime = System.currentTimeMillis();
        buffers.forEach(txnBuffer::merge);
        long endTime = System.currentTimeMillis();
        System.out.println("cost time: " + (endTime - startTime));
    }

    private List<TxnBuffer> buildBufferList(int size, int count) {
        ArrayList<TxnBuffer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildOneBuffer(size));
        }
        return result;
    }

    private TxnBuffer buildOneBuffer(int size) {
        TxnBuffer txnBuffer = new TxnBuffer(new TxnKey(System.nanoTime(), UUID.randomUUID().toString()), null);
        txnBuffer.setReturningFixEnabled(true);
        txnBuffer.markStart();

        for (int i = 0; i < size; i++) {
            int eventType;
            if (i == 0) {
                eventType = TABLE_MAP_EVENT;
            } else {
                eventType = WRITE_ROWS_EVENT;
            }
            TxnBufferItem txnItem = TxnBufferItem.builder()
                .traceId("00001")
                .eventType(eventType)
                .payload(new byte[0])
                .eventType(i % 2 == 0 ? LogEvent.TABLE_MAP_EVENT : LogEvent.WRITE_ROWS_EVENT)
                .build();
            txnBuffer.push(txnItem);
        }
        txnBuffer.markComplete();
        txnBuffer.setPartitionId("");
        return txnBuffer;
    }

    /**
     * 测试returning delete相关逻辑
     * dd if=binlog.000107 bs=1 skip=1039 count=62 2>/dev/null | hexdump -v -e '62/1 "%02x"'
     */
    @Test
    public void testDoAddInsertLeftContains() {
        // Test case 1: Insert and delete operations with partial returning
        // insert (2,2),(1,1), insert(3,3), delete(2,2)
        String[] hexes = {
            "57848868135b060000380000009002000000006200000000000100067a696d69616e000773696d706c6574000203030002010100e04d6590",
            "578488681e5b06000035000000c502000000006200000000000100020002ff000200000002000000000100000001000000f010c865",
            "62848868135b06000038000000b803000000006200000000000100067a696d69616e000773696d706c657400020303000201010062d35a08",
            "628488681e5b0600002c000000e403000000006200000000000100020002ff000300000003000000ce447e87",
            "6b848868135b06000038000000d704000000006200000000000100067a696d69616e000773696d706c657400020303000201010048bfe90d",
            "6b848868205b0600002c0000000305000000006200000000000100020002ff00020000000200000019e0fa0a"
        };
        int[] eventTypes =
            {TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, DELETE_ROWS_EVENT};
        addHexToTxnBuffer(hexes, eventTypes);
    }

    @Test
    public void testDoAddInsertEquals() {
        // Test case 2: Insert and delete operations with total returning
        // insert(11,11),(12,12), insert(14,14), delete(11,11),(12,12)
        String[] hexes = {
            "ef7ea568135b060000380000009002000000006200000000000100067a696d69616e000773696d706c65740002030300020101009d997baf",
            "ef7ea5681e5b06000035000000c502000000006200000000000100020002ff000b0000000b000000000c0000000c0000004b7099c1",
            "047fa568135b06000038000000b803000000006200000000000100067a696d69616e000773696d706c6574000203030002010100214d12a1",
            "047fa5681e5b0600002c000000e403000000006200000000000100020002ff000e0000000e0000005288612b",
            "137fa568135b06000038000000d704000000006200000000000100067a696d69616e000773696d706c65740002030300020101001b1c85d9",
            "137fa568205b060000350000000c05000000006200000000000100020002ff000b0000000b000000000c0000000c000000d067adf5"
        };
        int[] eventTypes =
            {TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, DELETE_ROWS_EVENT};
        addHexToTxnBuffer(hexes, eventTypes); // Reuse eventTypes1 as they're identical
    }

    @Test
    public void testDoAddInsertRightContains() {
        // Test case 2: Insert and delete operations with total returning
        // insert 1,2, insert 3, delete 1,2,3,4
        // dd if=binlog.000110 bs=1 skip=1575 count=71 2>/dev/null | hexdump -v -e '71/1 "%02x"'
        // expected: delete 4
        String[] hexes = {
            "c9795369135b06000038000000c102000000005b00000000000100067a696d69616e000773696d706c6574000203030002010100350f6f56",
            "c97953691e5b06000035000000f602000000005b00000000000100020002ff00010000000100000000020000000200000028afec1c",
            "d0795369135b06000038000000e903000000005b00000000000100067a696d69616e000773696d706c65740002030300020101000234621e",
            "d07953691e5b0600002c0000001504000000005b00000000000100020002ff00030000000300000083aff0b3",
            "fb795369135b060000380000002706000000005b00000000000100067a696d69616e000773696d706c6574000203030002010100e9c9fd33",
            "fb795369205b060000470000006e06000000005b00000000000100020002ff000100000001000000000200000002000000000300000003000000000400000004000000f5fba9aa"
        };
        int[] eventTypes =
            {TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, WRITE_ROWS_EVENT, TABLE_MAP_EVENT, DELETE_ROWS_EVENT};
        addHexToTxnBuffer(hexes, eventTypes);
    }

    /**
     * 测试returning delete相关逻辑
     */
    @Test
    public void testDoAddUpdate() {
        // Test case 3: Update and delete operations with returning
        // update c1 = 10 where c0 = 1, update c1 = 10 where c0 = 3, delete c0 = 1
        log.info("###### test update delete returning ######");
        String[] hexes = {
            "a7f89268135b060000380000007a01000000006200000000000100067a696d69616e000773696d706c6574000203030002010100db6cf2fe",
            "a7f892681f5b06000036000000b001000000006200000000000100020002ffff00010000000100000000010000000a000000d4ec60d6",
            "b0f89268135b06000038000000ac02000000006200000000000100067a696d69616e000773696d706c657400020303000201010043139898",
            "b0f892681f5b06000036000000e202000000006200000000000100020002ffff00030000000300000000030000000a000000a446b1ab",
            "f4f89268135b06000038000000d503000000006200000000000100067a696d69616e000773696d706c6574000203030002010100c077ea0d",
            "f4f89268205b0600002c0000000104000000006200000000000100020002ff00010000000a000000d0eead65"
        };
        int[] eventTypes = {
            TABLE_MAP_EVENT, UPDATE_ROWS_EVENT, TABLE_MAP_EVENT, UPDATE_ROWS_EVENT, TABLE_MAP_EVENT, DELETE_ROWS_EVENT};
        addHexToTxnBuffer(hexes, eventTypes);

    }

    /**
     * 测试returning delete相关逻辑
     */
    @Test
    public void testDoAddComplexUpdate() {
        // Test case 4: Complex update and delete operations with higher returning values
        // update c1 = 10 where c0 in (1,2), update c1 = 10 where c0 = 3, delete (1,3,4);
        // 期望fix如下:
        // delete 4, update 1,2, delete 1, update 3, delete 3,
        log.info("###### test update higher delete returning ######");
        String[] hexes = {
            "4b335269135b06000038000000a201000000005f00000000000100067a696d69616e000773696d706c657400020303000201010045ccf09a",
            "4b3352691f5b06000048000000ea01000000005f00000000000100020002ffff00010000000100000000010000000a00000000020000000200000000020000000a000000d07c20c0",
            "50335269135b06000038000000e602000000005f00000000000100067a696d69616e000773696d706c6574000203030002010100f76f3d90",
            "503352691f5b060000360000001c03000000005f00000000000100020002ffff00030000000300000000030000000a00000011051ec7",
            "72335269135b060000380000000f04000000005f00000000000100067a696d69616e000773696d706c6574000203030002010100aee8a034",
            "72335269205b0600003e0000004d04000000005f00000000000100020002ff00010000000a00000000030000000a000000000400000004000000bf3d9ce2"
        };
        int[] eventTypes = {
            TABLE_MAP_EVENT, UPDATE_ROWS_EVENT, TABLE_MAP_EVENT, UPDATE_ROWS_EVENT, TABLE_MAP_EVENT, DELETE_ROWS_EVENT};
        addHexToTxnBuffer(hexes, eventTypes);
    }

    @SneakyThrows
    private void addHexToTxnBuffer(String[] hexes, int[] eventTypes) {
        TxnBuffer txnBuffer = new TxnBuffer(new TxnKey(System.nanoTime(), UUID.randomUUID().toString()), null);
        txnBuffer.setReturningFixEnabled(true);
        for (int i = 0; i < hexes.length; i++) {
            byte[] data = ByteBufUtil.decodeHexDump(hexes[i]);
            TxnBufferItem txnItem =
                TxnBufferItem.builder().traceId("00001").logicSqlId(1).payload(data).eventType(eventTypes[i]).build();
            if (i == 5) {
                txnItem.setTraceId("00000");
                txnItem.setReturningEvent(true);
            }
            txnBuffer.doAdd(txnItem);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (int i = 0; i < txnBuffer.getEntity().refList.size(); i++) {
            log.info("traceId:{}", txnBuffer.getItemRef(i).getTraceId());
            outputStream.write(txnBuffer.getItemRef(i).getRawPayload());
        }
        parseDml(outputStream.toByteArray());
    }

    @Test
    @SneakyThrows
    public void testParseDDLs() {
        String ddlHexMysql =
            "2d9b6f680201000000a00000006303000000000f0000000000000006000031000000000000012000a04500000000060373746404ff00ff00ff000c017a696d69616e00110e0000000000000012ff0013007a696d69616e00414c544552205441424c452074332041444420434f4c554d4e20663220494e5420434f4e53545241494e542074335f63686b5f3320434845434b20286632203c20313029ed078e08";
        log.info("parse mysql ddl");
        // dd if=binlog.000050 bs=1 skip=475922510 count=347 2>/dev/null | hexdump -v -e '347/1 "%02x"'
        // flags: 0:0
        parseDDL(ddlHexMysql);
        String ddlHexPolarx =
            "aa5a776802cce02d185b010000a9015e1c010001000000000000000600002b0000000000000100002040000000000603737464050653595354454d042100210021000c017a696d69616e007a696d69616e002320504f4c4152585f4f524947494e5f53514c3d414c544552205441424c452074332041444420434f4c554d4e20663220494e5420434f4e53545241494e542074335f63686b5f3320434845434b20286632203c203130290a2320504f4c4152585f54534f3d3733353131353732313539363235393533393231383832323232343331353037303632373834303030303030303030303030303030300a2320504f4c4152585f44444c5f49443d373335313135373231343836373838323034380a414c544552205441424c452074332041444420434f4c554d4e20663220494e5420434f4e53545241494e542074335f63686b5f3320434845434b20286632203c2031302925e5ddf3";
        log.info("parse polarx ddl");
        // flags: 1:0
        parseDDL(ddlHexPolarx);
    }

    public void parseDDL(String hexString) throws Exception {
        byte[] ddlData = ByteBufUtil.decodeHexDump(hexString);
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        QueryLogEvent event = (QueryLogEvent) logDecoder.decode(new LogBuffer(ddlData, 0, ddlData.length), logContext);
        int mysqlFlag = event.getHeader().getFlags();
        long flag2 = event.getFlags2();
        log.info("query:{}", event.getQuery());
        log.info("flags:{}:{}", mysqlFlag, flag2);
        log.info("sql Mode:{}", event.getSqlMode());
    }

    /**
     * | binlog.000010 | 2291 | Table_map      | 1627      | 2347        | table_id: 98 (zimian.simplet)
     * |
     * | binlog.000010 | 2347 | Delete_rows    | 1627      | 2400        | table_id: 98 flags: STMT_END_F
     * <p>
     * | binlog.000010 | 1995 | Table_map      | 1627      | 2051        | table_id: 98 (zimian.simplet)
     * |
     * | binlog.000010 | 2051 | Write_rows     | 1627      | 2104        | table_id: 98 flags: STMT_END_F
     */
    @Test
    @SneakyThrows
    public void testParseDml() {
        log.info("parse insert event with table map...");
        String insertHex =
            "e3a77868135b060000380000000308000000006200000000000100067a696d69616e000773696d706c6574000203030002010100f471ee05e3a778681e5b060000350000003808000000006200000000000100020002ff0003000000030000000004000000040000006ee3c12b";

        parseDml(insertHex);
        log.info("parse delete event with table map...");
        String deleteHex =
            "00a87868135b060000380000002b09000000006200000000000100067a696d69616e000773696d706c6574000203030002010100d6e955c500a87868205b060000350000006009000000006200000000000100020002ff0003000000030000000004000000040000005a49b458";
        parseDml(deleteHex);
    }

    @SneakyThrows
    public void parseDml(String tableMap, String eventString) throws Exception {
        byte[] data1 = ByteBufUtil.decodeHexDump(tableMap);
        byte[] data2 = ByteBufUtil.decodeHexDump(eventString);
        // 拼凑data1 data2
        byte[] data = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data, 0, data1.length);
        System.arraycopy(data2, 0, data, data1.length, data2.length);
        parseDml(data);
    }

    public void parseDml(String eventString) throws Exception {
        parseDml(eventString, true);
    }

    public void parseDml(String eventString, boolean printDetail) throws Exception {
        byte[] data = ByteBufUtil.decodeHexDump(eventString);
        parseDml(data, printDetail);
    }

    public void parseDml(byte[] data) throws Exception {
        parseDml(data, true);
    }

    public void parseDml(byte[] data, boolean printDetail) throws Exception {
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001", 0));
        logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        LogBuffer logBuffer = new LogBuffer(data, 0, data.length);
        LogEvent event;
        int eventCount = 1;
        do {
            event = logDecoder.decode(logBuffer, logContext);
            if (event != null) {
                log.info("[+] event count:{}, event type:{}, event pos:{}", eventCount, event.getHeader().getType(),
                    event.getHeader().getLogPos());
            }
            if (event instanceof TableMapLogEvent) {
                TableMapLogEvent tableMapEvent = (TableMapLogEvent) event;
                log.info("table map!");
                log.info("evenLen:{}, nextPos:{},", event.getHeader().getEventLen(), event.getHeader().getLogPos());
                TableMapLogEvent.ColumnInfo[] columnInfo = tableMapEvent.getColumnInfo();
                for (TableMapLogEvent.ColumnInfo info : columnInfo) {
                    log.info("column info type:{}, meta:{}", info.type, info.meta);
                }
            }
            if (event instanceof FormatDescriptionLogEvent) {
                FormatDescriptionLogEvent formatDescriptionLogEvent = (FormatDescriptionLogEvent) event;
                log.info("checksumAlg:{}", formatDescriptionLogEvent.getHeader().getChecksumAlg());
            }
            if (event instanceof RowsLogEvent) {
                RowsLogEvent rowsEvent = (RowsLogEvent) event;
                if (printDetail) {
                    String info = rowsEvent.printRowValues();
                    log.info("rows data:\n{}", info);
                }
            }
            eventCount++;
        } while (event != null);
    }

    @Test
    public void testRemoveList() {
        LinkedList<Integer> list = new LinkedList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        ListIterator<Integer> iterator = list.listIterator(0);
        iterator.next();
        int item = iterator.next();
        iterator.remove();
        log.info("item:{} should be removed", item);
        item = iterator.next();
        log.info("next item:{}", item);
        item = iterator.previous();
        log.info("previous item:{}", item);
        item = iterator.previous();
        log.info("previous item:{}", item);
        iterator.remove();
        log.info("item:{} should be removed", item);
        item = iterator.next();
        log.info("next item:{}", item);
        item = iterator.next();
        log.info("next item:{}", item);
        item = iterator.previous();
        log.info("previous item:{}", item);
        item = iterator.previous();
        log.info("previous item:{}", item);
        item = iterator.next();
        log.info("next item:{}", item);

        iterator = list.listIterator(0);
        log.info("####after all###");
        while (iterator.hasNext()) {
            log.info("item:{}", iterator.next());
        }
    }
}
