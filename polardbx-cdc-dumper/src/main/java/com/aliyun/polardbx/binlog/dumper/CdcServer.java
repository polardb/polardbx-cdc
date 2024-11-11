/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TimelineEnvConfig;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManager;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManagerCollection;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileReader;
import com.aliyun.polardbx.binlog.dumper.metrics.DumpClientMetric;
import com.aliyun.polardbx.binlog.dumper.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.rpc.cdc.BinaryLog;
import com.aliyun.polardbx.rpc.cdc.BinlogEvent;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.FullBinaryLog;
import com.aliyun.polardbx.rpc.cdc.FullMasterStatus;
import com.aliyun.polardbx.rpc.cdc.MasterStatus;
import com.aliyun.polardbx.rpc.cdc.Request;
import com.aliyun.polardbx.rpc.cdc.ResetSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.RplCommandResponse;
import com.aliyun.polardbx.rpc.cdc.ShowBinlogEventsRequest;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusRequest;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusResponse;
import com.aliyun.polardbx.rpc.cdc.StartSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.StopSlaveRequest;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.taskmeta.RplServiceManagerV0;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_HEARTBEAT_INTERVAL_WITH_DUMP;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_KEY;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_SYNC;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.MAX_TSO;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ShuGuang
 */
@Slf4j
public class CdcServer {
    private static final Logger metaLogger = LogUtil.getMetaLogger();
    private final String taskName;
    private final TaskType taskType;
    private final long version;
    private final LogFileManagerCollection logFileManagerCollection;
    private final int port;
    private final ExecutorService executor;
    private final BinlogTaskConfig taskConfig;
    private Server server;

    private final MetricsManager metricsManager;

    public static final Context.Key<DumpClientMetric> KEY_CLIENT_METRICS = Context.key("KEY_CLIENT_METRICS");

    public CdcServer(long version, TaskType taskType, String taskName,
                     LogFileManagerCollection logFileManagerCollection, int port,
                     BinlogTaskConfig taskConfig, MetricsManager metricsManager) {
        this.version = version;
        this.taskType = taskType;
        this.taskName = taskName;
        this.logFileManagerCollection = logFileManagerCollection;
        this.port = port;
        this.taskConfig = taskConfig;
        this.metricsManager = metricsManager;
        this.executor = Executors.newCachedThreadPool(getThreadFactory("Cdc-server-thread" + "-%d", false));
    }

    public void start() {
        if (!RuntimeLeaderElector.isDumperMasterOrX(version, taskType, taskName)) {
            return;
        }

        CdcServiceGrpc.CdcServiceImplBase svc = new CdcServiceGrpc.CdcServiceImplBase() {
            @Override
            public void showBinaryLogs(Request request, StreamObserver<BinaryLog> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                    log.info("CDC Server receive a show binary logs request, with stream name: {}",
                        request.getStreamName());

                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                    List<CdcFile> files;
                    if (request.getExcludeRemoteFiles()) {
                        files = logFileManager.getAllLocalBinlogFilesOrdered();
                    } else {
                        // FIXME: 本地文件file size = 0, binlog_oss_record表中size非0，导致空洞
                        files = logFileManager.getAllBinlogFilesOrdered();
                    }

                    for (CdcFile file : files) {
                        responseObserver.onNext(BinaryLog.newBuilder()
                            .setLogName(file.getName())
                            .setFileSize(file.size())
                            .build());
                    }
                    responseObserver.onCompleted();
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            @Override
            public void showFullBinaryLogs(Request request, StreamObserver<FullBinaryLog> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);

                    log.info("CDC Server receive a show full binary logs request, with stream name:{}",
                        request.getStreamName());

                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                    List<CdcFile> files;
                    if (request.getExcludeRemoteFiles()) {
                        files = logFileManager.getAllLocalBinlogFilesOrdered();
                    } else {
                        files = logFileManager.getAllBinlogFilesOrdered();
                    }

                    for (CdcFile file : files) {
                        responseObserver.onNext(FullBinaryLog.newBuilder()
                            .setLogName(file.getName())
                            .setFileSize(file.size())
                            .setCreatedTime(file.getCreatedTime())
                            .setLastModifyTime(file.getLastModifyTime())
                            .setFirstEventTime(file.getFirstEventTime())
                            .setLastEventTime(file.getLastEventTime())
                            .setLastTso(file.getLastTso())
                            .setUploadStatus(file.getUploadStatus())
                            .setFileLocation(file.getLocation())
                            .setExt("")
                            .build());
                    }

                    responseObserver.onCompleted();
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            // case1 : show binlog events [with stream_name], no file_name
            // case2 : show binlog events in file_name, no stream_name
            // case3 : show binlog events in file_name with stream_name
            @Override
            public void showBinlogEvents(ShowBinlogEventsRequest request,
                                         StreamObserver<BinlogEvent> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                    log.info(
                        "CDC Server receive a show binlog events request with log name:{}, pos:{}, offset:{}, rowCount:{}, streamName:{}",
                        request.getLogName(), request.getPos(), request.getOffset(), request.getRowCount(),
                        request.getStreamName());

                    final ServerCallStreamObserver<BinlogEvent> serverCallStreamObserver =
                        (ServerCallStreamObserver<BinlogEvent>) responseObserver;

                    String streamName = StringUtils.isEmpty(request.getStreamName()) ?
                        BinlogFileUtil.extractStreamName(request.getLogName()) : request.getStreamName();
                    LogFileManager logFileManager = getLogFileManager(streamName);
                    LogFileReader logFileReader = new LogFileReader(logFileManager);
                    CdcFile cdcFile = StringUtils.isEmpty(request.getLogName()) ? logFileManager.getMinBinlogFile() :
                        logFileManager.getBinlogFileByName(request.getLogName());
                    logFileReader.showBinlogEvent(cdcFile, request.getPos(), request.getOffset(), request.getRowCount(),
                        serverCallStreamObserver);
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            @Override
            public void showMasterStatus(Request request, StreamObserver<MasterStatus> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                    log.info("CDC Server receive a show master status request, with stream name: {}",
                        request.getStreamName());
                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                    BinlogCursor cursor = logFileManager.getLatestFileCursor();
                    if (cursor != null) {
                        responseObserver.onNext(MasterStatus.newBuilder().setFile(cursor.getFileName())
                            .setPosition(cursor.getFilePosition()).build());
                    } else {
                        CdcFile maxFile = logFileManager.getMaxBinlogFile();
                        String fileName = maxFile == null ? "" : maxFile.getName();
                        responseObserver.onNext(MasterStatus.newBuilder().setFile(fileName)
                            .setPosition(4).build());
                    }
                    responseObserver.onCompleted();
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            @Override
            public void showFullMasterStatus(Request request, StreamObserver<FullMasterStatus> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                    log.info("CDC Server receive a show full master status request, with stream name: {}",
                        request.getStreamName());
                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());

                    String streamName =
                        StringUtils.isEmpty(request.getStreamName()) ? STREAM_NAME_GLOBAL : request.getStreamName();
                    StreamMetrics streamMetrics = metricsManager.getStreamMetrics(streamName);
                    MetricsManager.StreamMetricsAverage streamMetricsAvg =
                        metricsManager.getStreamMetricsAvg(streamName);

                    String fileName;
                    long position;
                    String lastTso = "";
                    BinlogCursor cursor = logFileManager.getLatestFileCursor();
                    if (cursor != null) {
                        fileName = cursor.getFileName();
                        position = cursor.getFilePosition();
                        lastTso = cursor.getTso();
                    } else {
                        CdcFile maxFile = logFileManager.getMaxBinlogFile();
                        fileName = maxFile.getName();
                        position = 4L;
                    }

                    responseObserver.onNext(
                        FullMasterStatus.newBuilder()
                            .setFile(fileName)
                            .setPosition(position)
                            .setLastTso(lastTso)
                            .setDelayTime(streamMetrics.getLatestDelayTimeOnCommit())
                            .setAvgRevBps(streamMetricsAvg.getAvgRevBps())
                            .setAvgRevEps(streamMetricsAvg.getAvgRevEps())
                            .setAvgWriteBps(streamMetricsAvg.getAvgWriteBps())
                            .setAvgWriteBps(streamMetricsAvg.getAvgWriteBps())
                            .setAvgWriteTps(streamMetricsAvg.getAvgWriteTps())
                            .setAvgUploadBps(streamMetricsAvg.getAvgUploadBps())
                            .setAvgDumpBps(streamMetricsAvg.getAvgDumpBps())
                            .build());

                    responseObserver.onCompleted();
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            @Override
            public void dump(DumpRequest request, StreamObserver<DumpStream> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                    log.info("CDC Server receive a dump request, with stream name: {}, file name: {}, position: {}, "
                            + "registered: {}, ext: {}.", request.getStreamName(), request.getFileName(),
                        request.getPosition(), request.getRegistered(), request.getExt());

                    tryAdjustCdcHeartbeatWriteInterval();
                    final ServerCallStreamObserver<DumpStream> serverCallStreamObserver =
                        (ServerCallStreamObserver<DumpStream>) responseObserver;
                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                    String fileName = request.getFileName();
                    if (StringUtils.isEmpty(fileName)) {
                        CdcFile cdcFile = logFileManager.getMinBinlogFile();
                        String searchFile = cdcFile != null ? cdcFile.getName() : "";
                        request = DumpRequest.newBuilder()
                            .setFileName(searchFile)
                            .setPosition(request.getPosition())
                            .setRegistered(request.getRegistered())
                            .setExt(request.getExt())
                            .build();
                    }

                    Map<String, String> ext = new HashMap<>();
                    if (StringUtils.isNotBlank(request.getExt())) {
                        ext = JSON.parseObject(request.getExt(), new TypeReference<Map<String, String>>() {
                        });
                    }

                    LogFileReader logFileReader = new LogFileReader(logFileManager);
                    try {
                        logFileReader.binlogDump(
                            request.getFileName(),
                            request.getPosition(),
                            request.getRegistered(),
                            ext,
                            serverCallStreamObserver);
                    } finally {
                        DumpClientMetric.stopDump();
                    }
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            @Override
            public void sync(DumpRequest request, StreamObserver<DumpStream> responseObserver) {
                try {
                    MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_SYNC);
                    final ServerCallStreamObserver<DumpStream> serverCallStreamObserver =
                        (ServerCallStreamObserver<DumpStream>) responseObserver;
                    TxnOutputStream<DumpStream> txnOutputStream = new TxnOutputStream<>(serverCallStreamObserver);
                    txnOutputStream.init();

                    LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                    executor.submit(() -> {
                        try {
                            MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_SYNC);
                            LogFileReader logFileReader = new LogFileReader(logFileManager);
                            txnOutputStream.setExecutingThead(Thread.currentThread());
                            logFileReader
                                .binlogSync(request.getFileName(), request.getPosition(), request.getSplitMode(),
                                    txnOutputStream);
                        } finally {
                            MDC.remove(MDC_THREAD_LOGGER_KEY);
                        }
                    });
                } finally {
                    MDC.remove(MDC_THREAD_LOGGER_KEY);
                }
            }

            ///////////////////////////// Replicate /////////////////////////
            @Override
            public void changeMaster(ChangeMasterRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("changeMaster: " + request.getRequest());
                RplServiceManagerV0.changeMaster(request, responseObserver);
            }

            @Override
            public void changeReplicationFilter(ChangeReplicationFilterRequest request,
                                                StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("changeReplicationFilter: " + request.getRequest());
                RplServiceManagerV0.changeReplicationFilter(request, responseObserver);
            }

            @Override
            public void startSlave(StartSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("startSlave: " + request.getRequest());
                RplServiceManagerV0.startSlave(request, responseObserver);
            }

            @Override
            public void stopSlave(StopSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("stopSlave: " + request.getRequest());
                RplServiceManagerV0.stopSlave(request, responseObserver);
            }

            @Override
            public void resetSlave(ResetSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("resetSlave: " + request.getRequest());
                RplServiceManagerV0.resetSlave(request, responseObserver);
            }

            @Override
            public void showSlaveStatus(ShowSlaveStatusRequest request,
                                        StreamObserver<ShowSlaveStatusResponse> responseObserver) {
                metaLogger.info("showSlaveStatus: " + request.getRequest());
                RplServiceManagerV0.showSlaveStatus(request, responseObserver);
            }
        };

        try {
            server = NettyServerBuilder
                .forPort(port)
                .flowControlWindow(1048576 * 500)
                .addService(svc)
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                                 Metadata headers,
                                                                                 ServerCallHandler<ReqT, RespT> next) {
                        InetSocketAddress address =
                            (InetSocketAddress) call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                        Context context;
                        if (address != null) {
                            DumpClientMetric metric =
                                new DumpClientMetric(address.getHostString(), address.getPort(), metricsManager);
                            context = Context.current().withValue(KEY_CLIENT_METRICS, metric);
                        } else {
                            context = Context.current();
                        }
                        return Contexts.interceptCall(context, call, headers, next);
                    }
                })
                .build()
                .start();
            log.info("Listening on " + server.getPort());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("Shutting down");
                try {
                    server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }));
        } catch (IOException e) {
            log.error("start cdc server fail", e);
        }
    }

    public void stop() {
        if (server != null) {
            try {
                server.shutdownNow();
            } catch (Exception e) {
                log.warn("cdc server stop fail", e);
            }
        }
    }

    private LogFileManager getLogFileManager(String streamName) {
        if (TaskType.Dumper.name().equals(taskConfig.getRole())) {
            log.info("prepare to get LogFileManager for global binlog.");
            return logFileManagerCollection.get(STREAM_NAME_GLOBAL);
        } else if (TaskType.DumperX.name().equals(taskConfig.getRole())) {
            log.info("prepare to get LogFileManager for binlog-x with stream name " + streamName);
            if (StringUtils.isBlank(streamName)) {
                throw new PolardbxException("stream name can`t be blank.");
            }
            LogFileManager logFileManager = logFileManagerCollection.get(streamName);
            if (logFileManager == null) {
                throw new PolardbxException("stream " + streamName + "is not working on this dumper.");
            }
            return logFileManager;
        } else {
            throw new PolardbxException("get LogFileManger error for role " + taskConfig.getRole());
        }
    }

    // 只要发生binlog dump，则调大cdc heartbeat记录到binlog file中的频率，覆盖默认配置，即使后续不再有dump
    private void tryAdjustCdcHeartbeatWriteInterval() {
        try {
            int newInterval = DynamicApplicationConfig.getInt(BINLOG_WRITE_HEARTBEAT_INTERVAL_WITH_DUMP);
            if (newInterval <= 0) {
                return;
            }

            TimelineEnvConfig timelineEnvConfig = new TimelineEnvConfig();
            timelineEnvConfig.initConfigByTso(MAX_TSO);
            int currentInterval = timelineEnvConfig.getInt(ConfigKeys.BINLOG_WRITE_HEARTBEAT_INTERVAL);
            if (currentInterval <= newInterval) {
                return;
            }

            final String name = ConfigKeys.BINLOG_WRITE_HEARTBEAT_INTERVAL;
            final String value = newInterval + "";
            final String TRANSACTION_POLICY = "set drds_transaction_policy='TSO'";
            final String SEND_CONFIG_UPDATE_COMMAND =
                "insert ignore into __cdc_instruction__(INSTRUCTION_TYPE, INSTRUCTION_CONTENT, INSTRUCTION_ID) values(?,?,?)";

            JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");
            TransactionTemplate transactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
            transactionTemplate.execute((o) -> transactionTemplate.execute(transactionStatus -> {
                template.execute(TRANSACTION_POLICY);
                JSONObject newObject = new JSONObject();
                newObject.put(name, value);
                template.update(SEND_CONFIG_UPDATE_COMMAND, InstructionType.CdcEnvConfigChange.name(),
                    newObject.toJSONString(), "binlog_dump_auto_adjust_heartbeat_interval");
                return null;
            }));
        } catch (Throwable t) {
            log.error("try adjust cdc heartbeat write interval error!!", t);
        }
    }

}
