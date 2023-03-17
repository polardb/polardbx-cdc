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
