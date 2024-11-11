/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class TsoSegment {
    private Long tso = 0L;
    private long txnId;
    private int sequence;

    public TsoSegment(TsoSegment other) {
        this.tso = other.tso;
        this.txnId = other.txnId;
        this.sequence = other.sequence;
    }

    public void trySet(Long newTso, Long newTxnId) {
        if (this.tso == null || newTso > tso) {
            this.tso = newTso;
            this.txnId = newTxnId;
            this.sequence = 0;
        } else if (newTso.equals(this.tso) && newTxnId > this.txnId) {
            this.txnId = newTxnId;
            this.sequence = 0;
        }
    }

    public Long getTso() {
        return tso;
    }

    public int nextSeq(long txnId) {
        if (txnId != this.txnId) {
            throw new PolardbxException("current value not equal max value" + txnId + ", " + this.txnId);
        }
        return ++sequence;
    }

    public long getTxnId() {
        return txnId;
    }

    public boolean isTsoAvaliable() {
        return this.tso != null && tso > 0;
    }
}
