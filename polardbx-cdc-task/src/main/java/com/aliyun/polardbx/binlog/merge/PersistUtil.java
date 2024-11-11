/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnKey;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * created by ziyang.lb
 **/
public class PersistUtil {

    public static int persist(Queue<MergeItem> queue, Storage storage) {
        Iterator<TxnToken> iterator = iterator(queue);
        int count = 0;
        while (iterator.hasNext()) {
            TxnToken token = iterator.next();
            TxnKey key = new TxnKey(token.getTxnId(), token.getPartitionId());
            TxnBuffer txnBuffer = storage.fetch(key);
            if (txnBuffer != null && txnBuffer.persist()) {
                count++;
            }
        }
        return count;
    }

    private static Iterator<TxnToken> iterator(Queue<MergeItem> queue) {
        LinkedList<TxnToken> list = new LinkedList<>();
        for (MergeItem mergeItem : queue) {
            list.add(mergeItem.getTxnToken());
        }
        return list.iterator();
    }

}
