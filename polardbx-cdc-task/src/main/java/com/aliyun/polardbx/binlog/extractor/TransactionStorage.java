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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionCommitListener;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.sort.SortItem;
import com.aliyun.polardbx.binlog.extractor.sort.SortItemType;
import com.aliyun.polardbx.binlog.extractor.sort.Sorter;
import com.aliyun.polardbx.binlog.storage.PersistAllChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 解决空洞问题，解决大推进小TSO
 */
public class TransactionStorage implements TransactionCommitListener {

    private static final Logger logger = LoggerFactory.getLogger(TransactionStorage.class);
    private final Sorter sorter = new Sorter();
    private final LinkedList<TransactionGroup> transactionGroupList;
    private final ThreadRecorder recorder;
    private final PersistAllChecker persistAllChecker;
    private long size;

    public TransactionStorage(ThreadRecorder recorder) {
        this.transactionGroupList = new LinkedList<>();
        this.recorder = recorder;
        this.persistAllChecker = new PersistAllChecker();
    }

    public void add(Transaction t) {
        t.setListener(this);
        size++;
        sorter.pushTSItem(SortItem.builder().transaction(t).xid(t.getXid()).type(SortItemType.PreWrite).build());
        SortItem sortItem = sorter.peekFirstSortItem();
        if (sortItem != null) {
            recorder.setFirstTransInSorter(
                sortItem.getTransaction().getBinlogFileName() + ":" + sortItem.getTransaction().getStartLogPos());
            recorder.setFirstTransXidInSorter(sortItem.getXid());
        }
        tryPersistAll();
    }

    private void tryPersistAll() {
        persistAllChecker.checkWithCallback(() -> {
            logger.info("prepare to persist all txn buffer in sorter.");

            List<Transaction> tranList = sorter.getAllQueuedTransactions();
            if (!tranList.isEmpty()) {
                AtomicLong newlyPersistCount = new AtomicLong();
                AtomicLong heartbeatCount = new AtomicLong();
                AtomicLong hasPersistedCount = new AtomicLong();
                Transaction firstTrans = sorter.peekFirstSortItem().getTransaction();

                tranList.forEach(tran -> {
                    boolean flag = tran.persistBuffer();
                    if (flag) {
                        newlyPersistCount.incrementAndGet();
                    }
                    if (tran.isHeartbeat()) {
                        heartbeatCount.incrementAndGet();
                    }
                    if (tran.isBufferPersisted()) {
                        hasPersistedCount.incrementAndGet();
                    }
                });
                logger.warn("persisting detail info of this round in sorter is: total trans count is {}, newly "
                        + "persisted trans count is {}, all persisted trans count is {}, heartbeat trans count is {},"
                        + " first trans xid is {}, first trans pos is {}.", tranList.size(), newlyPersistCount.get(),
                    hasPersistedCount.get(), heartbeatCount.get(), firstTrans.getXid(),
                    firstTrans.getBinlogFileName() + ":" + firstTrans.getStartLogPos());
            } else {
                logger.info("persisting detail info of this round in sorter is empty.");
            }
            return null;
        });
    }

    public void purge() {
        doCommit();
        recorder.setQueuedTransSizeInSorter(size + "");
    }

    private void doCommit() {
        List<Transaction> availableTransaction;
        while (!(availableTransaction = sorter.getAvailableTrans()).isEmpty()) {
            Iterator<Transaction> it = availableTransaction.iterator();
            while (it.hasNext()) {
                Transaction transaction = it.next();
                if (filter(transaction)) {
                    transaction.release();
                    it.remove();
                    size--;
                }
            }
            if (availableTransaction.isEmpty()) {
                return;
            }
            transactionGroupList.add(new TransactionGroup((LinkedList<Transaction>) availableTransaction));
        }
    }

    public Transaction getByXid(String xid) {
        return sorter.getTransByXid(xid);
    }

    public TransactionGroup fetchNext() {
        TransactionGroup first = transactionGroupList.peekFirst();
        if (first != null) {
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
