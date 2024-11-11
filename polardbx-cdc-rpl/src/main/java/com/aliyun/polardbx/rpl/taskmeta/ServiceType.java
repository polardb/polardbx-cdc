/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author shicai.xsc 2020/12/8 14:04
 * @since 5.0.0.0
 */
public enum ServiceType {

    REPLICA_FULL,

    REPLICA_INC,
    REPLICA_FULL_VALIDATION,

    INC_COPY,

    FULL_COPY,

    FULL_VALIDATION,

    RECONCILIATION,

    FULL_VALIDATION_CROSSCHECK,

    RECONCILIATION_CROSSCHECK,

    CDC_INC,

    REC_SEARCH,

    REC_COMBINE;

    // 关闭full_copy的check，防止超大表count超时
    public static boolean supportRunningCheck(ServiceType type) {
        return type != REPLICA_FULL && type != REPLICA_INC &&
            type != FULL_COPY && type != FULL_VALIDATION && type != REPLICA_FULL_VALIDATION;
    }
}
