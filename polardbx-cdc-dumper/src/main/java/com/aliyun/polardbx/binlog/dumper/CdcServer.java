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
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManager;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManagerCollection;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileReader;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.rpc.cdc.BinaryLog;
import com.aliyun.polardbx.rpc.cdc.BinlogEvent;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.DumpRequest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
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
import com.aliyun.polardbx.rpl.taskmeta.RplServiceManager;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.Constants.STREAM_NAME_GLOBAL;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ShuGuang
 */
@Slf4j
public class CdcServer {

    private static final Logger metaLogger = LogUtil.getMetaLogger();
    private final String taskName;
    private final LogFileManagerCollection logFileManagerCollection;
    private final int port;
    private final ExecutorService executor;
    private final BinlogTaskConfig taskConfig;
    private Server server;
    private boolean localMode;

    public CdcServer(String taskName, LogFileManagerCollection logFileManagerCollection, int port,
                     BinlogTaskConfig taskConfig) {
        this.taskName = taskName;
        this.logFileManagerCollection = logFileManagerCollection;
        this.port = port;
        this.taskConfig = taskConfig;
        this.executor = Executors.newCachedThreadPool(getThreadFactory("Cdc-server-thread" + "-%d", false));
    }

    public void start() {
        if (!localMode) {
            //多流模式没有dumper leader
            if (TaskType.Dumper.name().equals(taskConfig.getRole())
                && !RuntimeLeaderElector.isDumperLeader(taskName)) {
                return;
            }
        }
        CdcServiceGrpc.CdcServiceImplBase svc = new CdcServiceGrpc.CdcServiceImplBase() {
            @Override
            public void showBinaryLogs(Request request, StreamObserver<BinaryLog> responseObserver) {
                log.info("CDC Server receive a show binary logs request, with stream name: {}",
                    request.getStreamName());
                LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                List<CdcFile> files;
                if (request.getExcludeRemoteFiles()) {
                    files = logFileManager.getAllLocalBinlogFilesOrdered();
                } else {
                    files = logFileManager.getAllBinlogFilesOrdered();
                }

                for (CdcFile file : files) {
                    responseObserver.onNext(BinaryLog.newBuilder()
                        .setLogName(file.getName())
                        .setFileSize(file.size())
                        .build());
                }
                responseObserver.onCompleted();
            }

            @Override
            public void showBinlogEvents(ShowBinlogEventsRequest request,
                                         StreamObserver<BinlogEvent> responseObserver) {
                log.info("CDC Server receive a show binlog events request, with stream name: {}, log name: {}",
                    request.getStreamName(), request.getLogName());
                final ServerCallStreamObserver<BinlogEvent> serverCallStreamObserver =
                    (ServerCallStreamObserver<BinlogEvent>) responseObserver;
                LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                LogFileReader logFileReader = new LogFileReader(logFileManager);
                CdcFile cdcFile = StringUtils.isEmpty(request.getLogName()) ? logFileManager.getMinBinlogFile() :
                    logFileManager.getBinlogFileByName(request.getLogName());
                logFileReader.showBinlogEvent(cdcFile, request.getPos(), request.getOffset(), request.getRowCount(),
                    serverCallStreamObserver);
            }

            @Override
            public void showMasterStatus(Request request, StreamObserver<MasterStatus> responseObserver) {
                log.info("CDC Server receive a show master status request, with stream name: {}",
                    request.getStreamName());
                LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                Cursor cursor = logFileManager.getLatestFileCursor();
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
            }

            @Override
            public void dump(DumpRequest request, StreamObserver<DumpStream> responseObserver) {
                log.info("CDC Server receive a dump request, with stream name: {}, file name: {}, position: {}, "
                        + "registered: {}, ext: {}.", request.getStreamName(), request.getFileName(),
                    request.getPosition(), request.getRegistered(), request.getExt());

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
                        .build();
                }

                Map<String, String> ext = new HashMap<>();
                if (StringUtils.isNotBlank(request.getExt())) {
                    ext = JSON.parseObject(request.getExt(), new TypeReference<Map<String, String>>() {
                    });
                }

                LogFileReader logFileReader = new LogFileReader(logFileManager);
                logFileReader.binlogDump(
                    request.getFileName(),
                    request.getPosition(),
                    request.getRegistered(),
                    ext,
                    serverCallStreamObserver);
            }

            @Override
            public void sync(DumpRequest request, StreamObserver<DumpStream> responseObserver) {
                final ServerCallStreamObserver<DumpStream> serverCallStreamObserver =
                    (ServerCallStreamObserver<DumpStream>) responseObserver;
                TxnOutputStream<DumpStream> txnOutputStream = new TxnOutputStream<>(serverCallStreamObserver);
                txnOutputStream.init();

                LogFileManager logFileManager = getLogFileManager(request.getStreamName());
                executor.submit(() -> {
                    LogFileReader logFileReader = new LogFileReader(logFileManager);
                    txnOutputStream.setExecutingThead(Thread.currentThread());
                    logFileReader.binlogSync(request.getFileName(), request.getPosition(), request.getSplitMode(),
                        txnOutputStream);
                });
            }

            ///////////////////////////// Replicate /////////////////////////
            @Override
            public void changeMaster(ChangeMasterRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("changeMaster: " + request.getRequest());
                RplServiceManager.changeMaster(request, responseObserver);
            }

            @Override
            public void changeReplicationFilter(ChangeReplicationFilterRequest request,
                                                StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("changeReplicationFilter: " + request.getRequest());
                RplServiceManager.changeReplicationFilter(request, responseObserver);
            }

            @Override
            public void startSlave(StartSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("startSlave: " + request.getRequest());
                RplServiceManager.startSlave(request, responseObserver);
            }

            @Override
            public void stopSlave(StopSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("stopSlave: " + request.getRequest());
                RplServiceManager.stopSlave(request, responseObserver);
            }

            @Override
            public void resetSlave(ResetSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
                metaLogger.info("resetSlave: " + request.getRequest());
                RplServiceManager.resetSlave(request, responseObserver);
            }

            @Override
            public void showSlaveStatus(ShowSlaveStatusRequest request,
                                        StreamObserver<ShowSlaveStatusResponse> responseObserver) {
                metaLogger.info("showSlaveStatus: " + request.getRequest());
                RplServiceManager.showSlaveStatus(request, responseObserver);
            }
        };

        try {
            server = NettyServerBuilder
                .forPort(port)
                .addService(svc)
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

    public void setLocalMode(boolean localMode) {
        this.localMode = localMode;
    }

}
