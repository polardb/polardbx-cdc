/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.sql.Timestamp;

/**
 * Defines a SQL statement from query-log that must be replicated.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
@Deprecated
public abstract class DBMSQueryLog extends DBMSEvent {
    private static final long serialVersionUID = -2075483176427758922L;

    protected DBMSAction action;

    /**
     * Return the database update action.
     */
    @Override
    public DBMSAction getAction() {
        if (action == null) {
            action = DBMSAction.fromQuery(getQuery());
        }
        return action;
    }

    /**
     * Return the logging query.
     */
    public abstract String getQuery();

    /**
     * Change the logging query.
     */
    public abstract void setQuery(String query);

    /**
     * Return the timestamp of query log according.
     */
    public abstract Timestamp getTimestamp();

    /**
     * Return the errorCode of query log executing.
     */
    public abstract int getErrorCode();

    /**
     * Return the value of sql mode in binlog.
     */
    public abstract long getSqlMode();

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("action: ");
        builder.append(this.getAction());
        builder.append(", schema: ");
        builder.append(this.getSchema());
        builder.append(",\n    query: ");
        builder.append(this.getQuery());
        builder.append(",\n    timestamp: ");
        builder.append(this.getTimestamp());
        builder.append(", errorCode: ");
        builder.append(this.getErrorCode());
        for (DBMSOption option : this.getOptions()) {
            builder.append(",\n    option: ");
            builder.append(option);
        }
        builder.append(')');
        return builder.toString();
    }
}
