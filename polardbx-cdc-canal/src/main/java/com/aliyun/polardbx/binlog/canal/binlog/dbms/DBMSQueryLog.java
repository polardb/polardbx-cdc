/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.sql.Timestamp;

/**
 * Defines a SQL statement from query-log that must be replicated.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSQueryLog extends DBMSEvent {
    private static final long serialVersionUID = -2075483176427758922L;

    protected transient DBMSAction action;

    /**
     * Return the database update action.
     */
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
