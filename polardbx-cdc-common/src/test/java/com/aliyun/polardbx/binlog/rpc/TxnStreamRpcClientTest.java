/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
