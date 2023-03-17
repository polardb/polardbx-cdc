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
package com.aliyun.polardbx.binlog.dumper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.util.HexUtil;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc.CdcServiceStub;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.RplCommandResponse;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusRequest;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusResponse;
import com.aliyun.polardbx.rpc.cdc.StartSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.StopSlaveRequest;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CdcClientTest {
    public static void main(String[] args) throws InterruptedException {
        AtomicLong count = new AtomicLong();
        Stopwatch stopwatch = Stopwatch.createStarted();
        // Create a channel and a stub
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress("127.0.0.1", 6061)
            .usePlaintext()
            .maxInboundMessageSize(0xFFFFFF + 0xFF)
            .build();
        StreamObserver<DumpStream> observer = new StreamObserver<DumpStream>() {
            @Override
            public void onNext(DumpStream dumpStream) {
                count.incrementAndGet();
                final ByteString payload = dumpStream.getPayload();
                byte[] data = new byte[payload.size()];
                System.arraycopy(payload.toByteArray(), 0, data, 0, Math.min(128, payload.size()));

                System.out.println(count + "=>" + show(data));
                System.out.println(count + "=>" + payload.size() + ", " + payload.toByteArray().length);
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                stopwatch.stop();
                log.info("onCompleted {}, {}", count, stopwatch);
            }
        };

        CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        cdcServiceStub.dump(DumpRequest.newBuilder()
            .setFileName("binlog.000001")
            .setPosition(319).build(), observer);

        TimeUnit.SECONDS.sleep(420);
    }

    static String show(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            builder.append(HexUtil.toHex(data[i])).append(" ");
        }
        return builder.toString();
    }

    static void rplTest(ManagedChannel channel) {
        CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        changeMaster(cdcServiceStub);
        // startSlave(cdcServiceStub);
        // stopSlave(cdcServiceStub);
        // changeReplicationFilter(cdcServiceStub);
        // showSlaveStatus(cdcServiceStub);
    }

    static void changeMaster(CdcServiceStub cdcServiceStub) {
        StreamObserver<RplCommandResponse> observer = createRplCommandResponse();

        HostInfo srcHostInfo = new HostInfo();
        srcHostInfo.setHost("127.0.0.1");
        srcHostInfo.setPort(3306);
        srcHostInfo.setUserName("root");
        srcHostInfo.setSchema("");
        srcHostInfo.setPassword("");

        Map<String, String> parmas = new HashMap<>();
        parmas.put(RplConstants.CHANNEL, "");
        parmas.put(RplConstants.MASTER_HOST, srcHostInfo.getHost());
        parmas.put(RplConstants.MASTER_PORT, String.valueOf(srcHostInfo.getPort()));
        parmas.put(RplConstants.MASTER_USER, srcHostInfo.getUserName());
        parmas.put(RplConstants.MASTER_PASSWORD, srcHostInfo.getPassword());
        parmas.put(RplConstants.MASTER_LOG_FILE, "mysql_bin.000012");
        parmas.put(RplConstants.MASTER_LOG_POS, "808027744");
        parmas.put(RplConstants.IGNORE_SERVER_IDS, "0");

        ChangeMasterRequest request = ChangeMasterRequest.newBuilder().setRequest(JSON.toJSONString(parmas)).build();
        cdcServiceStub.changeMaster(request, observer);
    }

    static void startSlave(CdcServiceStub cdcServiceStub) {
        Map<String, String> parmas = new HashMap<>();
        parmas.put(RplConstants.CHANNEL, "");

        StreamObserver<RplCommandResponse> observer = createRplCommandResponse();
        StartSlaveRequest request = StartSlaveRequest.newBuilder().setRequest(JSON.toJSONString(parmas)).build();
        cdcServiceStub.startSlave(request, observer);
    }

    static void stopSlave(CdcServiceStub cdcServiceStub) {
        Map<String, String> parmas = new HashMap<>();
        parmas.put(RplConstants.CHANNEL, "");

        StreamObserver<RplCommandResponse> observer = createRplCommandResponse();
        StopSlaveRequest request = StopSlaveRequest.newBuilder().setRequest(JSON.toJSONString(parmas)).build();
        cdcServiceStub.stopSlave(request, observer);
    }

    static void changeReplicateFilter(CdcServiceStub cdcServiceStub) {
        Map<String, String> parmas = new HashMap<>();
        parmas.put(RplConstants.CHANNEL, "");
        parmas.put(RplConstants.REPLICATE_DO_DB, "(full_src_1, rpl)");
        parmas.put(RplConstants.REPLICATE_IGNORE_DB, "(full_src_1, rpl)");
        parmas.put(RplConstants.REPLICATE_DO_TABLE, "(full_src_1.t1, full_src_1.t2)");
        parmas.put(RplConstants.REPLICATE_IGNORE_TABLE, "(full_src_1.t1, full_src_1.t2)");
        parmas.put(RplConstants.REPLICATE_WILD_DO_TABLE, "('d%.tb\\_charset%', 'd%.col\\_charset%')");
        parmas.put(RplConstants.REPLICATE_WILD_IGNORE_TABLE, "('d%.tb\\_charset%', 'd%.col\\_charset%')");
        parmas.put(RplConstants.REPLICATE_REWRITE_DB, "((full_src_1, full_dst_1), (full_src_2, full_dst_2))");

        StreamObserver<RplCommandResponse> observer = createRplCommandResponse();
        ChangeReplicationFilterRequest request = ChangeReplicationFilterRequest.newBuilder()
            .setRequest(JSON.toJSONString(parmas))
            .build();
        cdcServiceStub.changeReplicationFilter(request, observer);
    }

    static void showSlaveStatus(CdcServiceStub cdcServiceStub) {
        StreamObserver<ShowSlaveStatusResponse> observer = new StreamObserver<ShowSlaveStatusResponse>() {

            @Override
            public void onNext(ShowSlaveStatusResponse response) {
                System.out.println("*****************************");

                Map<String, String> params = JSON.parseObject(response.getResponse(),
                    new TypeReference<HashMap<String, String>>() {
                    });

                System.out.println("masterServerId: " + params.get(RplConstants.MASTER_SERVER_ID));
                System.out.println("masterHost: " + params.get(RplConstants.MASTER_HOST));
                System.out.println("masterPort: " + params.get(RplConstants.MASTER_PORT));
                System.out.println("masterUser: " + params.get(RplConstants.MASTER_USER));
                System.out.println("masterLogFile: " + params.get(RplConstants.MASTER_LOG_FILE));
                System.out.println("readMasterLogPos: " + params.get(RplConstants.MASTER_LOG_POS));
                System.out.println("running: " + params.get(RplConstants.RUNNING));
                System.out.println("replicateDoDb: " + params.get(RplConstants.REPLICATE_DO_DB));
                System.out.println("replicateIgnoreDb: " + params.get(RplConstants.REPLICATE_IGNORE_DB));
                System.out.println("replicateDoTable: " + params.get(RplConstants.REPLICATE_DO_TABLE));
                System.out.println("replicateIgnoreTable: " + params.get(RplConstants.REPLICATE_IGNORE_TABLE));
                System.out.println("replicateWildDoTable: " + params.get(RplConstants.REPLICATE_WILD_DO_TABLE));
                System.out.println("replicateWildIgnoreTable: " + params.get(RplConstants.REPLICATE_WILD_IGNORE_TABLE));
                System.out.println("replicateRewriteDb: " + params.get(RplConstants.REPLICATE_REWRITE_DB));
                System.out.println("replicateIgnoreServerIds: " + params.get(RplConstants.REPLICATE_IGNORE_SERVER_IDS));
                System.out.println("channelName: " + params.get(RplConstants.CHANNEL));
                System.out.println("lastError: " + params.get(RplConstants.LAST_ERROR));
                System.out.println("skipCounter: " + params.get(RplConstants.SKIP_COUNTER));
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("completed");
            }
        };

        Map<String, String> parmas = new HashMap<>();
        ShowSlaveStatusRequest request = ShowSlaveStatusRequest.newBuilder()
            .setRequest(JSON.toJSONString(parmas))
            .build();
        cdcServiceStub.showSlaveStatus(request, observer);
    }

    static StreamObserver<RplCommandResponse> createRplCommandResponse() {
        return new StreamObserver<RplCommandResponse>() {

            @Override
            public void onNext(RplCommandResponse response) {
                System.out.println("resultCode: " + response.getResultCode());
                System.out.println("error: " + response.getError());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("completed");
            }
        };
    }
}