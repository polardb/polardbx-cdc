/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.CommonUtils;
import lombok.ToString;

/**
 * true tso | transactionId | seq | storageInstanceId
 */
@ToString
public class VirtualTSO implements Comparable<VirtualTSO> {

    public final long tso;
    public final long transactionId;
    public final long seq;

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
