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

package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author chengjin.lyf on 2020/7/17 5:46 下午
 * @since 1.0.25
 */
public class TransactionGroup implements HandlerEvent {

    private LinkedList<Transaction> transactionList;
    private long maxTSO;
    private long minTSO;
    private boolean available;

    public TransactionGroup(LinkedList<Transaction> transactionList) {
        this.transactionList = transactionList;
        this.reOrder();
    }

    public void reOrder() {
        transactionList.sort(Comparator.comparing(Transaction::getVirtualTSO));
        this.maxTSO = CommonUtils.getTsoTimestamp(transactionList.getLast().getVirtualTSO());
        this.minTSO = CommonUtils.getTsoTimestamp(transactionList.getFirst().getVirtualTSO());
    }

    public long getMaxTSO() {
        return maxTSO;
    }

    public long getMinTSO() {
        return minTSO;
    }

    public Transaction pollFirst() {
        Transaction t = this.transactionList.pollFirst();
        if (transactionList.isEmpty()) {
            this.minTSO = -1;
        } else {
            this.minTSO = CommonUtils.getTsoTimestamp(transactionList.getFirst().getVirtualTSO());
        }
        return t;
    }

    public boolean isEmpty() {
        return transactionList.isEmpty();
    }

    public void append(Transaction transaction) {
        ListIterator<Transaction> it = transactionList.listIterator(transactionList.size());
        Transaction last = transactionList.getLast();
        do {
            Transaction pre = it.previous();
            if (transaction.getVirtualTSO().compareTo(pre.getVirtualTSO()) >= 0) {
                // ListIterator will add before next, so do next after previous
                it.next();
                it.add(transaction);
                if (pre == last) {
                    this.maxTSO = CommonUtils.getTsoTimestamp(transaction.getVirtualTSO());
                }
                break;
            }
        } while (it.hasPrevious());

        if (!it.hasPrevious()) {
            it.add(transaction);
            this.minTSO = CommonUtils.getTsoTimestamp(transaction.getVirtualTSO());
        }
    }

    public List<Transaction> getTransactionList() {
        return transactionList;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransactionGroup begin :\n");
        transactionList.forEach(t -> {
            sb.append(t.getVirtualTSO()).append(" pos ： ").append(t.getStartLogPos()).append("\n");
        });
        sb.append("TransactionGroup end!");
        return sb.toString();
    }
}
