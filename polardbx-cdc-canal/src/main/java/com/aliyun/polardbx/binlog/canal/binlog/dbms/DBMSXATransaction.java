/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

/**
 * @author shicai.xsc 2018/5/30 下午2:40
 * @since 5.0.0.0
 */
public class DBMSXATransaction {
    private String xid;

    private XATransactionType type;

    public DBMSXATransaction(String xid, XATransactionType type) {
        this.xid = xid;
        this.type = type;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public XATransactionType getType() {
        return type;
    }

    public void setType(XATransactionType type) {
        this.type = type;
    }
}

