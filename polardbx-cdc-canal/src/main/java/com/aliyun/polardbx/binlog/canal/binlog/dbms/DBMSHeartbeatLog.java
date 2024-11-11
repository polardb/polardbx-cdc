/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Defines a SQL statement from query-log that must be replicated.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DBMSHeartbeatLog extends DBMSEvent {
    private static final long serialVersionUID = -2075483176427758922L;

    protected DBMSAction action;

    private String logIdent;

    public DBMSHeartbeatLog(String logIdent) {
        this.logIdent = logIdent;
    }

    /**
     * Return the database update action.
     */
    @Override
    public DBMSAction getAction() {
        return action;
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public void setSchema(String schema) {

    }

    @Override
    public List<? extends DBMSOption> getOptions() {
        return null;
    }

    @Override
    public void setOptionValue(String name, Serializable value) {

    }

    public String getLogIdent() {
        return logIdent;
    }

    public void setLogIdent(String logIdent) {
        this.logIdent = logIdent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DBMSHeartbeatLog that = (DBMSHeartbeatLog) o;
        return action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(action);
    }
}
