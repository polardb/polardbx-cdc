/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

/**
 * Shared physical metadata contract for externalized columns.
 */
final class ExternalColumnMetaConstants {
    static final String EXTERNALIZED_ADDR_SUFFIX = "_addr_";
    static final String EXTERNALIZED_BLOB_REF_TYPE = "varchar(128)";

    private ExternalColumnMetaConstants() {
    }
}
