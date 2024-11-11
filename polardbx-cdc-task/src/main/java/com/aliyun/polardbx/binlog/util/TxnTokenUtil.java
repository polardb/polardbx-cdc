/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnKey;

/**
 * created by ziyang.lb
 **/
public class TxnTokenUtil {

    public static void cleanTxnBuffer4Token(TxnToken token, Storage storage) {

        // 如果allPartiesCount > 0，说明这是一个事务合并后的delegate token，需要把所有分片的缓存都清空
        if (token.getAllPartiesCount() > 0) {
            token.getAllPartiesList().forEach(p -> {
                TxnKey key = new TxnKey(token.getTxnId(), p);
                storage.deleteAsync(key);
            });
        } else {
            TxnKey key = new TxnKey(token.getTxnId(), token.getPartitionId());
            storage.deleteAsync(key);
        }

    }
}
