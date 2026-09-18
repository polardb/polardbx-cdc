/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.google.protobuf.ByteString;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TxnStreamRpcServerTest {

    @Test
    public void testCheckVersion() {
        TxnStreamRpcServer server = new TxnStreamRpcServer(8981, (request, outputStream) -> {
        });

        // 设置服务器版本
        server.setVersion(1);
        server.setSubVersion(2);

        // 获取内部服务类来测试checkVersion方法
        TxnStreamRpcServer.TxnStreamingService service = server.new TxnStreamingService(null, null);

        // 测试版本匹配的情况 - 应该正常通过
        service.checkVersion(1, 2);
        service.checkVersion(0, 2); // 主版本为0表示不检查
        service.checkVersion(1, 0); // 子版本为0表示不检查
        service.checkVersion(0, 0); // 都为0表示都不检查

        // 测试主版本不匹配的情况
        try {
            service.checkVersion(3, 2);
            fail("应该抛出PolardbxException异常");
        } catch (PolardbxException e) {
            assertEquals("main version is inconsistent, request version is 3 , current version is 1", e.getMessage());
        }

        // 测试子版本不匹配的情况
        try {
            service.checkVersion(1, 4);
            fail("应该抛出PolardbxException异常");
        } catch (PolardbxException e) {
            assertEquals("sub version is inconsistent, request version is 4 , current version is 2", e.getMessage());
        }

        // 测试主版本和子版本都不匹配的情况
        try {
            service.checkVersion(3, 4);
            fail("应该抛出PolardbxException异常");
        } catch (PolardbxException e) {
            assertEquals("main version is inconsistent, request version is 3 , current version is 1", e.getMessage());
        }
    }

    @Test
    @Ignore
    public void testServer() throws InterruptedException, IOException {
        TxnStreamRpcServer server = new TxnStreamRpcServer(8980, (request, outputStream) -> {
            int traceIdSeed = 0;
            int tsoSeed = 0;
            for (int j = 0; j < 200000; j++) {
                ArrayList<TxnItem> items = new ArrayList<>();

                for (int i = 0; i < 10; i++) {
                    TxnItem item = TxnItem.newBuilder()
                        .setTraceId(String.valueOf(traceIdSeed++))
                        .setPayload(ByteString.copyFrom(new byte[10]))
                        .build();
                    items.add(item);
                }

                TxnToken token = TxnToken.newBuilder()
                    .setTso(String.valueOf(tsoSeed++))
                    .setTxnId(System.nanoTime())
                    .setPartitionId("11")
                    .build();

                try {
                    TxnBegin txnBegin = TxnBegin.newBuilder().setTxnToken(token).build();
                    outputStream.onNext(DumpReply.newBuilder()
                        .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.BEGIN).setTxnBegin(txnBegin))
                        .build());

                    TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();
                    outputStream.onNext(DumpReply.newBuilder()
                        .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(txnData))
                        .build());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            outputStream.onNext(DumpReply.newBuilder().build());
        });
        server.start();
        server.blockUntilShutdown();
    }

    @Test
    public void testPrintRequestLogWithTso() {
        // 准备测试数据
        DumpRequest request = DumpRequest.newBuilder()
            .setTso("738441180017275705619154770157088481380000000000000000")
            .setDumperName("testDumper")
            .setStreamSeq(1)
            .build();

        String lockId = "testDumper_1";

        // 创建服务器实例以获取内部服务类
        TxnStreamRpcServer server = new TxnStreamRpcServer(8981, (req, outputStream) -> {
        });
        TxnStreamRpcServer.TxnStreamingService service = server.new TxnStreamingService(null, null);

        // 执行测试方法
        service.printRequestLog(request, lockId);

        // 由于日志是通过SLF4J记录的，我们无法直接验证日志输出
        // 这个测试主要是确保方法能正常执行不抛异常
    }

    @Test
    public void testPrintRequestLogWithoutTso() {
        // 准备测试数据
        DumpRequest request = DumpRequest.newBuilder()
            .setDumperName("testDumper")
            .setStreamSeq(1)
            .build();

        String lockId = "testDumper_1";

        // 创建服务器实例以获取内部服务类
        TxnStreamRpcServer server = new TxnStreamRpcServer(8981, (req, outputStream) -> {
        });
        TxnStreamRpcServer.TxnStreamingService service = server.new TxnStreamingService(null, null);

        // 执行测试方法
        service.printRequestLog(request, lockId);

        // 由于日志是通过SLF4J记录的，我们无法直接验证日志输出
        // 这个测试主要是确保方法能正常执行不抛异常
    }
}