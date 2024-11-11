/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.sort;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionFilter;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_SORT_HOLD_SIZE;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class Sorter {
    private static final Logger skipTransLogger = LoggerFactory.getLogger("SKIP_TRANS_LOG");

    private final Set<TxnKey> waitTrans;
    private final List<SortItem> items;
    private final Map<TxnKey, Transaction> transMap;
    private final PriorityQueue<TransNode> transQueue;//TODO,transQueue是可以持久化的
    private final AtomicReference<SortItem> firstSortItem;
    private SortItem maxSortItem;

    public Sorter() {
        this.waitTrans = new HashSet<>();
        this.items = new LinkedList<>();
        this.transMap = new HashMap<>();
        this.transQueue = new PriorityQueue<>();
        this.firstSortItem = new AtomicReference<>();
    }

    public void pushTSItem(SortItem item) {
        if (firstSortItem.compareAndSet(null, item)) {
            log.info("First received sortItem is : " + item);
        }

        if (item.getType() == SortItemType.PreWrite) {
            waitTrans.add(item.getTxnKey());
            if (transMap.put(item.getTxnKey(), item.getTransaction()) != null) {
                throw new PolardbxException("duplicate txn key for ： " + item.getTxnKey());
            }
        } else {
            if (transMap.get(item.getTxnKey()) == null) {
                if (TransactionFilter.shouldFilter(item.getTxnKey(),
                    item.getTransPosInfo().getBinlogFile(), item.getTransPosInfo().getPos())) {
                    return;
                }
                throw new PolardbxException("txn key is not existed in trans map for : " + item.getTxnKey());
            }
            waitTrans.remove(item.getTxnKey());
            if (item.getType() == SortItemType.Commit) {
                transQueue.add(new TransNode(item));
            }
        }
        items.add(item);
    }

    public int size() {
        return items.size();
    }

    public List<Transaction> getAvailableTrans(int fetchSize) {
        List<Transaction> result = new LinkedList<>();
        if (items.isEmpty()) {
            return result;
        }

        long holdSize = DynamicApplicationConfig.getLong(TASK_EXTRACT_SORT_HOLD_SIZE);
        if (holdSize > 0 && transMap.size() < holdSize) {
            return result;
        }

        trySkip();

        long startTime = System.currentTimeMillis();
        while (true) {
            if (items.size() == 0) {
                break;
            }

            SortItem item = items.get(0);
            if (item.getType() == SortItemType.PreWrite) {
                if (waitTrans.contains(item.getTxnKey())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Transaction {} is not ready.", item.getTxnKey());
                    }
                    break;
                } else {
                    items.remove(0);
                }
            } else {
                if (maxSortItem == null || item.getTransaction().compareTo(maxSortItem.getTransaction()) > 0) {
                    maxSortItem = item;
                }
                items.remove(0);
            }

            if (System.currentTimeMillis() - startTime > 500) {
                break;
            }
        }
        //  P1 P2 C1 P3 C2 C3
        TransNode t;
        while ((t = transQueue.peek()) != null && maxSortItem != null) {
            if (t.getTransaction().compareTo(maxSortItem.getTransaction()) <= 0) {
                t = transQueue.poll();
                transMap.remove(Objects.requireNonNull(t).getTxnKey());
                result.add(t.getTransaction());
                if (log.isDebugEnabled()) {
                    log.debug(t.getTransaction().toString());
                }
            } else {
                break;
            }

            if (fetchSize > 0 && result.size() >= fetchSize) {
                break;
            }
        }

        if (log.isDebugEnabled() && !result.isEmpty()) {
            log.debug("available trans result size is " + result.size());
            result.forEach(r -> {
                System.out.println();
                log.debug(r.toString());
            });
        }

        return result;
    }

    private void trySkip() {
        int skipThreshold = DynamicApplicationConfig.getInt(ConfigKeys.TASK_EXTRACT_FILTER_TRANS_THRESHOLD);
        if (items.size() > skipThreshold) {
            // 加一个优化， items 比较大的情况，可能有丢失commit或者rollback现象
            // 只清理头部即可
            SortItem item = items.get(0);
            if (item.getType() == SortItemType.PreWrite) {
                TxnKey txnKey = item.getTxnKey();
                if (TransactionFilter.shouldFilter(txnKey,
                    item.getTransPosInfo().getBinlogFile(), item.getTransPosInfo().getPos())) {
                    if (waitTrans.remove(txnKey)) {
                        //释放一下 transaction
                        item.getTransaction().release();
                    }
                    transMap.remove(txnKey);
                }
            }
        }
    }

    public Transaction getTransByTxnKey(TxnKey txnKey) {
        return transMap.get(txnKey);
    }

    public SortItem peekFirstSortItem() {
        return items.isEmpty() ? null : items.get(0);
    }

    public List<Transaction> getAllQueuedTransactions() {
        return Lists.newArrayList(transMap.values());
    }

    public void clear() {
        waitTrans.clear();
        items.clear();
        transMap.clear();
        maxSortItem = null;
    }

    public static class TransNode implements Comparable<TransNode> {
        private final TxnKey txnKey;
        private final Transaction transaction;

        public TransNode(SortItem item) {
            this.txnKey = item.getTxnKey();
            this.transaction = item.getTransaction();
        }

        @Override
        public int compareTo(TransNode o) {
            return transaction.compareTo(o.transaction);
        }

        public TxnKey getTxnKey() {
            return txnKey;
        }

        public Transaction getTransaction() {
            return transaction;
        }
    }
}
