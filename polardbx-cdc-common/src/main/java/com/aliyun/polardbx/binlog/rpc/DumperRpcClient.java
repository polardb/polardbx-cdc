/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpc.cdc.BinlogDumpStatus;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.GetDumperInfoResponse;
import com.aliyun.polardbx.rpc.cdc.ShowBinlogDumpStatusRequest;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zm
 */
@Slf4j
public class DumperRpcClient {
    private ManagedChannel channel;
    private CdcServiceGrpc.CdcServiceBlockingStub blockingStub;
    private String ip;
    private int port;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private final int rpcTimeoutSecond;

    public DumperRpcClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        rpcTimeoutSecond = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_RPC_CLIENT_TIMEOUT_SECOND);
    }

    public void connect() {
        if (connected.compareAndSet(false, true)) {
            channel = NettyChannelBuilder
                .forAddress(ip, port)
                .usePlaintext()
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .build();
            log.info("connect to dumper {}:{}", ip, port);
        }
    }

    /**
     * 用户接口
     *
     * @return dumper ip:port -> BinlogDumpStatus
     */
    public Pair<String, List<BinlogDumpStatus>> showDumperStatus() {
        blockingStub = CdcServiceGrpc.newBlockingStub(channel);
        ShowBinlogDumpStatusRequest request = ShowBinlogDumpStatusRequest.newBuilder().build();
        List<BinlogDumpStatus> responses = new ArrayList<>();
        Iterator<BinlogDumpStatus> responseIterator = blockingStub.showBinlogDumpStatus(request);
        while (responseIterator.hasNext()) {
            responses.add(responseIterator.next());
        }
        return Pair.of(ip + ":" + port, responses);
    }

    /**
     * 内部接口，负载均衡使用
     *
     * @return dumper ip -> BinlogDumpInfo
     */
    public Pair<String, GetDumperInfoResponse> getDumperInfo(String streamName) {
        ShowBinlogDumpStatusRequest request =
            ShowBinlogDumpStatusRequest.newBuilder().setStreamName(streamName).build();
        GetDumperInfoResponse response = null;
        Retryer<GetDumperInfoResponse> retryer = RetryerBuilder.<GetDumperInfoResponse>newBuilder()
            .retryIfException()
            .withStopStrategy(StopStrategies.stopAfterAttempt(5))
            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
            .build();
        try {
            response = retryer.call(
                () -> CdcServiceGrpc.newBlockingStub(channel).withDeadlineAfter(rpcTimeoutSecond, TimeUnit.SECONDS)
                    .getDumperInfo(request));
        } catch (Exception e) {
            log.error("filter dumper: {}:{}, caused by get dumper info timeout", ip, port, e);
        }
        return Pair.of(ip, response);
    }

    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            if (channel != null) {
                channel.shutdown();
                log.info("disconnect to dumper {}:{}", ip, port);
            }
        }
    }
}
