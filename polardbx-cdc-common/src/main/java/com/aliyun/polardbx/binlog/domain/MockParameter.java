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
