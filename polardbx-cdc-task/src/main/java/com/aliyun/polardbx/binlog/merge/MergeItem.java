/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

    @Override
    public String toString() {
        return "MergeItem{" +
            "sourceId='" + sourceId + '\'' +
            ", txnToken=" + txnToken +
            ", mergeGroupId='" + mergeGroupId + '\'' +
            '}';
    }
}
