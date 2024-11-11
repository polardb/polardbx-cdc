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
package com.aliyun.polardbx.binlog.client;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.MySqlInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.MasterStatus;
import com.aliyun.polardbx.rpc.cdc.Request;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.NameResolverRegistry;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DumperDataSource {

    private static final Logger logger = LoggerFactory.getLogger(DumperDataSource.class);
    private static final long DUMPER_TIMEOUT_MILL = TimeUnit.SECONDS.toMillis(60);
    private ManagedChannel channel;
    private String ip;
    private int port;
    private MySqlInfo mySqlInfo;
    private MetaDbHelper metaDbHelper;

    private IExceptionHandler exceptionHandler;

    public DumperDataSource(MetaDbHelper metaDbHelper) {
        this.metaDbHelper = metaDbHelper;
        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());
    }

    public ServerCharactorSet getServerCharset() {
        return mySqlInfo.getServerCharactorSet();
    }

    public void stop() {
    }

    public BinlogPosition findStartPosition() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        reConnect();
        CdcServiceGrpc.CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        AtomicReference<BinlogPosition> reference = new AtomicReference<>();
        cdcServiceStub.showMasterStatus(Request.newBuilder().build(), new StreamObserver<MasterStatus>() {
            @Override
            public void onNext(MasterStatus value) {
                logger.info("find start position  : " + value.getFile() + ":" + value.getPosition());
                reference.set(new BinlogPosition(value.getFile(), value.getPosition(), -1, -1));
                countDownLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                logger.error("find start position  error !", t);
                countDownLatch.countDown();
                exceptionHandler.handle(t);
            }

            @Override
            public void onCompleted() {

            }
        });
        if (!countDownLatch.await(15, TimeUnit.SECONDS)) {
            throw new PolardbxException("request show master status failed!");
        }
        return reference.get();
    }

    public void releaseChannel() {
        if (this.channel == null) {
            return;
        }
        this.channel.shutdownNow();
        this.channel = null;
        logger.warn("close cdc channel!");
    }

    private void checkChannelState() {
        if (channel == null) {
            throw new PolardbxException("cdc dumper channel not init before use");
        }
        ConnectivityState state = channel.getState(true);
        if (state == ConnectivityState.SHUTDOWN || state == ConnectivityState.TRANSIENT_FAILURE) {
            throw new PolardbxException("cdc dumper channel state check error , state " + state);
        }
    }

    private CdcMasterDumperNode getCdcMasterDumperNode() throws SQLException {
        List<Map<String, Object>> dataList = metaDbHelper.selectMasterDumperInfo();
        if (dataList.isEmpty()) {
            String errorMsg = "select dumper master failed, please check cdc is ready!";
            throw new PolardbxException(errorMsg);
        }
        Map<String, Object> rowMap = dataList.get(0);
        String cdcServerIp = String.valueOf(rowMap.get("ip"));
        int cdcPort = Integer.parseInt(rowMap.get("port").toString());
        return new CdcMasterDumperNode(cdcServerIp, cdcPort);
    }

    public void reConnect() throws Exception {
        synchronized (this) {
            releaseChannel();
            CdcMasterDumperNode newDumperNode = getCdcMasterDumperNode();
            logger.info("cdc dumper master info : " + newDumperNode);
            logger.info("try to init cdc server ip and port newDumperNode success!");
            this.ip = newDumperNode.ip;
            this.port = newDumperNode.port;
            channel = NettyChannelBuilder
                .forAddress(ip, port)
                .usePlaintext()
                .maxInboundMessageSize(0xFFFFFF + 0xFF)
                .build();
        }
    }

    public void dump(BinlogPosition position, StreamObserver<DumpStream> target) {
        checkChannelState();
        CdcServiceGrpc.CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        Map<String, String> ext = new HashMap<>();
        ext.put("master_binlog_checksum", "CRC32");
        ext.put("client_type", "COLUMNAR");
        cdcServiceStub.dump(DumpRequest.newBuilder()
            .setExt(JSON.toJSONString(ext))
            .setRegistered(true)
            .setFileName(position.getFileName())
            .setPosition(position.getPosition()).build(), new ObserverProxy(target));
        logger.warn(
            "connect to " + ip + ":" + port + " success with pos[" + position.getFileName() + ":"
                + position.getPosition() + "]");
    }

    public void setExceptionHandler(IExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void initCharset() throws Exception {
        mySqlInfo = metaDbHelper.providerMySqlInfo();
    }

    private static class ObserverProxy implements StreamObserver<DumpStream> {

        private StreamObserver<DumpStream> target;

        public ObserverProxy(StreamObserver<DumpStream> target) {
            this.target = target;
        }

        public void close() {
            target = null;
        }

        @Override
        public void onNext(DumpStream value) {
            target.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onCompleted() {
            target.onCompleted();
        }
    }

    @Data
    @ToString
    private static class CdcMasterDumperNode {
        private final String ip;
        private final int port;
    }
}
