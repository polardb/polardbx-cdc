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
package com.aliyun.polardbx.binlog.dumper.dump.client;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.aliyun.polardbx.rpc.cdc.BinaryLog;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc.CdcServiceStub;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import com.aliyun.polardbx.rpc.cdc.Request;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ShuGuang
 **/
public class DumpClient {

    private final static Logger logger = LoggerFactory.getLogger(DumpClient.class);

    private final String host;
    private final Integer port;
    private final boolean useAsyncMode;
    private final AtomicBoolean connected;
    private final int asyncQueueSize;
    private final int flowControlWindowSize;
    private ManagedChannel channel;

    public DumpClient(String host, Integer port, boolean useAsyncMode, int asyncQueueSize, int flowControlWindowSize) {
        this.host = host;
        this.port = port;
        this.useAsyncMode = useAsyncMode;
        this.asyncQueueSize = asyncQueueSize;
        this.flowControlWindowSize = flowControlWindowSize;
        this.connected = new AtomicBoolean(false);
    }

    public void connect() {
        if (connected.compareAndSet(false, true)) {
            channel = NettyChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .flowControlWindow(1048576 * flowControlWindowSize)
                .build();
        }
    }

    public void dump(String fileName, long pos, EventSplitMode splitMode, PacketReceiver receiver) throws Throwable {
        logger.info("sync for {}#{}", fileName, pos);

        if (!useAsyncMode) {
            CdcServiceGrpc.CdcServiceBlockingStub blockingStub = CdcServiceGrpc.newBlockingStub(channel);
            Iterator<DumpStream> iterator = blockingStub.sync(DumpRequest.newBuilder()
                .setFileName(fileName)
                .setPosition(pos)
                .setSplitMode(splitMode).build());
            while ((iterator.hasNext())) {
                DumpStream reply = iterator.next();
                receiver.onReceive(DirectByteOutput.unsafeFetch(reply.getPayload()), reply.getIsHeartBeat());

                if (logger.isDebugEnabled()) {
                    logger.debug("reply is: " + reply);
                }
            }
        } else {
            ArrayBlockingQueue<Pair<byte[], Boolean>> queue = new ArrayBlockingQueue<>(asyncQueueSize);
            AtomicReference<Throwable> error = new AtomicReference<>();

            StreamObserver<DumpStream> observer = new StreamObserver<DumpStream>() {
                @Override
                public void onNext(DumpStream value) {
                    try {
                        queue.put(Pair.of(DirectByteOutput.unsafeFetch(value.getPayload()), value.getIsHeartBeat()));
                    } catch (Throwable e) {
                        error.set(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("onError " + fileName + "@" + pos, t);
                    MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_FILE_SYNC_ERROR,
                        ExceptionUtils.getStackTrace(t));
                    error.set(new PolardbxException("onError " + fileName + "@" + pos, t));
                }

                @Override
                public void onCompleted() {
                    logger.info("dump complete {}@{}", fileName, pos);
                    error.set(new PolardbxException("onComplete " + fileName + "@" + pos));
                }
            };
            CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
            cdcServiceStub.sync(DumpRequest.newBuilder()
                .setFileName(fileName)
                .setPosition(pos)
                .setSplitMode(splitMode).build(), observer);

            while (connected.get()) {
                if (error.get() != null) {
                    throw error.get();
                }
                try {
                    Pair<byte[], Boolean> pair = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (pair != null) {
                        receiver.onReceive(pair.getKey(), pair.getValue());
                    }
                } catch (IOException e) {
                    throw new PolardbxException("onReceive fail " + fileName + "@" + pos, e);
                }
            }
        }
    }

    public String findMasterFirstLogFile() {
        Iterator<BinaryLog> binaryLogs = CdcServiceGrpc.newBlockingStub(channel).showBinaryLogs(
            Request.newBuilder().setExcludeRemoteFiles(true).build());
        if (binaryLogs.hasNext()) {
            return binaryLogs.next().getLogName();
        }
        return null;
    }

    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
        logger.info("Dump client is disconnected to server.");
    }
}
