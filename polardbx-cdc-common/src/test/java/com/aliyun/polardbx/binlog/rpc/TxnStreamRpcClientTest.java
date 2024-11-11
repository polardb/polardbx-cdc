/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.junit.Ignore;
import org.junit.Test;

public class TxnStreamRpcClientTest {

    private static final TaskType taskType = TaskType.Final;

    @Test
    @Ignore
    public void testClient() throws InterruptedException {
        String target = "localhost:8912";
        NettyChannelBuilder channelBuilder =
            (NettyChannelBuilder) ManagedChannelBuilder.forTarget(target).usePlaintext();
        TxnStreamRpcClient client = new TxnStreamRpcClient(channelBuilder, messages -> {
            for (TxnMessage message : messages) {
                System.out.println(message.getType());
                if (message.getType() == MessageType.BEGIN) {
                    if (taskType == TaskType.Final) {
                        System.out.println(message.getTxnBegin().getTxnMergedToken());
                    } else {
                        System.out.println(message.getTxnBegin().getTxnToken());
                    }
                }
                if (message.getType() == MessageType.DATA) {
                    System.out.println(message.getTxnData().getTxnItemsList());
                }
                if (message.getType() == MessageType.END) {
                    System.out.println(message.getTxnEnd());
                }
                if (message.getType() == MessageType.TAG) {
                    if (taskType == TaskType.Final) {
                        System.out.println(message.getTxnBegin().getTxnMergedToken());
                    } else {
                        System.out.println(message.getTxnBegin().getTxnToken());
                    }
                }

                System.out.println("=======================");
            }
        });
        client.connect();
        client.dump(DumpRequest.newBuilder().build());
    }
}
