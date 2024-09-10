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
