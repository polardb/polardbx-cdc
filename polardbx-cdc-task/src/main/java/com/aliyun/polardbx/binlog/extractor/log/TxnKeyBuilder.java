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
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import static com.aliyun.polardbx.binlog.canal.LogEventUtil.getGroupWithReadViewSeqFromXid;
import static com.aliyun.polardbx.binlog.canal.LogEventUtil.getTranIdFromXid;

/**
 * created by ziyang.lb
 **/
public class TxnKeyBuilder {
    private static final String ENCODING = "UTF-8";

    public static Pair<Long, String> getTransIdGroupIdPair() {
        return getTransIdGroupIdPair(null);
    }

    @SneakyThrows
    public static Pair<Long, String> getTransIdGroupIdPair(String xid) {
        long transactionId;
        String groupId;

        if (StringUtils.isBlank(xid)) {
            transactionId = Math.abs(CommonUtils.randomXid());
            groupId = "";
        } else {
            if (LogEventUtil.isValidXid(xid)) {
                transactionId = getTranIdFromXid(xid, ENCODING);
                groupId = getGroupWithReadViewSeqFromXid(xid, ENCODING);
            } else {
                transactionId = Long.MAX_VALUE;
                groupId = xid;
            }
        }
        return Pair.of(transactionId, groupId);
    }

    public static TxnKey buildTxnKey(String storageHashCode, Pair<Long, String> pair) {
        try {
            if (pair.getKey() == Long.MAX_VALUE) {
                return new TxnKey(pair.getKey(), pair.getValue());
            } else {
                if (StringUtils.isBlank(pair.getValue())) {
                    return new TxnKey(pair.getKey(), storageHashCode.intern());
                } else {
                    return new TxnKey(pair.getKey(), buildPartitionId(storageHashCode, pair.getValue()));
                }
            }
        } catch (Exception e) {
            throw new PolardbxException("generate partition id failed", e);
        }
    }

    public static String buildPartitionId(String storageHashCode, String groupWithReadViewSeq) {
        return (storageHashCode + "_" + groupWithReadViewSeq).intern();
    }
}
