/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandler;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV1;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV2;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.SeekResult;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_SERVER_ID;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogTransactionCompressorTest.buildCompressionBinlog;
import static org.mockito.Mockito.mockStatic;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogFileTest extends BaseTest {
    private final String rootPath = "binlog_file_test";
    private final String fileName = "binlog.000001";
    MockedStatic<ServerCharactorSet> serverCharactorSetMockedStatic;

    @Before
    public void before() {
        serverCharactorSetMockedStatic = mockStatic(ServerCharactorSet.class);
        ServerCharactorSet serverCharactorSet = new ServerCharactorSet();
        serverCharactorSetMockedStatic.when(ServerCharactorSet::loadCharactorSetFromCN).thenReturn(serverCharactorSet);
    }

    @After
    public void after() {
        serverCharactorSetMockedStatic.close();
    }

    @Test
    @SneakyThrows
    public void testSeekLast() {
        // 超出1M的压缩事件
        File file =
            new File(BinlogFileTest.class.getClassLoader().getResource("binlog/large_compress.000001").toURI());
        seekLast(file, null);
        // 最后一个event不足25字节
        file =
            new File(BinlogFileTest.class.getClassLoader().getResource("binlog/small_event_binlog.000906").toURI());
        seekLast(file, null);
        // 较大event(32M)
        file = new File(BinlogFileTest.class.getClassLoader().getResource("binlog/big_event_bin.000001").toURI());
        seekLast(file, "728519316193096505618162583768085299210000000003417494");
        // 最后一个event是不完整rows query
        file = new File(BinlogFileTest.class.getClassLoader().getResource("binlog/incomplete_binlog.000013").toURI());
        seekLast(file, "737801246851517651219090776840470650880000000173153471");
    }

    @Test
    public void testSeekWithInterrupt_V1() throws URISyntaxException, IOException {
        File file = new File(BinlogFileTest.class.getClassLoader().getResource("binlog/big_event_bin.000001").toURI());
        Thread.currentThread().interrupt();
        try {
            mockConfig(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MEMORY_OPTIMIZE_ENABLED, "false");
            BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 1, true, null);
            binlogFile.seekLastTso();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof InterruptedException);
            Assert.assertTrue(e.getMessage().contains("seek tso interrupted ..."));
        }
    }

    @Test
    public void testSeekWithInterrupt_V2() throws URISyntaxException, FileNotFoundException {
        File file = new File(BinlogFileTest.class.getClassLoader().getResource("binlog/big_event_bin.000001").toURI());
        Thread.currentThread().interrupt();
        try {
            mockConfig(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MEMORY_OPTIMIZE_ENABLED, "true");
            BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 1, true, null);
            binlogFile.seekLastTso();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof InterruptedException);
            Assert.assertTrue(e.getMessage().contains("seek tso interrupted ..."));
        }
    }

    @SneakyThrows
    public void seekLast(File file, String lastTso) {
        mockConfig(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MEMORY_OPTIMIZE_ENABLED, "false");
        BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 1, true, null);
        SeekResult seekResultV1 = binlogFile.seekLastTso();
        log.info("seek tso res without optimize: {}", seekResultV1.toString());
        mockConfig(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MEMORY_OPTIMIZE_ENABLED, "true");
        binlogFile = new BinlogFile(file, "r", 1024, 1, true, null);
        SeekResult seekResultV2 = binlogFile.seekLastTso();
        log.info("seek tso res with optimize: {}", seekResultV2.toString());
        if (lastTso != null) {
            Assert.assertEquals(seekResultV1.getLastTso(), lastTso);
            Assert.assertEquals(seekResultV2.getLastTso(), lastTso);
        }
    }

    @Test
    @SneakyThrows
    public void testSeekLastFromCompress() {
        mockConfig(BINLOG_WRITE_CHECK_SERVER_ID, "false");
        buildCompressionBinlog(rootPath + "/" + fileName);
        // File file = new File("/Users/zm/logs/decode-binlogs/24-10-28/binlog.000008");
        File file = new File(rootPath + "/" + fileName);
        BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 64, true, null);
        BinlogFileSeekHandler seekHandler = new BinlogFileSeekHandlerV1();
        SeekResult seekResult = seekHandler.seekLastTso(binlogFile, 0, 1024, 0);
        log.info("seekV1Res: {}", seekResult);
        Assert.assertEquals(Integer.parseInt(seekResult.getLastTso()), BinlogTransactionCompressorTest.getFakeTso());
        seekHandler = new BinlogFileSeekHandlerV2();
        seekResult = seekHandler.seekLastTso(binlogFile, 0, 1024, 0);
        log.info("seekV2Res: {}", seekResult);
        Assert.assertEquals(Integer.parseInt(seekResult.getLastTso()), BinlogTransactionCompressorTest.getFakeTso());
    }

    @Test
    @Ignore
    public void testTruncate() throws IOException {
        String dataStr = "xxxxxxxxxx";
        byte[] dataBytes = dataStr.getBytes();

        String basePath = System.getProperty("user.home");
        File file = new File(basePath + "/truncate_test.txt");
        FileUtils.deleteQuietly(file);
        file.createNewFile();
        BinlogFile binlogFile = new BinlogFile(file, "rw", 1024, 256, true, null);
        for (int i = 0; i < 10; i++) {
            binlogFile.writeData(dataBytes, 0, dataBytes.length);
        }
        binlogFile.flush();

        Assert.assertEquals(binlogFile.fileSize(), 100);
        Assert.assertEquals(binlogFile.filePointer(), 100);

        binlogFile.truncate(50);
        Assert.assertEquals(binlogFile.fileSize(), 50);
        Assert.assertEquals(binlogFile.fileSize(), 50);
    }
}
