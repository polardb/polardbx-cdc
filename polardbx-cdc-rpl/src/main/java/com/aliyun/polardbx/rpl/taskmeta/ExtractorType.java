/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author jiyue 2021/09/06 20:58
 * @since 5.0.0.0
 */
public enum ExtractorType {
    // MYSQL数据库全量
    DATA_IMPORT_FULL,

    // MYSQL数据库增量
    DATA_IMPORT_INC,

    // 用于replica全量
    RPL_FULL,

    // 用于replica增量
    RPL_INC,

    // 用于CDC的增量
    CDC_INC,

    // Full validation
    FULL_VALIDATION,

    // Full validation from polardbx to drds
    FULL_VALIDATION_CROSSCHECK,

    // Reconciliation
    RECONCILIATION,

    // Reconciliation from polardbx to drds
    RECONCILIATION_CROSSCHECK,

    // SQL闪回
    RECOVERY
}
