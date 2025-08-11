/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.alibaba.polardbx.druid.util.FnvHash;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
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

    private static volatile Boolean appendXidFlag;

    public static Pair<Long, String> getTransIdGroupIdPair() {
        return getTransIdGroupIdPair(null);
    }

    public static boolean isAppendXidFlag() {
        if (appendXidFlag == null) {
            synchronized (TxnKeyBuilder.class) {
                if (appendXidFlag == null) {
                    appendXidFlag =
                        DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_BUILD_PARTITION_APPEND_HASH_XID);
                }
            }
        }
        return appendXidFlag;
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
                if (isAppendXidFlag()) {
                    groupId = groupId + "@" + FnvHash.hashCode64(xid);
                }
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
