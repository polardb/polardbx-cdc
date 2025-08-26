/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.rpc.TxnMessageReceiver;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcClient;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class UpstreamBinlogFetcher {
    private final String taskName;
    private final TaskType taskType;
    private final String streamName;
    private final ExecutionConfig executionConfig;
    private final List<Pair<String, String>> targetTaskAddress;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private TxnStreamRpcClient rpcClient;
    private BinlogKWayMerger kWayMerger;

    public UpstreamBinlogFetcher(String taskName, TaskType taskType, String streamName, ExecutionConfig executionConfig,
                                 TxnMessageReceiver receiver, boolean rpcUseAsyncMode, int rpcReceiveQueueSize,
                                 int flowControlWindowSize) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.streamName = streamName;
        this.executionConfig = executionConfig;
        this.targetTaskAddress = new ArrayList<>();
        this.buildTarget();

        if (taskType == TaskType.Dumper || oneStreamPerDn()) {
            buildRpcClient(receiver, rpcUseAsyncMode, rpcReceiveQueueSize, flowControlWindowSize);
        } else if (taskType == TaskType.DumperX) {
            kWayMerger = new BinlogKWayMerger(taskName, this.streamName, targetTaskAddress, receiver,
                executionConfig, flowControlWindowSize);
        }
    }

    public void connect() {
        if (connected.compareAndSet(false, true)) {
            if (rpcClient != null) {
                rpcClient.connect();
            } else {
                kWayMerger.connect();
            }
        }
    }

    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            if (rpcClient != null) {
                rpcClient.disconnect();
            } else {
                kWayMerger.disconnect();
            }
        }
    }

    public void setMetrics(StreamMetrics metrics) {
        if (rpcClient != null) {
            rpcClient.setMetricsConsumer(metrics::setReceiveQueueSize);
        } else {
            kWayMerger.setMetrics(metrics);
        }
    }

    public void dump(String startTso) throws InterruptedException {
        if (suspend()) {
            return;
        }

        if (rpcClient != null) {
            DumpRequest dumpRequest = DumpRequest.newBuilder()
                .setDumperName(taskName)
                .setTso(startTso)
                .setStreamSeq(oneStreamPerDn() ? getStreamId(streamName) : Integer.MAX_VALUE)
                .setVersion(executionConfig.getRuntimeVersion())
                .setStorageInstId(getStorageInstId()).build();
            rpcClient.dump(dumpRequest);
        } else {
            kWayMerger.dump(startTso);
        }
    }

    boolean suspend() {
        if (oneStreamPerDn()) {
            if (XStreamService.isStreamInPendingState(streamName)) {
                log.info("stream {} is in pending state, will suspend it.", streamName);
                while (connected.get()) {
                    if (Thread.interrupted()) {
                        break;
                    }
                    LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
                }
                return true;
            }
        }
        return false;
    }

    int getStreamId(String streamName) {
        return XStreamService.getXStreamByName(streamName).getId().intValue();
    }

    private void buildRpcClient(TxnMessageReceiver receiver, boolean rpcUseAsyncMode,
                                int rpcReceiveQueueSize, int flowControlWindowSize) {
        NettyChannelBuilder channelBuilder = (NettyChannelBuilder) ManagedChannelBuilder
            .forTarget(targetTaskAddress.get(0).getValue()).usePlaintext();
        rpcClient = new TxnStreamRpcClient(channelBuilder, receiver, rpcUseAsyncMode, rpcReceiveQueueSize,
            flowControlWindowSize);
    }

    private String getStorageInstId() {
        if (oneStreamPerDn()) {
            return executionConfig.getStreamStorageMap().get(streamName);
        } else {
            return "";
        }
    }

    private void buildTarget() {
        // fixed port move to TaskInfo
        BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
        TaskType upstreamTaskType = (taskType == TaskType.DumperX) ? TaskType.Dispatcher : TaskType.Final;
        Set<String> expectedTasks = new HashSet<>(executionConfig.getSources());

        List<BinlogTaskInfo> upstreamTaskInfoList = taskInfoMapper.select(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(BinlogTaskInfoDynamicSqlSupport.role, SqlBuilder.isEqualTo(upstreamTaskType.name())));

        if (oneStreamPerDn()) {
            upstreamTaskInfoList = upstreamTaskInfoList.stream().filter(i -> expectedTasks.contains(i.getTaskName()))
                .collect(Collectors.toList());
        }

        if (upstreamTaskInfoList.isEmpty()) {
            throw new PolardbxException("all target task is unavailable now, will try later.");
        } else {
            Set<String> runningTasks =
                upstreamTaskInfoList.stream().map(BinlogTaskInfo::getTaskName).collect(Collectors.toSet());
            if (!runningTasks.equals(expectedTasks)) {
                throw new PolardbxException(
                    "running tasks and expected tasks is different, running is " + JSONObject.toJSONString(
                        runningTasks)
                        + ", expected is " + JSONObject.toJSONString(expectedTasks));
            }

            upstreamTaskInfoList.forEach(info -> {
                String address = info.getIp() + ":" + info.getPort();
                targetTaskAddress.add(Pair.of(info.getTaskName(), address));
            });
            log.info("target upstream task address is :" + JSONObject.toJSONString(targetTaskAddress));
        }
    }

    boolean oneStreamPerDn() {
        return (taskType == TaskType.DumperX
            && HashLevel.getCurrentHashLevel() == HashLevel.DATANODE);
    }
}
