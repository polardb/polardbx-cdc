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
    private final static Logger logger = LoggerFactory.getLogger(TxnOutputStream.class);
    private final ServerCallStreamObserver<T> observer;
    private final AtomicLong onReadyCallBackCount;
    private Thread executingThead;

    public TxnOutputStream(ServerCallStreamObserver<T> observer) {
        this.observer = observer;
        this.onReadyCallBackCount = new AtomicLong(0);
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
