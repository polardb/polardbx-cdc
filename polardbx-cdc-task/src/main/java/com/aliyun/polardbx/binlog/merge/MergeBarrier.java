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
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class MergeBarrier {
    private final TaskType taskType;
    /**
     * 正在进行排队，等待合并完成的XA事务
     */
    private final XaTransactionHolder xaTransactionHolder;
    /**
     * 是否对"事务策略不是TSO"的XA事务进行合并
     */
    private final boolean isMergeNoTsoXa;
    /**
     * 回调方法，当事务已达OK状态时调用
     */
    private final Consumer<TxnToken> consumer;

    public MergeBarrier(TaskType taskType, boolean isMergeNoTsoXa, Consumer<TxnToken> consumer) {
        this.taskType = taskType;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
        this.xaTransactionHolder = new XaTransactionHolder();
        this.consumer = consumer;
    }

    public void addTxnToken(TxnToken token) {
        // 如果是一个TsoXa事务，必须进行合并
        // 如果是一个NoTsoXa事务，当isMergeNoTsoXa开关为true的时候才进行合并
        if (token.getXaTxn() && (shouldMerge() && (token.getTsoTransaction() || isMergeNoTsoXa))) {
            addXaTokenForMerge(token);
        } else {
            addTokenWithoutMerge(token);
        }
    }

    public void addXaTokenForMerge(TxnToken token) {
        // 如果isMergeNoTsoXa为false，那么合并TsoXa事务的时候使用更精简的算法
        // 因为，某个TsoXa事务的tso一定是连续到达的，当收到的Token和lastToken不相等时，说明开启了一个新的事务，直接把前序事务标记为Complete即可
        if (!isMergeNoTsoXa) {
            assert token.getTsoTransaction();
            tryForceComplete(token, false);

            if (!xaTransactionHolder.isHolding) {
                xaTransactionHolder.begin();
            }
            xaTransactionHolder.xaTransaction.addPartitionToken(token);
        } else {
            throwUnSupport();
        }
    }

    public void addTokenWithoutMerge(TxnToken token) {
        if (!isMergeNoTsoXa) {
            tryForceComplete(token, true);
            consumer.accept(token);
        } else {
            throw new UnsupportedOperationException("Merging for NoTsoXa transaction is not supported yet.");
        }
    }

    public void flush() {
        if (!xaTransactionHolder.isHolding) {
            return;
        }

        if (!isMergeNoTsoXa) {
            doEmit();
        } else {
            throwUnSupport();
        }
    }

    private void tryForceComplete(TxnToken token, boolean isForceMark) {
        if (!xaTransactionHolder.isHolding) {
            return;
        }

        String actualTsoCurrent = CommonUtils.getActualTso(token.getTso());
        String actualTsoLast = xaTransactionHolder.xaTransaction.getActualTso();
        if ((isForceMark || actualTsoCurrent.compareTo(actualTsoLast) > 0)) {
            doEmit();
        }
    }

    private void doEmit() {
        xaTransactionHolder.xaTransaction.forceMarkComplete();
        consumer.accept(xaTransactionHolder.xaTransaction.getDelegateToken());
        xaTransactionHolder.commit();
    }

    private void throwUnSupport() {
        throw new UnsupportedOperationException("Merging for NoTsoXa transaction is not supported yet.");
    }

    private boolean shouldMerge() {
        //return taskType != TaskType.Dispatcher;
        return true;
    }

    private static class XaTransactionHolder {
        private final XaTransaction xaTransaction;//为了性能考虑，一直复用XaTransaction对象
        private volatile boolean isHolding;

        XaTransactionHolder() {
            xaTransaction = new XaTransaction();
            isHolding = false;
        }

        void begin() {
            isHolding = true;
        }

        void commit() {
            xaTransaction.clear();
            isHolding = false;
        }
    }
}
