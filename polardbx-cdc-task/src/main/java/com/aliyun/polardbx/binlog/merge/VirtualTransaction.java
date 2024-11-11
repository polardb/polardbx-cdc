/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.protocol.TxnToken;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class VirtualTransaction implements MergeTransaction {

    /**
     * 一批连续的单机事务 or 一阶段XA事务 or 不要进行合并的NoTsoXA事务的partial
     */
    private final List<TxnToken> tokens;

    public VirtualTransaction(TxnToken token) {
        this();
        this.appendToken(token);
    }

    public VirtualTransaction() {
        tokens = new LinkedList<>();
    }

    public void appendToken(TxnToken token) {
        tokens.add(token);
    }

    public List<TxnToken> tokens() {
        return tokens;
    }
}
