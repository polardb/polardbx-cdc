/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.protocol.TxnToken;

public class DumperContext {
    private static TxnToken txnToken = null;

    public static TxnToken getTxnToken() {
        return txnToken;
    }

    public static void setTxnToken(TxnToken txnToken) {
        DumperContext.txnToken = txnToken;
    }

    public static void clear() {
        setTxnToken(null);
    }
}
