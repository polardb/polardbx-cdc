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

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionCommitListener;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.sort.SortItem;
import com.aliyun.polardbx.binlog.extractor.sort.SortItemType;
import com.aliyun.polardbx.binlog.extractor.sort.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 解决空洞问题，解决大推进小TSO
 */
public class TransactionStorage implements TransactionCommitListener {

    private static final Logger logger = LoggerFactory.getLogger(TransactionStorage.class);
    private final Sorter sorter = new Sorter();
    /**
     * 可以下推的group list;
     */
    private final LinkedList<TransactionGroup> transactionGroupList = new LinkedList<>();
    private final ThreadRecorder recorder;
    private long timestamp;
    private long size;

    public TransactionStorage(ThreadRecorder recorder) {
        this.recorder = recorder;
    }

    public void add(Transaction t) {
        t.setListener(this);
        size++;
        sorter.pushTSItem(SortItem.builder().transaction(t).xid(t.getXid()).type(SortItemType.PreWrite).build());
    }

    public void purge() {
        doCommit();
        long now = System.currentTimeMillis();
        recorder.setCommitSizeChange(size + "");
        if (now - timestamp > 10000L) {
            timestamp = now;
            if (sorter.size() > 1000) {
                logger.warn("sort size : " + sorter.size());
            }
        }
    }

    private void doCommit() {
        List<Transaction> avaliableTransaction;
        while (!(avaliableTransaction = sorter.getAvailableTrans()).isEmpty()) {
            Iterator<Transaction> it = avaliableTransaction.iterator();
            while (it.hasNext()) {
                Transaction transaction = it.next();
                if (filter(transaction)) {
                    transaction.release();
                    it.remove();
                    size--;
                }
            }
            if (avaliableTransaction.isEmpty()) {
                return;
            }
            transactionGroupList.add(new TransactionGroup((LinkedList<Transaction>) avaliableTransaction));
            merge();
            checkSize();
        }
    }

    private void print(List<Transaction> avaliableTransaction) {
        StringBuilder sb = new StringBuilder();
        sb.append("\ntransaction group begin:\n");
        avaliableTransaction.forEach(t -> {
            sb.append(t.getVirtualTSO()).append(":").append(t.getStartLogPos()).append("\n");
        });
        sb.append("transaction group end");
        logger.error(sb.toString());
    }

    private void merge() {
        if (transactionGroupList.size() < 2) {
            return;
        }
        TransactionGroup p1, p2;
        Iterator<TransactionGroup> it = transactionGroupList.iterator();
        p1 = it.next();
        boolean canNotPush;
        boolean allSingle = true;
        while (it.hasNext()) {
            p2 = it.next();
            while ((canNotPush = p1.getMaxTSO() >= p2.getMinTSO())) {
                Transaction t = p2.pollFirst();
                if (t.isXa()) {
                    allSingle = false;
                }
                p1.append(t);
                if (p2.isEmpty()) {
                    it.remove();
                    break;
                }
            }
            if (allSingle) {
                canNotPush = false;
            }
            if (!canNotPush) {
                p1.setAvailable(true);
                p1 = p2;
            }
        }

    }

    private void checkSize() {
        if (transactionGroupList.isEmpty()) {
            return;
        }
        TransactionGroup p1 = transactionGroupList.peekFirst();
        //大于1000直接下推
        if (size > 1000) {
            p1.setAvailable(true);
        }
    }

    public Transaction getByXid(String xid) {
        return sorter.getTransByXid(xid);
    }

    public TransactionGroup fetchNext() {
        TransactionGroup first = transactionGroupList.peekFirst();
        if (first != null && first.isAvailable()) {
            size -= first.getTransactionList().size();
            return transactionGroupList.pollFirst();
        }
        return null;
    }

    public boolean filter(Transaction tran) {
        return tran.needRevert() || !tran.canNotFilter();
    }

    public void clear() {
        sorter.clear();
        transactionGroupList.clear();
    }

    @Override
    public void onCommit(Transaction t) {
        if (t.isRollback()) {
            sorter.pushTSItem(SortItem.builder().transaction(t).xid(t.getXid()).type(SortItemType.Rollback).build());
        } else {
            sorter.pushTSItem(SortItem.builder().transaction(t).xid(t.getXid()).type(SortItemType.Commit).build());
        }
    }
}
