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

package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManager;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileReader;
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
import io.grpc.ServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ShuGuang
 */
@Slf4j
public class CdcServer {

    private final String taskName;
    private final LogFileManager logFileManager;
    private final int port;

    private Server server;
    private boolean localMode;
    private final ExecutorService executor;
    private static Logger metaLogger = LogUtil.getMetaLogger();

    public CdcServer(String taskName, LogFileManager logFileManager, int port) {
        this.taskName = taskName;
        this.logFileManager = logFileManager;
        this.port = port;
        this.executor = Executors.newCachedThreadPool(getThreadFactory("Cdc-server-thread" + "-%d", true));
    }

    public void start() {
        if (!localMode) {
            if (!RuntimeLeaderElector.isDumperLeader(taskName)) {
                return;
            }
        }
        CdcServiceGrpc.CdcServiceImplBase svc = new CdcServiceGrpc.CdcServiceImplBase() {
            @Override
            public void showBinaryLogs(Request request, StreamObserver<BinaryLog> responseObserver) {
                List<File> files = logFileManager.getAllLogFilesOrdered();
                for (int i = 0; i < files.size(); i++) {
                    responseObserver.onNext(BinaryLog.newBuilder()
                        .setLogName(files.get(i).getName())
                        .setFileSize(files.get(i).length())
                        .build());
                }
                responseObserver.onCompleted();
            }

            @Override
            public void showBinlogEvents(ShowBinlogEventsRequest request,
                                         StreamObserver<BinlogEvent> responseObserver) {
                final ServerCallStreamObserver<BinlogEvent> serverCallStreamObserver =
                    (ServerCallStreamObserver<BinlogEvent>) responseObserver;
                LogFileReader logFileReader = new LogFileReader(logFileManager);
                String fileName = StringUtils.isEmpty(request.getLogName()) ? logFileManager
                    .getMinBinlogFileName()
                    : request.getLogName();
                logFileReader.showBinlogEvent(fileName, request.getPos(), request.getOffset(), request.getRowCount(),
                    serverCallStreamObserver);
            }

            @Override
            public void showMasterStatus(Request request, StreamObserver<MasterStatus> responseObserver) {
                Cursor cursor = logFileManager.getLatestFileCursor();
                if (cursor != null) {
                    responseObserver.onNext(MasterStatus.newBuilder().setFile(cursor.getFileName())
                        .setPosition(cursor.getFilePosition()).build());
                } else {
                    responseObserver.onNext(MasterStatus.newBuilder().setFile(logFileManager.getMaxBinlogFileName())
                        .setPosition(4).build());
                }
                responseObserver.onCompleted();
            }

            @Override
            public void dump(DumpRequest request, StreamObserver<DumpStream> responseObserver) {

                final ServerCallStreamObserver<DumpStream> serverCallStreamObserver =
                    (ServerCallStreamObserver<DumpStream>) responseObserver;

                String fileName = request.getFileName();
                log.info("dump {} {}", fileName, request.getPosition());

                if (StringUtils.isEmpty(fileName)) {
                    request = DumpRequest.newBuilder().setFileName(logFileManager.getMinBinlogFileName())
                        .setPosition(
                            request.getPosition())
                        .build();
                }
                LogFileReader logFileReader = new LogFileReader(logFileManager);
                logFileReader.binlogDump(request.getFileName(), request.getPosition(), serverCallStreamObserver);
            }

            @Override
            public void sync(DumpRequest request, StreamObserver<DumpStream> responseObserver) {
                final ServerCallStreamObserver<DumpStream> serverCallStreamObserver =
                    (ServerCallStreamObserver<DumpStream>) responseObserver;
                TxnOutputStream<DumpStream> txnOutputStream = new TxnOutputStream<>(serverCallStreamObserver);
                txnOutputStream.init();

                executor.submit(() -> {
                    LogFileReader logFileReader = new LogFileReader(logFileManager);
                    txnOutputStream.setExecutingThead(Thread.currentThread());
                    logFileReader.binlogSync(request.getFileName(), request.getPosition(), txnOutputStream);
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
            server = ServerBuilder
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

    public void setLocalMode(boolean localMode) {
        this.localMode = localMode;
    }

}
