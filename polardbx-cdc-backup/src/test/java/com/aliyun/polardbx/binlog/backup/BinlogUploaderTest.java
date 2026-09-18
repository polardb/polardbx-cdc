/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.remote.io.IFileReader;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

public class BinlogUploaderTest extends BaseTest {

    @Test
    public void testDoAppend() throws IOException, InterruptedException {
        // 准备 mock 对象
        IFileReader mockFetcher = Mockito.mock(IFileReader.class);
        Appender mockAppender = Mockito.mock(Appender.class);
        MetricsObserver mockObserver = Mockito.mock(MetricsObserver.class);
        BinlogOssRecord record = new BinlogOssRecord();

        // 设置 RemoteBinlogProxy 的静态方法 mock
        RemoteBinlogProxy mockProxy = Mockito.mock(RemoteBinlogProxy.class);
        Mockito.when(mockProxy.providerAppender(Mockito.anyString())).thenReturn(mockAppender);

        // 设置 fetcher 行为
        Mockito.when(mockFetcher.getName()).thenReturn("binlog.000001");
        byte[] testData1 = "test data 1".getBytes();
        byte[] testData2 = "test data 2".getBytes();
        Mockito.when(mockFetcher.read(Mockito.any(byte[].class)))
            .thenReturn(testData1.length)
            .thenReturn(testData2.length)
            .thenReturn(0); // 第三次返回0表示结束

        // 创建 uploader 实例并替换 proxy 实例
        BinlogUploader uploader = new BinlogUploader(mockFetcher, "remote_file", mockObserver, record);

        // 使用 Mockito 模拟静态方法
        try (MockedStatic<RemoteBinlogProxy> mockedStatic = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            mockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);

            // 调用测试方法
            uploader.doAppend();

            // 验证交互
            Mockito.verify(mockFetcher, Mockito.times(3)).read(Mockito.any(byte[].class));
            Mockito.verify(mockAppender).begin();
            Mockito.verify(mockAppender, Mockito.times(2)).append(Mockito.any(byte[].class), Mockito.anyInt());
            Mockito.verify(mockAppender).end();
            Mockito.verify(mockObserver, Mockito.times(2)).incrementUploadBytes(Mockito.anyLong());
            Mockito.verify(mockFetcher).close();
        }
    }

    @Test
    public void testDoMultiUpload() throws IOException, InterruptedException {
        // 准备 mock 对象
        IFileReader mockFetcher = Mockito.mock(IFileReader.class);
        Appender mockAppender = Mockito.mock(Appender.class);
        MetricsObserver mockObserver = Mockito.mock(MetricsObserver.class);
        BinlogOssRecord record = new BinlogOssRecord();

        // 设置 RemoteBinlogProxy 的静态方法 mock
        RemoteBinlogProxy mockProxy = Mockito.mock(RemoteBinlogProxy.class);
        Mockito.when(mockProxy.providerMultiAppender(Mockito.anyString(), Mockito.anyLong())).thenReturn(mockAppender);

        // 设置 fetcher 行为
        Mockito.when(mockFetcher.getName()).thenReturn("binlog.000001");
        Mockito.when(mockFetcher.isComplete()).thenReturn(true);
        Mockito.when(mockFetcher.length()).thenReturn(1024L);

        byte[] testData = "test data for multi upload".getBytes();
        Mockito.when(mockFetcher.read(Mockito.any(byte[].class)))
            .thenReturn(testData.length)
            .thenReturn(0); // 第二次返回0表示结束

        // 设置 appender 行为
        Mockito.when(mockAppender.begin()).thenReturn(2); // 2个part

        // 创建 uploader 实例并替换 proxy 实例
        BinlogUploader uploader = new BinlogUploader(mockFetcher, "remote_file", mockObserver, record);

        // 模拟静态方法
        try (MockedStatic<RemoteBinlogProxy> mockedStatic = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            mockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            mockedStatic.when(() -> RemoteBinlogProxy.getInstance().supportMultiUpload()).thenReturn(true);
            mockedStatic.when(() -> RemoteBinlogProxy.getInstance().needSwitchMultiUpload(Mockito.anyLong()))
                .thenReturn(true);

            // 调用测试方法
            uploader.doMultiUpload();

            // 验证交互
            Mockito.verify(mockFetcher).isComplete();
            Mockito.verify(mockFetcher).length();
            Mockito.verify(mockProxy).providerMultiAppender(Mockito.anyString(), Mockito.eq(1024L));
            Mockito.verify(mockAppender).begin();
            Mockito.verify(mockFetcher, Mockito.times(2)).read(Mockito.any(byte[].class));
            Mockito.verify(mockAppender, Mockito.times(2)).append(Mockito.any(byte[].class), Mockito.anyInt());
            Mockito.verify(mockAppender).end();
            Mockito.verify(mockObserver, Mockito.times(2)).incrementUploadBytes(Mockito.anyLong());
        }
    }

    @Test(expected = InterruptedException.class)
    public void testDoAppendInterruptedException() throws IOException, InterruptedException {
        // 准备 mock 对象
        IFileReader mockFetcher = Mockito.mock(IFileReader.class);
        Appender mockAppender = Mockito.mock(Appender.class);
        MetricsObserver mockObserver = Mockito.mock(MetricsObserver.class);
        BinlogOssRecord record = new BinlogOssRecord();

        // 设置 fetcher 行为
        Mockito.when(mockFetcher.getName()).thenReturn("binlog.000001");
        byte[] testData1 = "test data 1".getBytes();
        byte[] testData2 = "test data 2".getBytes();
        Mockito.when(mockFetcher.read(Mockito.any(byte[].class)))
            .thenReturn(testData1.length)
            .thenReturn(testData2.length)
            .thenReturn(0); // 第三次返回0表示结束

        // 设置中断状态
        Thread.currentThread().interrupt();

        // 创建 uploader 实例
        BinlogUploader uploader = new BinlogUploader(mockFetcher, "remote_file", mockObserver, record);

        try (MockedStatic<RemoteBinlogProxy> mockedStatic = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            RemoteBinlogProxy mockProxy = Mockito.mock(RemoteBinlogProxy.class);
            mockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            Mockito.when(mockProxy.providerAppender(Mockito.anyString())).thenReturn(mockAppender);

            try {
                // 调用测试方法
                uploader.doAppend();
            } finally {
                // 清除中断状态
                Thread.interrupted();
            }
        }
    }

    @Test(expected = InterruptedException.class)
    public void testDoMultiUploadInterruptedException() throws IOException, InterruptedException {
        // 准备 mock 对象
        IFileReader mockFetcher = Mockito.mock(IFileReader.class);
        MetricsObserver mockObserver = Mockito.mock(MetricsObserver.class);
        BinlogOssRecord record = new BinlogOssRecord();

        // 设置 fetcher 行为，使其不完整以便进入循环
        Mockito.when(mockFetcher.isComplete()).thenReturn(false);

        // 设置中断状态
        Thread.currentThread().interrupt();

        // 创建 uploader 实例
        BinlogUploader uploader = new BinlogUploader(mockFetcher, "remote_file", mockObserver, record);

        try (MockedStatic<RemoteBinlogProxy> mockedStatic = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            mockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(Mockito.mock(RemoteBinlogProxy.class));

            try {
                // 调用测试方法
                uploader.doMultiUpload();
            } finally {
                // 清除中断状态
                Thread.interrupted();
            }
        }
    }
}
