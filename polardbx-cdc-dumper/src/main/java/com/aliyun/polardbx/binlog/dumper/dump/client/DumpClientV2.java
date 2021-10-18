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

package com.aliyun.polardbx.binlog.dumper.dump.client;

import com.aliyun.polardbx.rpc.cdc.BinaryLog;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc.CdcServiceStub;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.Request;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by ShuGuang
 **/
public class DumpClientV2 {

    private final static Logger logger = LoggerFactory.getLogger(DumpClientV2.class);

    private final String host;
    private final Integer port;
    private final boolean useAsyncMode;
    private ManagedChannel channel;
    private volatile boolean connected;

    public DumpClientV2(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.useAsyncMode = false;
    }

    public void connect() {
        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .maxInboundMessageSize(Integer.MAX_VALUE)
            .build();
        connected = true;
    }

    public void dump(String fileName, long pos, PacketReceiver receiver) throws InterruptedException, IOException {
        logger.info("sync for {}#{}", fileName, pos);

        if (!useAsyncMode) {
            CdcServiceGrpc.CdcServiceBlockingStub blockingStub = CdcServiceGrpc.newBlockingStub(channel);
            Iterator<DumpStream> iterator = blockingStub.sync(DumpRequest.newBuilder()
                .setFileName(fileName)
                .setPosition(pos).build());
            while ((iterator.hasNext())) {
                DumpStream reply = iterator.next();
                receiver.onReceive(reply.getPayload().toByteArray());

                if (logger.isDebugEnabled()) {
                    logger.debug("reply is: " + reply);
                }
            }
            TimeUnit.SECONDS.sleep(30);
        } else {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            StreamObserver<DumpStream> observer = new StreamObserver<DumpStream>() {
                @Override
                public void onNext(DumpStream value) {
                    try {
                        receiver.onReceive(value.getPayload().toByteArray());
                    } catch (IOException e) {
                        throw new PolardbxException("onReceive fail " + fileName + "@" + pos, e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("onError " + fileName + "@" + pos, t);
                    MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_FILE_SYNC_ERROR,
                        ExceptionUtils.getStackTrace(t));
                    throw new PolardbxException("onError " + fileName + "@" + pos, t);
                }

                @Override
                public void onCompleted() {
                    try {
                        TimeUnit.SECONDS.sleep(30);
                    } catch (InterruptedException e) {
                    }
                    logger.info("dump complete {}@{}", fileName, pos);
                    countDownLatch.countDown();
                }
            };
            CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
            cdcServiceStub.sync(DumpRequest.newBuilder()
                .setFileName(fileName)
                .setPosition(pos).build(), observer);
            countDownLatch.await();
        }
    }

    public String findMasterFirstLogFile() {
        Iterator<BinaryLog> binaryLogs = CdcServiceGrpc.newBlockingStub(channel).showBinaryLogs(
            Request.newBuilder().build());
        if (binaryLogs.hasNext()) {
            return binaryLogs.next().getLogName();
        }
        return null;
    }

    public void disconnect() {
        if (channel != null) {
            channel.shutdownNow();
        }
        connected = false;
        logger.info("Dump client is disconnected to server.");
    }
}
