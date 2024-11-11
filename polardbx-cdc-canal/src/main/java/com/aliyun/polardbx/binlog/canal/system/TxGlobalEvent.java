/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.system;

import lombok.Data;

@Data
public class TxGlobalEvent {
    private final Long txGlobalTid;
    private final Long txGlobalTso;

    public TxGlobalEvent(Long txGlobalTid, Long txGlobalTso) {
        this.txGlobalTid = txGlobalTid;
        this.txGlobalTso = txGlobalTso;
    }
}
