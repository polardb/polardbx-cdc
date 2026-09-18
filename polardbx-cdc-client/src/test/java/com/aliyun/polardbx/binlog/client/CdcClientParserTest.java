/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */

package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zm
 */
@Slf4j
public class CdcClientParserTest {
    private final String path = ColumnarCdcClientTest.class.getClassLoader().getResource(".").getPath() + "binlog";

    @Test
    public void testParse() throws Exception {
        int parseTest = 2;
        // 准备测试数据 注意，这个binlog没有四字节
        File binlogFile = new File(path + File.separator + "binlog.000001");
        if (binlogFile.exists()) {
            FileInputStream fis = new FileInputStream(binlogFile);
            byte[] fileBuffer = new byte[(int) binlogFile.length()];
            fis.read(fileBuffer);
            fis.close();

            // 创建一个自定义的LogFetcher，用于提供测试数据
            StreamObserverFileLogFetcher testFetcher = new StreamObserverFileLogFetcher() {
                private boolean dataProvided = false;
                private final byte[] fileData = fileBuffer; // 保存buffer的引用

                @Override
                public boolean fetch() throws java.io.IOException {
                    if (!dataProvided) {
                        byte[] eventData = new byte[fileData.length];
                        System.arraycopy(fileData, 0, eventData, 0, eventData.length);
                        // 将测试数据通过onNext方法写入pipe
                        onNext(DumpStream.newBuilder().setPayload(ByteString.copyFrom(eventData)).build());
                        dataProvided = true;
                        // 调用super.fetch()来处理数据
                        return super.fetch();
                    }
                    // 数据已经处理完毕，返回false表示没有更多数据
                    return false;
                }
            };

            // 设置起始位置为binlog.000001文件的开始位置
            BinlogPosition startPosition = new BinlogPosition("binlog.000001", 4, -1, -1);

            // 创建一个简单的事件处理器
            IEventHandler handler = event -> {
                // 简单处理事件
                log.info("Received event: " + event.getEvent());
            };

            // 创建一个简单的异常处理器
            IExceptionHandler exceptionHandler = Throwable::printStackTrace;

            // 创建一个新的CdcClientParser实例用于测试
            ServerCharactorSet charset = new ServerCharactorSet();

            CdcClientParser parser = new CdcClientParser(
                testFetcher,
                startPosition,
                handler,
                charset,
                exceptionHandler,
                1024,
                1
            );

            // 初始化parser
            parser.started.set(true);
            parser.init();

            // 创建一个线程来运行parse方法
            AtomicBoolean finished = new AtomicBoolean(false);
            Thread parseThread = new Thread(() -> {
                try {
                    parser.parse();
                } finally {
                    finished.set(true);
                }
            });

            // 启动解析线程
            parseThread.start();

            // 等待解析完成或超时
            long startTime = System.currentTimeMillis();
            while (!finished.get() && (System.currentTimeMillis() - startTime) < 50000) {
                Thread.sleep(100);
            }

            // 停止parser
            parser.stop();

            // 等待线程结束
            parseThread.join(1000);
        }
    }
}