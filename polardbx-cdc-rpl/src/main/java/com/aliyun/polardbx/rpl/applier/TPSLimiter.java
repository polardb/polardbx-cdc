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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.github.rholder.retry.RetryException;
import com.google.common.util.concurrent.RateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TPSLimiter implements FlowLimiter {

    /*
     * 关于这里tps的WARNING：
     * 所有限流均是对该任务（rplTask）而言，对于实例的压力应是所有task之和
     * 对全量来说以event数目作为限流单位，但全量的event含有多row，默认为5000
     * 对增量来说以event数目作为限流单位，即真实的rps
     * 对校验来说以校验的一个batch作为限流单位，默认为1000
     * 考虑实现方便，暂时未做统一化
     * */
    private Integer tps;
    private FlowLimiter target;
    private RateLimiter rateLimiter;

    public TPSLimiter(Integer tps, FlowLimiter target) {
        this.tps = tps;
        this.target = target;
        this.rateLimiter = RateLimiter.create(tps);
    }

    @Override
    public void runTask(List<DBMSEvent> events) throws ExecutionException, RetryException {

        List<DBMSEvent> needHandleMsg = new ArrayList<>();
        int handleCount = 0, totalSize = events.size();
        int needSize = totalSize;
        int acq = needSize;
        while (needSize > 0) {
            acq = Math.min(acq, tps);
            if (acq > 0 && !rateLimiter.tryAcquire(acq)) {
                acq = acq >> 1;
                continue;
            }
            if (acq == 0) {
                acq = 1;
                rateLimiter.acquire(acq);
            }
            int start = handleCount;
            handleCount = handleCount + acq;
            for (int i = start; i < handleCount; i++) {
                needHandleMsg.add(events.get(i));
            }
            target.runTask(needHandleMsg);
            needSize = totalSize - handleCount;
            needHandleMsg.clear();
            acq = needSize;
        }
    }

    @Override
    public void runTranTask(List<Transaction> transactions) throws ExecutionException, RetryException {

        int nowSize = 0;
        List<Transaction> transferTransactions = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (nowSize == 0 || nowSize + transaction.getEventSize() <= tps) {
                nowSize += transaction.getEventSize();
                transferTransactions.add(transaction);
            } else {
                rateLimiter.acquire(nowSize);
                target.runTranTask(transferTransactions);
                transferTransactions.clear();
                nowSize = 0;
            }
        }
        if (!transferTransactions.isEmpty()) {
            rateLimiter.acquire(nowSize);
            target.runTranTask(transferTransactions);
        }
    }

    @Override
    public void acquire() {
        rateLimiter.acquire(1);
    }

}
