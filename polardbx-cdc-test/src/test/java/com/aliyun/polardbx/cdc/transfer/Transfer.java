/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.transfer;

public class Transfer {

    private final int src;
    private final int dst;
    private final int amount;

    public Transfer(int src, int dst, int amount) {
        this.src = src;
        this.dst = dst;
        this.amount = amount;
    }

    public int getSrc() {
        return src;
    }

    public int getDst() {
        return dst;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Transfer{" + "src=" + src + ", dst=" + dst + ", amount=" + amount + '}';
    }
}
