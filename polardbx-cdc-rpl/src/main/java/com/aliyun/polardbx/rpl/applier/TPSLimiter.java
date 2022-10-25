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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.google.common.util.concurrent.RateLimiter;

import java.util.ArrayList;
import java.util.List;

public class TPSLimiter implements FlowLimiter {

    private Integer tps;
    private FlowLimiter target;
    private RateLimiter rateLimiter;

    public TPSLimiter(Integer tps, FlowLimiter target) {
        this.tps = tps;
        this.target = target;
        this.rateLimiter = RateLimiter.create(tps);
    }

    @Override
    public boolean runTask(List<DBMSEvent> events) {

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
            if (!target.runTask(needHandleMsg)) {
                return false;
            }
            needSize = totalSize - handleCount;
            needHandleMsg.clear();
            acq = needSize;
        }
        return true;
    }

    @Override
    public boolean runTranTask(List<Transaction> transactions) {

        int nowSize = 0;
        List<Transaction> transferTransactions = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (nowSize == 0 || nowSize + transaction.getEventSize() <= tps) {
                nowSize += transaction.getEventSize();
                transferTransactions.add(transaction);
            } else {
                rateLimiter.acquire(nowSize);
                if (!target.runTranTask(transferTransactions)) {
                    return false;
                }
                transferTransactions.clear();
                nowSize = 0;
            }
        }
        if (!transferTransactions.isEmpty()) {
            rateLimiter.acquire(nowSize);
            if (!target.runTranTask(transferTransactions)) {
                return false;
            }
        }
        return true;
    }

}
