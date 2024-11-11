/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.canal.HandlerEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * @author chengjin.lyf on 2020/7/17 5:46 下午
 * @since 1.0.25
 */
public class TransactionGroup implements HandlerEvent {

    private final LinkedList<Transaction> transactionList;

    public TransactionGroup(LinkedList<Transaction> transactionList) {
        this.transactionList = transactionList;
    }

    public boolean isEmpty() {
        return transactionList.isEmpty();
    }

    public List<Transaction> getTransactionList() {
        return transactionList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransactionGroup begin :\n");
        transactionList.forEach(t -> {
            sb.append(t.getVirtualTsoStr()).append(" pos ： ").append(t.getStartLogPos()).append("\n");
        });
        sb.append("TransactionGroup end!");
        return sb.toString();
    }
}
