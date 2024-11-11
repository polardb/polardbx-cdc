/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Objects;

/**
 * Created by ziyang.lb
 **/
public class XaTransaction implements MergeTransaction {

    private static final Logger logger = LoggerFactory.getLogger(XaTransaction.class);

    /**
     * XA事务ID，全局唯一
     */
    private Long txnId;
    /**
     * 参与该XA事务的各分片的局部事务
     */
    private final HashMap<String, TxnToken> partitionTokens;
    /**
     * 该事务对应的真实tso
     */
    private String actualTso;
    /**
     * 代理Token，用来代表该事务，和非代理Token相比，代理Token的allParties会被赋值，下游依据
     */
    private TxnToken delegateToken;
    /**
     * 各个partition中最小的tso
     */
    private String minTso;
    /**
     * 该XA事务是否已经合并完成
     */
    private boolean complete;

    public XaTransaction() {
        this.partitionTokens = new HashMap<>();
        this.minTso = "";
    }

    public void addPartitionToken(TxnToken token) {
        if (partitionTokens.containsKey(token.getPartitionId())) {
            throw new PolardbxException("detected duplicate partition token : " + token);
        }

        if (StringUtils.isNotBlank(actualTso) && token.getTsoTransaction() && !CommonUtils.getActualTso(token.getTso())
            .equalsIgnoreCase(actualTso)) {
            throw new PolardbxException(
                String.format("Detected different tso for same txnId, tso is [%s,%s], txnId is [%s]",
                    CommonUtils.getActualTso(token.getTso()),
                    actualTso,
                    txnId));
        }

        if (txnId == null) {
            txnId = token.getTxnId();
        } else {
            if (!txnId.equals(token.getTxnId())) {
                throw new PolardbxException(
                    "txn id is different, previous txn id is " + txnId + " , this txn is " + token.getTxnId()
                        + ", and this token is " + token);
            }
        }

        if (delegateToken == null) {
            delegateToken = token;
        }

        if (StringUtils.isBlank(minTso)) {
            minTso = token.getTso();
        } else {
            minTso = token.getTso().compareTo(minTso) >= 0 ? minTso : token.getTso();
        }

        partitionTokens.put(token.getPartitionId(), token);
        actualTso = actualTso == null ? CommonUtils.getActualTso(token.getTso()) : actualTso;
    }

    public TxnToken getDelegateToken() {
        return delegateToken;
    }

    public void forceMarkComplete() {
        if (partitionTokens.isEmpty()) {
            throw new IllegalStateException("partition tokens is empty.");
        }

        if (logger.isDebugEnabled()) {
            logger.warn(
                "XaTransaction is forced to mark complete, txnId is [{}], partitionTokens is [{}], delegate token is [{}]",
                txnId,
                partitionTokens,
                delegateToken);
        }

        rebuild();
        complete = true;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getActualTso() {
        return actualTso;
    }

    public void clear() {
        this.txnId = null;
        this.partitionTokens.clear();
        this.actualTso = null;
        this.delegateToken = null;
        this.minTso = null;
        this.complete = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        XaTransaction that = (XaTransaction) o;
        return txnId.equals(that.txnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnId);
    }

    private void rebuild() {
        if (delegateToken == null) {
            logger.error("delegate token is null, txn id is {}.", txnId);
            partitionTokens.values().forEach(t -> logger.error("delegate error: partition token is {}.", t));
            throw new PolardbxException("delegate token can't be null.");
        }

        delegateToken = delegateToken.toBuilder()
            .clearAllParties()
            .addAllAllParties(partitionTokens.keySet())
            .setTso(minTso)
            .build();
    }
}
