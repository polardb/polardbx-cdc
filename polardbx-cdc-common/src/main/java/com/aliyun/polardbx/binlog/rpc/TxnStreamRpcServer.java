/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.TxnServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.ChannelOption;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.aliyun.polardbx.binlog.util.CommonUtils.getFormatDateTimeFromTso;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ziyang.lb
 **/
public class TxnStreamRpcServer {

    private static final Logger logger = LoggerFactory.getLogger(TxnStreamRpcServer.class);
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 10;                                 // 10M

    private final int port;
    private final Server server;
    private final TaskType taskType;
    @Setter
    private long version;
    @Setter
    private long subVersion;

    public TxnStreamRpcServer(int port, TxnMessageProvider provider) {
        this((NettyServerBuilder) ServerBuilder.forPort(port), port, provider, TaskType.Dispatcher);
    }

    public TxnStreamRpcServer(int port, TxnMessageProvider provider, TaskType taskType) {
        this((NettyServerBuilder) ServerBuilder.forPort(port), port, provider, taskType);
    }

    /**
     * Create a TxnStream server using serverBuilder as a base and features as data.
     */
    public TxnStreamRpcServer(NettyServerBuilder serverBuilder, int port, TxnMessageProvider provider,
                              TaskType taskType) {
        this.port = port;
        this.taskType = taskType;
        this.server = serverBuilder.maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            .flowControlWindow(1048576 * 200)
            .addService(new TxnStreamRpcServer.TxnStreamingService(provider, this.taskType))
            .withOption(ChannelOption.SO_REUSEADDR, true)
            .build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Rpc Server started, listening on " + port);
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon
     * threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    class TxnStreamingService extends TxnServiceGrpc.TxnServiceImplBase {

        private final TxnMessageProvider provider;
        private final Map<String, ReentrantLock> locks;
        private final ExecutorService executor;
        private final TaskType taskType;

        TxnStreamingService(TxnMessageProvider provider, TaskType taskType) {
            this.provider = provider;
            this.locks = new ConcurrentHashMap<>();
            this.executor = Executors.newCachedThreadPool(getThreadFactory("txn-stream-processor" + "-%d", true));
            this.taskType = taskType;
        }

        // 同一时刻，暂时只支持一个消费者，其它消费者连接上来之后进行互斥等待
        @Override
        public void dump(DumpRequest request, StreamObserver<DumpReply> responseObserver) {
            checkVersion(request.getVersion(), request.getSubVersion());
            String dumperName = request.getDumperName();
            int streamSeq = request.getStreamSeq();
            String lockId = dumperName + "_" + streamSeq;

            logger.info("Accepted a request from client side, with dumper name {}.", dumperName);

            ServerCallStreamObserver<DumpReply> observer = (ServerCallStreamObserver<DumpReply>) responseObserver;
            TxnOutputStream<DumpReply> txnOutputStream = new TxnOutputStream<>(streamSeq, observer);
            txnOutputStream.init();

            final ReentrantLock lock = locks.computeIfAbsent(lockId, k -> new ReentrantLock());

            // 之前是直接在Grpc线程执行dump逻辑，后来改造为在单独的线程中执行dump逻辑，具体原因可参见：
            // https://github.com/grpc/grpc-java/issues/7839
            // https://github.com/grpc/grpc-java/issues/7361
            executor.submit(() -> {
                try {
                    if (!lock.tryLock(10, TimeUnit.SECONDS)) {
                        String message = String.format("try acquire lock failed for dumper %s, because other client"
                            + " is consuming.", dumperName);
                        logger.warn(message);
                        responseObserver.onError(new PolardbxException(message));
                        return;
                    }

                    // important log
                    printRequestLog(request, lockId);

                    txnOutputStream.setExecutingThead(Thread.currentThread());
                    provider.dump(request, txnOutputStream);

                    // 如果出现没有抛异常，dump方法退出的情况，只有一种可能：Provider执行了stop操作，此时通过报错的方式通知客户端
                    responseObserver.onError(new PolardbxException("server is shutdown, with dumperId " + lockId));
                } catch (Throwable t) {
                    logger.error("dump error!!", t);
                    responseObserver.onError(t);
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                    locks.remove(lockId);
                }
            });
        }

        void printRequestLog(DumpRequest request, String lockId) {
            logger.info("The client successfully acquired lock, with lockId {}.", lockId);
            if (StringUtils.isNotBlank(request.getTso())) {
                logger.info("request tso is [{}][{}], with lockId {}.", request.getTso(),
                    getFormatDateTimeFromTso(request.getTso()), lockId);
            } else {
                logger.info("request tso is empty string, with lockId {}.", lockId);
            }
        }

        void checkVersion(long requestMainVersion, long requestSubVersion) {
            if (requestMainVersion != 0 && requestMainVersion != TxnStreamRpcServer.this.version) {
                throw new PolardbxException(
                    "main version is inconsistent, request version is " + requestMainVersion +
                        " , current version is " + version);
            }

            if (requestSubVersion != 0 && requestSubVersion != TxnStreamRpcServer.this.subVersion) {
                throw new PolardbxException(
                    "sub version is inconsistent, request version is " + requestSubVersion +
                        " , current version is " + subVersion);
            }
        }
    }
}
