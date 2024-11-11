/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.ToString;

import java.io.Serializable;

/**
 * true tso | transactionId | seq | storageInstanceId
 */
@ToString
public class VirtualTSO implements Comparable<VirtualTSO>, Serializable {

    public long tso;
    public long transactionId;
    public long seq;

    public VirtualTSO() {

    }

    public VirtualTSO(long tso, long transactionId, int seq) {
        this.tso = tso;
        this.transactionId = transactionId;
        this.seq = seq;
    }

    public VirtualTSO(String rtso) {
        tso = CommonUtils.getTsoTimestamp(rtso);
        transactionId = CommonUtils.getTransactionId(rtso);
        seq = Long.parseLong(rtso.substring(38, 48));
    }

    @Override
    public int compareTo(VirtualTSO o) {
        long cmp = (tso - o.tso);
        if (cmp == 0) {
            cmp = transactionId - o.transactionId;
            if (cmp == 0) {
                cmp = seq - o.seq;
            }
        }
        if (cmp > 0) {
            return 1;
        } else if (cmp == 0) {
            return 0;
        }
        return -1;
    }
}
