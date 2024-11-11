/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import java.util.Set;

/**
 * @author ziyang.lb
 **/
public class MockParameter {

    private int txnType;//0-hybrid;1-1pc;2-2pc
    private int dmlCount;
    private int eventSiz;
    private boolean useBuffer;
    private String partitionId;
    private Set<String> allParties;

    public int getTxnType() {
        return txnType;
    }

    public void setTxnType(int txnType) {
        this.txnType = txnType;
    }

    public int getDmlCount() {
        return dmlCount;
    }

    public void setDmlCount(int dmlCount) {
        this.dmlCount = dmlCount;
    }

    public int getEventSiz() {
        return eventSiz;
    }

    public void setEventSiz(int eventSiz) {
        this.eventSiz = eventSiz;
    }

    public boolean isUseBuffer() {
        return useBuffer;
    }

    public void setUseBuffer(boolean useBuffer) {
        this.useBuffer = useBuffer;
    }

    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    public Set<String> getAllParties() {
        return allParties;
    }

    public void setAllParties(Set<String> allParties) {
        this.allParties = allParties;
    }
}
