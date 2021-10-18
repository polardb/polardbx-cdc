/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.TxnServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ziyang.lb
 **/
@SuppressWarnings("rawtypes")
public class TxnStreamRpcClient {

    private static final Logger logger = LoggerFactory.getLogger(TxnStreamRpcClient.class);

    private final NettyChannelBuilder channelBuilder;
    private final TxnMessageReceiver receiver;
    private final boolean useAsyncMode;
    private TxnServiceGrpc.TxnServiceBlockingStub blockingStub;
    private TxnServiceGrpc.TxnServiceStub asyncStub;
    private volatile ManagedChannel channel;

    public TxnStreamRpcClient(NettyChannelBuilder channelBuilder, TxnMessageReceiver receiver) {
        this(channelBuilder, receiver, false);
    }

    public TxnStreamRpcClient(NettyChannelBuilder channelBuilder, TxnMessageReceiver receiver, boolean useAsyncMode) {
        // maxInboundMessageSize的默认值为4M，对于BinlogEvent来说，很容易超过该阈值，所以需要进行重新设置
        // 对于mysql来说，packet可允许的最大值为1G，参见：https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html#sysvar_max_allowed_packet
        // 既然mysql已经有对应的限制，所以，我们对此不再进行限制，直接设置为Integer的最大值（2G)
        this.channelBuilder = channelBuilder.maxInboundMessageSize(Integer.MAX_VALUE).flowControlWindow(1048576 * 100);
        this.receiver = receiver;
        this.useAsyncMode = useAsyncMode;
    }

    @SneakyThrows
    public void dump(DumpRequest request) throws InterruptedException {
        if (!useAsyncMode) {
            Iterator<DumpReply> replyIterator = blockingStub.dump(request);
            while ((replyIterator.hasNext())) {
                DumpReply reply = replyIterator.next();
                receiver.onReceived(reply.getTxnMessageList());

                if (logger.isDebugEnabled()) {
                    logger.debug("reply is: " + reply);
                }
            }
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            asyncStub.dump(request, new StreamObserver<DumpReply>() {

                @SneakyThrows
                @Override
                public void onNext(DumpReply reply) {
                    receiver.onReceived(reply.getTxnMessageList());
                }

                @Override
                public void onError(Throwable t) {
                    error.set(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await();
            if (error.get() != null) {
                throw error.get();
            }
        }
    }

    public void connect() {
        this.channel = channelBuilder.build();
        this.blockingStub = TxnServiceGrpc.newBlockingStub(channel);
        this.asyncStub = TxnServiceGrpc.newStub(channel);
    }

    public void disconnect() {
        try {
            if (this.channel != null) {
                channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (Throwable t) {
            // do nothing
        }
    }
}
