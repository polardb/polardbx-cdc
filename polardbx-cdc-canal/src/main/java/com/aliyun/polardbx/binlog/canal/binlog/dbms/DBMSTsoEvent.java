/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.List;

public class DBMSTsoEvent extends DBMSEvent {

    private String tso;

    public DBMSTsoEvent(String tso) {
        this.tso = tso;
    }

    public String getTso() {
        return tso;
    }

    public void setTso(String tso) {
        this.tso = tso;
    }

    @Override
    public DBMSAction getAction() {
        throw new IllegalArgumentException("not support");
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

    @Override
    public String toString() {
        return "DBMSTsoEvent{" +
            "tso='" + tso + '\'' +
            '}';
    }
}
