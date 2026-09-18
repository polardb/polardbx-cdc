/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.BatchEventToken;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.EventData;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.EventDataBuildHandler;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.HandleContext;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken;
import com.aliyun.polardbx.binlog.enums.CompressionType;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.github.luben.zstd.Zstd;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.BEGIN_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.COMMIT_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.ROWS_QUERY_FIXED_LENGTH;

@Slf4j
public class BinlogTransactionCompressorTest extends BaseTest {
    private static EventDataBuildHandler eventDataBuildHandler;
    private static final int FORMAT_DESCRIPTION_END_POS = 125;
    private static final byte[] BINLOG_FILE_HEADER = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};
    private static final String rootPath = "binlog_transaction_compressor_test";
    private static final String binlogFileName = "binlog.000001";
    private static final String filePath = "/Users/zm/Downloads/tmp/binlog.000004";
    private static final String realFilePath = "/Users/zm/binlog/binlog.000001";
    private static final String idealFilePath = "/Users/zm/logs/decode-binlogs/24-08-27/binlog.000011";
    private static final int ROWS_QUERY_EVENT_LENGTH = 33;
    public static final String COMPRESSOR_TEST_DB_NAME = "test";
    public static final String COMPRESSOR_TEST_TB_NAME = "tst";

    byte[] bytesOfEventHeader = new byte[32];
    byte[] bytesOfEventData = new byte[1024];
    List<byte[]> data = Lists.newArrayList();
    private static int curPos = 0;
    private static final int debugPos = 0;
    @Getter
    private static int fakeTso = 0;

    @Test
    public void testCompress() throws Exception {
        buildCompressionBinlog(rootPath + "/" + binlogFileName);
        log.info("Compression Event in {}", rootPath + "/" + binlogFileName);
        // 测试能否正常读取原始字节码
        read(rootPath + "/" + binlogFileName);
        // 测试能否使用decoder进行解码
        decode(rootPath + "/" + binlogFileName);
    }

    public static void buildCompressionBinlog(String filePath) throws Exception {
        HandleContext handleContext = new HandleContext();
        handleContext.setRunning(new AtomicBoolean());
        handleContext.getRunning().set(true);
        eventDataBuildHandler = new EventDataBuildHandler(handleContext);
        EventData event = new EventData(4096);
        BatchEventToken eventToken = new BatchEventToken();
        event.setEventToken(eventToken);
        curPos = FORMAT_DESCRIPTION_END_POS;
        fakeTso = 0;

        // 向batch event中添加事件
        addBeginEvent(eventToken);
        addTableMapEvent(eventToken, COMPRESSOR_TEST_DB_NAME, COMPRESSOR_TEST_TB_NAME, 2);
        addRowsQueryEvent(eventToken);
        addCommitEvent(eventToken);
        addBeginEvent(eventToken);
        addTableMapEvent(eventToken, COMPRESSOR_TEST_DB_NAME, COMPRESSOR_TEST_TB_NAME, 2);
        addRowsQueryEvent(eventToken);
        addCommitEvent(eventToken);

        // 处理batch event 并压缩它
        eventDataBuildHandler.onEvent(event);

        int uncompressedSize = eventDataBuildHandler.getCompressor().getUncompressedSize();
        int compressionSize = eventDataBuildHandler.getCompressor().getCompressionSize();
        log.info("uncompressedSize:" + uncompressedSize + ", compressionSize:" + compressionSize);
        // 压缩后事件应该有两个
        Assert.assertEquals(2, eventToken.getTokens().size());
        BatchEventToken batchEventToken = (BatchEventToken) event.getEventToken();

        // 创建binlog文件
        File file = new File(filePath);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("delete file failed");
            }
        }
        if (!file.getParentFile().exists()) {
            if (file.getParentFile().mkdirs()) {
                log.info("create dir" + filePath);
            } else {
                throw new IOException("create file failed.");
            }
        }
        if (!file.createNewFile()) {
            throw new IOException("create file failed");
        }
        BinlogFile binlogFile = new BinlogFile(file, "rw", 4096, 4096, false, null);

        // 将压缩事件写入binlog
        for (SingleEventToken token : batchEventToken.getTokens()) {
            binlogFile.writeEvent(token.getData(), 0, token);
            binlogFile.flush();
        }

        log.info("write file success.");
    }

    @Test
    @Ignore
    public void testRead() {
        read(rootPath + "/" + binlogFileName);
        //read(filePath);

    }

    /**
     * 一个简单的binlog读取函数，支持解析压缩事件，pos: 指定开始读取的位置, debugPos: 从何处开始输出debug信息
     */
    @SneakyThrows
    public void read(String filePath) {
        long pos = 0;
        int maxEventSize = 0;
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            file.seek(pos);
            for (; ; ) {
                int read = file.read(bytesOfEventHeader, 0, 19);
                if (read < 0) {
                    break;
                }
                final ByteArray byteArray = new ByteArray(bytesOfEventHeader);
                long filePointer = file.getFilePointer();
                byteArray.skip(4);
                int type = byteArray.readInteger(1);

                int oserver_id = byteArray.readInteger(4);
                int eventSize = byteArray.readInteger(4);
                if (eventSize > maxEventSize) {
                    maxEventSize = eventSize;
                }
                long endPos = byteArray.readLong(4);

                // final int dataSize = (int) (endPos - pos - 19);

                final int dataSize = (eventSize - 19);
                if (filePointer >= debugPos) {
                    log.info("pos={}, endPos={}, type={}, length={}, server_id={}", pos, endPos, type, eventSize,
                        oserver_id);
                }

                Assert.assertEquals(pos + eventSize, endPos);
                byte[] bytesOfEventData = new byte[dataSize];
                file.read(bytesOfEventData, 0, dataSize);

                final ByteArray bodyArray = new ByteArray(bytesOfEventData);

                if (type == 40) {
                    int compressionFiled1 = bodyArray.readInteger(1);
                    int typeLength1 = bodyArray.readInteger(1);
                    long typeValue1 = bodyArray.readLenenc();

                    int compressionFiled2 = bodyArray.readInteger(1);
                    int typeLength2 = bodyArray.readInteger(1);
                    long uncompressedSize = bodyArray.readLenenc();

                    int compressionFiled3 = bodyArray.readInteger(1);
                    int typeLength3 = bodyArray.readInteger(1);
                    long compressionSize = bodyArray.readLenenc();

                    int endmask = bodyArray.readInteger(1);
                    if (filePointer >= debugPos) {
                        log.info("[+]Start parse compression data....");
                        log.info("[+]compressionFiled={}, typeLength={}, typeValue={}", compressionFiled1, typeLength1,
                            typeValue1);
                        log.info("[+]compressionFiled={}, typeLength={}, uncompressedSize={}", compressionFiled2,
                            typeLength2,
                            uncompressedSize);
                        log.info("[+]compressionFiled={}, typeLength={}, compressionSize={}", compressionFiled3,
                            typeLength3,
                            compressionSize);
                        log.info("endmask={}", endmask);
                    }
                    int payloadStartPos = bodyArray.getPos();

                    byte[] compressedData = Arrays.copyOfRange(bodyArray.getData(), payloadStartPos, dataSize - 4);
                    byte[] decompressedData = new byte[(int) uncompressedSize];

                    byte[] bytesOfEvent = new byte[dataSize + 19];

                    //crc checksum
                    bodyArray.setPos(dataSize - 4);
                    long checksum = bodyArray.readLong(4);

                    System.arraycopy(bytesOfEventHeader, 0, bytesOfEvent, 0, 19);
                    System.arraycopy(bytesOfEventData, 0, bytesOfEvent, 19, dataSize);
                    CRC32 crc32 = new CRC32();
                    crc32.update(bytesOfEvent, 0, eventSize - 4);
                    if (filePointer >= debugPos) {
                        log.info("actual checksum:{}, expect checksum:{}", checksum, crc32.getValue());
                    }
                    Assert.assertEquals(checksum, crc32.getValue());
                    if (typeValue1 == 0) {
                        Zstd.decompress(decompressedData, compressedData);
                    } else {
                        decompressedData = compressedData;
                    }

                    final ByteArray decompressedArray = new ByteArray(decompressedData);
                    int allDecompressedEventSizeSum = 0;
                    while (decompressedArray.getPos() < decompressedArray.getLimit()) {
                        decompressedArray.skip(4);
                        int dtype = decompressedArray.readInteger(1);
                        int server_id = decompressedArray.readInteger(4);
                        int dsize = decompressedArray.readInteger(4);
                        int dpos = decompressedArray.readInteger(4);
                        allDecompressedEventSizeSum += dsize;
                        decompressedArray.skip(dsize - 17);
                        if (filePointer >= debugPos) {
                            log.info("[+]dtype={}, server_id={}, dsize={}, dpos={}", dtype, server_id, dsize, dpos);
                        }
                    }
                    Assert.assertEquals(allDecompressedEventSizeSum, uncompressedSize);
                    if (filePointer >= debugPos) {
                        log.info("[-]End parse compression data....");
                    }
                }

                pos += eventSize;
            }
        }
        log.info("maxEventSize:{}", maxEventSize);
    }

    @Test
    @Ignore
    public void testDecode() {
        decode(rootPath + "/" + binlogFileName);
    }

    @SneakyThrows
    public void decode(String filePath) {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            byte[] data = new byte[2048];
            file.read(data);
            LogContext context = new LogContext();
            context.setServerCharactorSet(new ServerCharactorSet());
            context.setLogPosition(new LogPosition("binlog.000001", 125));
            LogBuffer buffer = new LogBuffer(data, 0, 256);
            LogDecoder decoder = new LogDecoder(0, 165);
            LogEvent event = decoder.decode(buffer, context);
            Assert.assertNotNull(event);
            Assert.assertEquals(event.getHeader().getType(), LogEvent.TRANSACTION_PAYLOAD_EVENT);
            List<LogEvent> list = decoder.processIterateDecode(event, context);
            Assert.assertEquals(list.size(), 4);
            Assert.assertEquals(list.get(0).getHeader().getType(), LogEvent.QUERY_EVENT);
            Assert.assertEquals(list.get(1).getHeader().getType(), LogEvent.TABLE_MAP_EVENT);
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) list.get(1);
            Assert.assertEquals(tableMapLogEvent.getTableId(), 2L);
            Assert.assertEquals(tableMapLogEvent.getDbName(), COMPRESSOR_TEST_DB_NAME);
            Assert.assertEquals(tableMapLogEvent.getTableName(), COMPRESSOR_TEST_TB_NAME);
            Assert.assertEquals(list.get(2).getHeader().getType(), LogEvent.ROWS_QUERY_LOG_EVENT);
            RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) list.get(2);
            Assert.assertEquals(rowsQueryLogEvent.getRowsQuery(), "CTS::1");
            Assert.assertEquals(list.get(3).getHeader().getType(), LogEvent.XID_EVENT);
        }
    }

    public static void addBeginEvent(BatchEventToken eventToken) {
        SingleEventToken token = new SingleEventToken();
        token.setUseCompression(true);
        token.setType(SingleEventToken.Type.BEGIN);
        token.setCompressionType(CompressionType.ZSTD);
        token.setLength(BEGIN_EVENT_LENGTH);
        curPos += BEGIN_EVENT_LENGTH;
        token.setNextPosition(curPos);
        token.setCheckServerId(false);
        eventToken.addToken(token);
    }

    public static void addCommitEvent(BatchEventToken eventToken) {
        SingleEventToken token = new SingleEventToken();
        token.setType(SingleEventToken.Type.COMMIT);
        token.setLength(COMMIT_EVENT_LENGTH);
        curPos += COMMIT_EVENT_LENGTH;
        token.setNextPosition(curPos);
        token.setCheckServerId(false);
        eventToken.addToken(token);
    }

    public static void addRowsQueryEvent(BatchEventToken eventToken) {
        SingleEventToken token = new SingleEventToken();
        token.setType(SingleEventToken.Type.ROWSQUERY);
        String content = MarkType.CTS + "::" + ++fakeTso;
        log.info("addRowsQueryEvent:{}", content);
        token.setLength(ROWS_QUERY_FIXED_LENGTH + content.length());
        token.setRowsQuery(content);
        curPos += ROWS_QUERY_FIXED_LENGTH + content.length();
        token.setNextPosition(curPos);
        token.setCheckServerId(false);
        eventToken.addToken(token);
    }

    @SneakyThrows
    public static void addTableMapEvent(BatchEventToken eventToken, String dbName, String tbName, int tableId) {
        SingleEventToken token = new SingleEventToken();
        token.setType(SingleEventToken.Type.DML);
        token.setLength(78);
        TableMapEventBuilder tableMapEventBuilder = new TableMapEventBuilder(0, 0, tableId, dbName, tbName, "utf8");
        AutoExpandBuffer autoExpandBuffer = new AutoExpandBuffer(1024, 512);
        token.setCheckServerId(false);

        List<Field> fieldList = new ArrayList<>();
        fieldList.add(MakeFieldFactory.makeField("BIGINT(20)", "1", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(256)", "aa", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(10)", "bb", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("JSON", "{\"id\": 1,\"name\": \"muscleape\"}", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("JSON", null, "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("DECIMAL(18, 2)", "1.8", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("DECIMAL(20, 11)", "-1.0987654321", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("DATETIME(3)", "2022-06-06 10:22:33", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("TIMESTAMP", "2022-06-06 10:22:33", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("tinytext", null, "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("YEAR", "2022", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("enum('red','green','yellow')", "red", "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("set(1,2,3)", "2", "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("geometry", null, "utf8", true, false));
        tableMapEventBuilder.setFieldList(fieldList);

        int eventSize = tableMapEventBuilder.write(autoExpandBuffer);
        // log.info(eventSize + "," + autoExpandBuffer.position());
        byte[] data = new byte[eventSize];
        System.arraycopy(autoExpandBuffer.toBytes(), 0, data, 0, data.length);
        token.setData(data);
        token.setUseTokenData(true);
        curPos += 78;
        token.setNextPosition(curPos);
        eventToken.addToken(token);
    }

    @SneakyThrows
    private void decodeCompressedBinlogFile(String path) {
        log.info("try decompressed binlog file: {}", path);
        FileLogFetcher logFetcher = new FileLogFetcher();
        logFetcher.open(path, 4);
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setServerCharactorSet(new ServerCharactorSet());
        logContext.setLogPosition(new LogPosition("binlog.000004", 4));
        while (logFetcher.fetch()) {
            LogEvent event = logDecoder.decode(logFetcher.buffer(), logContext);
            log.info("[+] type:{}, size:{} pos:{}", event.getHeader().getType(), event.getHeader().getEventLen(),
                event.getHeader().getLogPos());
            if (event.getHeader().getType() == 40) {
                log.info("Start to parse TRANSACTION_PAYLOAD_EVENT");
                List<LogEvent> eventList = logDecoder.processIterateDecode(event, logContext);
                eventList.forEach(
                    e -> log.info("[-] type:{}, size:{}, pos:{}", e.getHeader().getType(), e.getHeader().getEventLen(),
                        e.getHeader().getLogPos()));
                log.info("context pos:{}", logContext.getLogPosition().getPosition());
            }
        }
        logFetcher.close();
    }
}
