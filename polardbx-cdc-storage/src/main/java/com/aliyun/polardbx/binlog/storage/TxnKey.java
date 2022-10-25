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
package com.aliyun.polardbx.binlog.storage;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 *
 **/
public class TxnKey {

    /**
     * 事务标识符，要求全局唯一 <br/>
     * 如果是单机事务，可以构造一个虚拟txnId，如uuid；如果是分布式事务，取xid中的事务标识符即可
     */
    private final String txnId;

    /**
     * 分片标识符，标识LogEvent是在那个物理分片产生
     */
    private final String partitionId;

    public TxnKey(String txnId, String partitionId) {
        this.txnId = txnId;
        this.partitionId = partitionId;
    }

    public String getTxnId() {
        return txnId;
    }

    public String getPartitionId() {
        return partitionId;
    }

    /**
     * 对于2PC XA事务来说，在CN没有引入"并行写"之前，一个group只对应一个事务分支，partitionId对应的就是group name；在引入"并行写"之后
     * 一个group可以对应多个事务分支，partitionId对应的不再是group name，而是group@readViewSeq。为了避免多个Extractor线程之间的数据
     * 访问冲突，会基于group构建SubCache，此处需要将readViewSeq remove掉，否则会导致SubCache的数量膨胀
     * <p>
     * 对于1PC和单机事务来说，partitionId对应的是storageInstId，数量是可控的
     */
    public String getPartitionGroupId() {
        return StringUtils.substringBefore(partitionId, "@");
    }

    // equals和hashcode，用txnId和partitionId就够了
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TxnKey txnKey = (TxnKey) o;
        return txnId.equals(txnKey.txnId) && partitionId.equals(txnKey.partitionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnId, partitionId);
    }

    @Override
    public String toString() {
        return "TxnKey{" + "txnId='" + txnId + '\'' + ", partitionId='" + partitionId + '\'' + '}';
    }
}
