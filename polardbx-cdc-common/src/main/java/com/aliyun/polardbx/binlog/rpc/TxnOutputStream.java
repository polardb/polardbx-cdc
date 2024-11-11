/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import io.grpc.stub.ServerCallStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ziyang.lb
 **/
public class TxnOutputStream<T> {
    private static final Logger logger = LoggerFactory.getLogger(TxnOutputStream.class);
    private final ServerCallStreamObserver<T> observer;
    private final AtomicLong onReadyCallBackCount;
    private int streamSeq;
    private Thread executingThead;

    public TxnOutputStream(ServerCallStreamObserver<T> observer) {
        this.observer = observer;
        this.onReadyCallBackCount = new AtomicLong(0);
        this.streamSeq = Integer.MAX_VALUE;
    }

    public TxnOutputStream(int streamSeq, ServerCallStreamObserver<T> observer) {
        this(observer);
        this.streamSeq = streamSeq;
    }

    public void onNext(T reply) {
        checkState();
        observer.onNext(reply);
    }

    public void checkState() {
        if (observer.isCancelled()) {
            throw new PolardbxException("stream observer has been cancelled.");
        }
    }

    // must be done on the GRPC thread before returning
    public void init() {
        observer.setOnCancelHandler(this::onCancelled);
        observer.setOnReadyHandler(this::onReady);
    }

    public synchronized boolean tryWait() throws InterruptedException {
        while (true) {
            if (observer.isCancelled()) {
                throw new PolardbxException("stream observer has been cancelled.");
            }
            if (observer.isReady()) {
                return true;
            }

            //最多等1ms，防止出现卡死
            wait(1);
        }
    }

    public boolean isReady() {
        return observer.isReady();
    }

    public void setExecutingThead(Thread executingThead) {
        this.executingThead = executingThead;
    }

    public void onCompleted() {
        observer.onCompleted();
    }

    public void onError(Throwable t) {
        observer.onError(t);
    }

    public int getStreamSeq() {
        return streamSeq;
    }

    private void onCancelled() {
        logger.info("Response observer has been cancelled.");
        if (executingThead != null) {
            executingThead.interrupt();
        }
    }

    private synchronized void onReady() {
        onReadyCallBackCount.incrementAndGet();
        notify();
    }
}
