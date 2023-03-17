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
