/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.sort;

/**
 * Created by ziyang.lb
 */
public enum SortItemType {
    /**
     * xa prepare events in 2pc transaction, begin events in single transaction
     */
    PreWrite,
    /**
     * xa commit events in 2pc transaction, xid events in single transaction
     */
    Commit,
    /**
     * xa rollback in 2pc transaction
     */
    Rollback
}
