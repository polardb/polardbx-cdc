/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.extractor.log.TransPosInfo;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionCommitListener;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.log.TxnKeyBuilder;
import com.aliyun.polardbx.binlog.extractor.sort.SortItem;
import com.aliyun.polardbx.binlog.extractor.sort.SortItemType;
import com.aliyun.polardbx.binlog.extractor.sort.Sorter;
import com.aliyun.polardbx.binlog.storage.PersistAllChecker;
import com.aliyun.polardbx.binlog.storage.PersistMode;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import lombok.SneakyThrows;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_TRANSACTION_GROUP_SIZE;
import static com.aliyun.polardbx.binlog.extractor.log.TxnKeyBuilder.getTransIdGroupIdPair;

/**
 * 解决空洞问题，解决大推进小TSO
 */
public class TransactionStorage implements TransactionCommitListener {

    private final Sorter sorter = new Sorter();
    private final ThreadRecorder recorder;
    private final PersistAllChecker persistAllChecker;
    private final int transactionGroupSize;
    private Transaction lastPersistAllPosition;
    private long size;

    public TransactionStorage(ThreadRecorder recorder) {
        this.recorder = recorder;
        this.persistAllChecker = new PersistAllChecker();
        this.transactionGroupSize = DynamicApplicationConfig.getInt(TASK_EXTRACT_TRANSACTION_GROUP_SIZE);
    }

    public void add(Transaction t) {
        t.setListener(this);
        sorter.pushTSItem(SortItem.builder().transaction(t).txnKey(t.getTxnKey()).type(SortItemType.PreWrite)
            .transPosInfo(TransPosInfo.builder().binlogFile(t.getBinlogFileName())
                .pos(t.getStartLogPos()).when(t.getWhen()).build())
            .build());
        refreshMetrics(1);
        tryPersistTransAfterAdd(t);
    }

    private void refreshMetrics(int diffSize) {
        size += diffSize;
        SortItem sortItem = sorter.peekFirstSortItem();
        if (sortItem != null) {
            recorder.setFirstTransPosInSorter(sortItem.getTransPosInfo().toString());
            recorder.setFirstTransKeyInSorter(String.format("tranId : %s, group : %s",
                Long.toHexString(sortItem.getTxnKey().getTxnId()), sortItem.getTxnKey().getPartitionGroupId()));
        }
        recorder.setQueuedTransSizeInSorter(size);
    }

    private void tryPersistTransAfterAdd(Transaction t) {
        if (persistAllChecker.isSupportPersist() && (persistAllChecker.getPersistMode() == PersistMode.FORCE
            || persistAllChecker.randomFlag())) {
            t.persistTxnBuffer();
            return;
        }

        // try force persist txn buffer，if current first trans is same to last persist all position
        Transaction currentFirstTrans = sorter.peekFirstSortItem().getTransaction();
        if (lastPersistAllPosition != null && currentFirstTrans != null
            && currentFirstTrans == lastPersistAllPosition) {
            t.persistTxnBuffer();
            return;//直接返回，无需再触发全量的check，避免无谓的计算
        }

        persistAllChecker.checkWithCallback(t.isLargeTrans(), () -> {
            List<Transaction> tranList = sorter.getAllQueuedTransactions();
            if (!tranList.isEmpty()) {
                tranList.forEach(Transaction::persistTxnBuffer);
                lastPersistAllPosition = currentFirstTrans;

                return "persisted transaction count of this round in sorter is " + tranList.size();
            } else {
                return "persisted transaction count of this round in sorter is zero.";
            }
        });
    }

    public void purge(Consumer<TransactionGroup> consumer) {
        List<Transaction> availableTransaction;
        while (!(availableTransaction = sorter.getAvailableTrans(transactionGroupSize)).isEmpty()) {
            int diffSize = availableTransaction.size();
            consumer.accept(new TransactionGroup((LinkedList<Transaction>) availableTransaction));
            refreshMetrics(-1 * diffSize);
        }
        refreshMetrics(0);
    }

    @SneakyThrows
    public Transaction getByXid(String xid, RuntimeContext rc) {
        TxnKey txnKey = TxnKeyBuilder.buildTxnKey(rc.getStorageHashCode(), getTransIdGroupIdPair(xid));
        return sorter.getTransByTxnKey(txnKey);
    }

    public void clear() {
        sorter.clear();
    }

    @Override
    public void onCommit(Transaction t) {
        if (t.isRollback()) {
            sorter.pushTSItem(SortItem.builder()
                .transaction(t).txnKey(t.getTxnKey()).type(SortItemType.Rollback)
                .transPosInfo(TransPosInfo.builder().binlogFile(t.getBinlogFileName())
                    .pos(t.getStartLogPos()).when(t.getWhen()).build()).build());
        } else {
            sorter.pushTSItem(SortItem.builder()
                .transaction(t).txnKey(t.getTxnKey()).type(SortItemType.Commit)
                .transPosInfo(TransPosInfo.builder().binlogFile(t.getBinlogFileName())
                    .pos(t.getStartLogPos()).when(t.getWhen()).build()).build());
        }
        tryPersistTransAfterCommit(t);
    }

    private void tryPersistTransAfterCommit(Transaction t) {
        if (t.shouldPersistEntity()) {
            t.persistEntity();
        }
    }
}
