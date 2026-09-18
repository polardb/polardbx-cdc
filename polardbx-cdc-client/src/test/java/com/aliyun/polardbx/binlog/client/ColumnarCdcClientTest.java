/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.filter.LogBufferFilter;
import com.aliyun.polardbx.binlog.client.handler.ColumnarCdcClientParser;
import com.aliyun.polardbx.binlog.client.handler.LogEventPreHandler;
import com.aliyun.polardbx.binlog.client.handler.RowTableNameFilter;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 列存CDC客户端的单元测试。
 * 通过Mock DumperDataSource，使用本地binlog文件模拟并行读取场景。
 */
@Slf4j
public class ColumnarCdcClientTest {
    private final String path = ColumnarCdcClientTest.class.getClassLoader().getResource(".").getPath() + "binlog";
    private final AtomicBoolean stop = new AtomicBoolean(false);

    /**
     * 测试优化模式(optimize=true)下的并行读取。
     * 使用parallelism=2，Parser0读取binlog.000004+000006，Parser1读取binlog.000005+compress.000007。
     * 验证解析出的事件总数是否符合预期。
     */
    @Test
    public void test0() throws Exception {
        stop.set(false);
        IMetaDBDataSourceProvider provider = Mockito.mock(IMetaDBDataSourceProvider.class);
        ColumnarCdcClient client = new ColumnarCdcClient(provider, 3, 5, 102485760, 2, true, 64);
        client.setExceptionHandler((e) -> {
            e.printStackTrace();
            stop.set(true);
        });
        client.setDryRunDecode(false);
        client.setDryRun(false);
        client.setDryRunParse(false);
        client.setUseSleepWaitMode();
        client.setYieldWaitMode();
        client.setUseBlockWaitMode();
        AtomicLong throughput = new AtomicLong(0);
        AtomicLong size = new AtomicLong(0);
        client.setAddBinlogEventThroughput(throughput::addAndGet);
        client.setAddBinlogEventSize(size::addAndGet);
        // mock dumper datasource
        Field field = client.getClass().getDeclaredField("dumperDataSources");
        field.setAccessible(true);
        DumperDataSource[] dumperDataSources = (DumperDataSource[]) field.get(client);
        dumperDataSources[0] = Mockito.mock(DumperDataSource.class);
        dumperDataSources[1] = Mockito.mock(DumperDataSource.class);
        Mockito.when(dumperDataSources[0].getServerCharset()).thenReturn(new ServerCharactorSet());
        Mockito.when(dumperDataSources[1].getServerCharset()).thenReturn(new ServerCharactorSet());

        // mock stream returned by dumper datasource
        // dumperDataSources[0] fetch binlog.000004 and binlog.000006
        Mockito.doAnswer((Answer<Void>) invocation -> {
            BinlogPosition binlogPosition = invocation.getArgument(0);
            StreamObserver<DumpStream> target = invocation.getArgument(1);
            Map<String, String> ext = invocation.getArgument(2);
            System.out.println(binlogPosition);
            System.out.println(ext);
            // dump binlog.000004 and binlog.000006
            File binlog0 = new File(path + File.separator + "binlog.000004");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "binlog.000006");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);
            target.onNext(DumpStream.newBuilder().setPayload(ByteString.copyFrom(expected)).build());
            return null;
        }).when(dumperDataSources[0]).dump(Mockito.any(), Mockito.any(), Mockito.any());

        // dumperDataSources[1] fetch binlog.000005 and binlog.000007
        Mockito.doAnswer((Answer<Void>) invocation -> {
            BinlogPosition binlogPosition = invocation.getArgument(0);
            StreamObserver<DumpStream> target = invocation.getArgument(1);
            Map<String, String> ext = invocation.getArgument(2);
            System.out.println(binlogPosition);
            System.out.println(ext);
            // dump binlog.000005 and binlog.000007
            File binlog0 = new File(path + File.separator + "binlog.000005");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "compress.000007");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);
            target.onNext(DumpStream.newBuilder().setPayload(ByteString.copyFrom(expected)).build());
            return null;
        }).when(dumperDataSources[1]).dump(Mockito.any(), Mockito.any(), Mockito.any());

        EventHandler handler = new EventHandler();
        client.startAsync("binlog.000004", 4L, handler);

        while (true) {
            if (stop.get()) {
                break;
            } else {
                synchronized (ColumnarCdcClientTest.this.stop) {
                    if (stop.get()) {
                        break;
                    } else {
                        stop.wait(5000);
                    }
                }
            }
        }

        client.shutdown();
        System.out.println("data count: " + handler.dataCount);
        Assert.assertEquals(514, handler.dataCount.get());
    }

    /**
     * 测试传统模式(optimize=false)下的并行读取。
     * 与test0相同的数据，但使用Disruptor RingBuffer模式。
     * 验证两种模式的解析结果一致性（766个事件）。
     */
    @Test
    public void test1() throws Exception {
        stop.set(false);
        IMetaDBDataSourceProvider provider = Mockito.mock(IMetaDBDataSourceProvider.class);
        ColumnarCdcClient client = new ColumnarCdcClient(provider, 3, 5, 102485760, 2, false, 64);
        client.setExceptionHandler((e) -> {
            e.printStackTrace();
            stop.set(true);
        });
        client.setDryRunDecode(false);
        client.setDryRun(false);
        client.setDryRunParse(false);
        client.setUseSleepWaitMode();
        client.setYieldWaitMode();
        client.setUseBlockWaitMode();
        AtomicLong throughput = new AtomicLong(0);
        AtomicLong size = new AtomicLong(0);
        client.setAddBinlogEventThroughput(throughput::addAndGet);
        client.setAddBinlogEventSize(size::addAndGet);
        // mock dumper datasource
        Field field = client.getClass().getDeclaredField("dumperDataSources");
        field.setAccessible(true);
        DumperDataSource[] dumperDataSources = (DumperDataSource[]) field.get(client);
        dumperDataSources[0] = Mockito.mock(DumperDataSource.class);
        dumperDataSources[1] = Mockito.mock(DumperDataSource.class);
        Mockito.when(dumperDataSources[0].getServerCharset()).thenReturn(new ServerCharactorSet());
        Mockito.when(dumperDataSources[1].getServerCharset()).thenReturn(new ServerCharactorSet());

        // mock stream returned by dumper datasource
        // dumperDataSources[0] fetch binlog.000004 and binlog.000006
        Mockito.doAnswer((Answer<Void>) invocation -> {
            BinlogPosition binlogPosition = invocation.getArgument(0);
            StreamObserver<DumpStream> target = invocation.getArgument(1);
            Map<String, String> ext = invocation.getArgument(2);
            System.out.println(binlogPosition);
            System.out.println(ext);
            // dump binlog.000004 and binlog.000006
            File binlog0 = new File(path + File.separator + "binlog.000004");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "binlog.000006");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);
            target.onNext(DumpStream.newBuilder().setPayload(ByteString.copyFrom(expected)).build());
            return null;
        }).when(dumperDataSources[0]).dump(Mockito.any(), Mockito.any(), Mockito.any());

        // dumperDataSources[1] fetch binlog.000005 and binlog.000007
        Mockito.doAnswer((Answer<Void>) invocation -> {
            BinlogPosition binlogPosition = invocation.getArgument(0);
            StreamObserver<DumpStream> target = invocation.getArgument(1);
            Map<String, String> ext = invocation.getArgument(2);
            System.out.println(binlogPosition);
            System.out.println(ext);
            // dump binlog.000005 and binlog.000007
            File binlog0 = new File(path + File.separator + "binlog.000005");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "compress.000007");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);
            target.onNext(DumpStream.newBuilder().setPayload(ByteString.copyFrom(expected)).build());
            return null;
        }).when(dumperDataSources[1]).dump(Mockito.any(), Mockito.any(), Mockito.any());

        EventHandler handler = new EventHandler();
        client.startAsync("binlog.000004", 4L, handler);

        while (true) {
            if (stop.get()) {
                break;
            } else {
                synchronized (ColumnarCdcClientTest.this.stop) {
                    if (stop.get()) {
                        break;
                    } else {
                        stop.wait(5000);
                    }
                }
            }
        }

        client.shutdown();
        System.out.println("data count: " + handler.dataCount);
        Assert.assertEquals(766, handler.dataCount.get());
    }

    /**
     * 测试动态设置白名单/黑名单、过滤开关、decode64等参数的传递。
     * 验证设置能正确传递到各个Parser的filter和logBufferFilter中。
     */
    @Test
    public void testSetVariables() {
        IMetaDBDataSourceProvider provider = Mockito.mock(IMetaDBDataSourceProvider.class);
        ColumnarCdcClientParser[] parsers = new ColumnarCdcClientParser[2];
        parsers[0] = Mockito.mock(ColumnarCdcClientParser.class, InvocationOnMock::callRealMethod);
        parsers[1] = Mockito.mock(ColumnarCdcClientParser.class, InvocationOnMock::callRealMethod);
        parsers[0].filter = new RowTableNameFilter();
        parsers[1].filter = new RowTableNameFilter();
        parsers[0].logBufferFilter = new LogBufferFilter(null, true);
        parsers[1].logBufferFilter = new LogBufferFilter(null, true);
        ColumnarCdcClient cdcClient = new ColumnarCdcClient(provider, 3, 5, 102485760, 2, true, 64);
        cdcClient.setCdcClientParsers(parsers);
        Set<String> tableSet = new HashSet<>();
        tableSet.add("zimian.pm_user_bill");
        cdcClient.setAcceptTable(tableSet);
        Assert.assertEquals(tableSet, parsers[0].logBufferFilter.getTableNameSet());
        Assert.assertTrue(parsers[1].logBufferFilter.isWhiteListMode());
        cdcClient.setIgnoreTable(tableSet);
        Assert.assertFalse(parsers[1].logBufferFilter.isWhiteListMode());
        cdcClient.setFilterOptimizeEnabled(false);
        Assert.assertFalse(parsers[1].logBufferFilter.isEnabled());

        // test set decode64 enabled
        LogEventPreHandler logEventPreHandler = new LogEventPreHandler();
        parsers[0].logEventPreHandler = logEventPreHandler;
        parsers[1].logEventPreHandler = logEventPreHandler;
        parsers[0].decode64Enabled = new AtomicBoolean(false);
        parsers[1].decode64Enabled = new AtomicBoolean(false);
        cdcClient.setDecode64Enabled(true);
        Assert.assertTrue(logEventPreHandler.getDecode64Enabled().get());
        Assert.assertTrue(parsers[1].decode64Enabled.get());
        Assert.assertTrue(parsers[0].decode64Enabled.get());
    }

    /**
     * 测试用的事件处理器，验证事件顺序和数量。
     * 处理binlog.000004-000007的事件，检查文件顺序是否连续递增，
     * 同一文件内的位置是否单调递增。
     */
    private class EventHandler implements IEventHandler {
        private final long lastPosition = -1;
        private final AtomicLong dataCount = new AtomicLong(0);
        private String binlogFile;
        private long position;
        private String expectedFileName = "binlog.000004";

        public void onHandle(CdcEventData cdcEventData) {
            if (stop.get()) {
                return;
            }
            position = cdcEventData.getPosition();
            binlogFile = cdcEventData.getBinlogFileName();
            DBMSEvent event = cdcEventData.getEvent();
            if (!(event instanceof DBMSHeartbeatLog)) {
                dataCount.incrementAndGet();
            }
            if (!binlogFile.equalsIgnoreCase(expectedFileName)) {
                int current = BinlogFileUtil.getBinlogSequence(binlogFile);
                int expected = BinlogFileUtil.getBinlogSequence(expectedFileName);
                Assert.assertEquals(current, expected + 1);
                expectedFileName = binlogFile;
            } else {
                Assert.assertTrue(lastPosition < position);
            }
            log.info("{}:{}", binlogFile, position);
            if (binlogFile.equalsIgnoreCase("binlog.000007") && position > 35200) {
                synchronized (ColumnarCdcClientTest.this.stop) {
                    ColumnarCdcClientTest.this.stop.set(true);
                    ColumnarCdcClientTest.this.stop.notify();
                }
            }
        }
    }
}
