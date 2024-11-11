/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.List;

/**
 * 事务开始
 *
 * @author agapple 2017年5月11日 下午6:46:22
 * @since 3.2.4
 */
public class DBMSTransactionEnd extends DBMSEvent {

    private static final long serialVersionUID = -5072768643669142562L;

    private Long transactionId;
    private String tso;

    @Override
    public DBMSAction getAction() {
        return DBMSAction.OTHER;
    }

    @Override
    public String getSchema() {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public void setSchema(String schema) {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public List<? extends DBMSOption> getOptions() {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public void setOptionValue(String name, Serializable value) {
        throw new IllegalArgumentException("not support");
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getTso() {
        return tso;
    }

    public void setTso(String tso) {
        this.tso = tso;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("xid: ");
        builder.append(transactionId);
        builder.append(')');
        return builder.toString();
    }

}
