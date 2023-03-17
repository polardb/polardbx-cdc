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

/**
 * Created by ziyang.lb
 **/
public class MergeItem implements Comparable<MergeItem> {

    private String sourceId;
    private TxnToken txnToken;
    private String mergeGroupId;
    private MergeGroup mergeGroup;

    public MergeItem() {
    }

    public MergeItem(String sourceId, TxnToken txnToken) {
        this.sourceId = sourceId;
        this.txnToken = txnToken;
    }

    public String getSourceId() {
        return sourceId;
    }

    public TxnToken getTxnToken() {
        return txnToken;
    }

    public MergeGroup getMergeGroup() {
        return mergeGroup;
    }

    public void setMergeGroup(MergeGroup mergeGroup) {
        this.mergeGroup = mergeGroup;
    }

    public String getMergeGroupId() {
        return mergeGroupId;
    }

    public void setMergeGroupId(String mergeGroupId) {
        this.mergeGroupId = mergeGroupId;
    }

    @Override
    public int compareTo(MergeItem o) {
        TxnToken thisToken = getTxnToken();
        TxnToken thatToken = o.getTxnToken();
        return thisToken.getTso().compareTo(thatToken.getTso());
    }

    public MergeItem copy() {
        MergeItem target = new MergeItem();
        target.sourceId = this.sourceId;
        target.txnToken = this.txnToken;
        target.mergeGroupId = this.mergeGroupId;
        target.mergeGroup = this.mergeGroup;
        return target;
    }
}
