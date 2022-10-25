/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dumper.dump.server;

import com.aliyun.polardbx.rpc.cdc.BinaryLog;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.Request;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 */
public class Dumpserver {
    private static final Logger logger = Logger.getLogger(Dumpserver.class.getName());

    public static void main(String[] args) throws InterruptedException, IOException {
        CdcServiceGrpc.CdcServiceImplBase svc = new CdcServiceGrpc.CdcServiceImplBase() {
            @Override
            public void showBinaryLogs(Request request, StreamObserver<BinaryLog> responseObserver) {
                for (int i = 1; i <= 10000; i++) {
                    responseObserver.onNext(BinaryLog.newBuilder().setLogName("polardb-x-bin.00000" + i)
                        .setFileSize(i * 10000)
                        .build());
                    try {
                        TimeUnit.MILLISECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    logger.info("done " + i);
                }
                logger.info("all done");
                responseObserver.onCompleted();
            }
        };

        final Server server = ServerBuilder
            .forPort(10010)
            .addService(svc)
            .build()
            .start();

        logger.info("Listening on " + server.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("Shutting down");
                try {
                    server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }
        });
        server.awaitTermination();
    }
}
