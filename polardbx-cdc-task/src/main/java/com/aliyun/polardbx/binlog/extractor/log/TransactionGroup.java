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
            sb.append(t.getVirtualTSO()).append(" pos ： ").append(t.getStartLogPos()).append("\n");
        });
        sb.append("TransactionGroup end!");
        return sb.toString();
    }
}
