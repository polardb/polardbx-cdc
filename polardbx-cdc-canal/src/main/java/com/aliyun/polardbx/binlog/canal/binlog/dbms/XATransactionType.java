/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

/**
 * @author shicai.xsc 2018/5/30 下午2:43
 * @since 5.0.0.0
 */
public enum XATransactionType {
    /**
     * 关于 MySQL XA 事务，参考：
     * https://dev.mysql.com/doc/refman/8.0/en/xa-statements.html
     */
    XA_START("XA START"),
    XA_END("XA END"),
    XA_COMMIT("XA COMMIT"),
    XA_ROLLBACK("XA ROLLBACK");

    private String name;

    XATransactionType(String typeName) {
        this.name = typeName;
    }

    public String getName() {
        return name;
    }
}
