/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Defines a SQL statement from query-log that must be replicated. Default implementation of {@link DefaultQueryLog
 * DefaultQueryLog}
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultQueryLog extends DBMSQueryLog {

    private static final long serialVersionUID = -3393475008857141186L;

    protected String schema;
    protected String query;
    protected Timestamp timestamp;
    protected int errorCode;
    // binlog保证每一个ddl均带有sql mode
    // 因此不需要区分没有sql mode和sql mode = ''
    protected long sqlMode;
    protected long execTime;
    protected List<DBMSOption> options;
    protected AtomicBoolean firstDdl;
    protected int parallelSeq;
    protected TableMeta tableMeta;

    public DefaultQueryLog() {
    }

    /**
     * Create new <code>DefaultQueryLog</code> object.
     */
    public DefaultQueryLog(String schema, String query, Timestamp timestamp, int errorCode, long execTime) {
        this.schema = schema;
        this.query = query;
        this.timestamp = timestamp;
        this.errorCode = errorCode;
        this.execTime = execTime;
    }

    /**
     * Create new <code>DefaultQueryLog</code> object.
     */
    public DefaultQueryLog(String schema, String query, Timestamp timestamp, int errorCode, long sqlMode,
                           DBMSAction action, long execTime) {
        this.schema = schema;
        this.query = query;
        this.timestamp = timestamp;
        this.errorCode = errorCode;
        this.sqlMode = sqlMode;
        this.action = action;
        this.execTime = execTime;
    }

    /**
     * Create new <code>DefaultQueryLog</code> object with options.
     */
    public DefaultQueryLog(String schema, String query, Timestamp timestamp, int errorCode, List<DBMSOption> options) {
        this.schema = schema;
        this.query = query;
        this.timestamp = timestamp;
        this.errorCode = errorCode;
        this.options = options;
    }

    /**
     * Return the schema of query log. For notice, query may change the table in another schema.
     */
    @Override
    public String getSchema() {
        return schema;
    }

    /**
     * Change the schema of query log.
     */
    @Override
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Return the logging query.
     */
    @Override
    public String getQuery() {
        return query;
    }

    /**
     * Change the logging query.
     */
    @Override
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Return the timestamp of query log according.
     */
    @Override
    public Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Change the timestamp of query log according.
     */
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Return the errorCode of query log executing.
     */
    @Override
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Return the value of query sql mode in binlog.
     */
    @Override
    public long getSqlMode() {
        return sqlMode;
    }

    /**
     * Change the errorCode of query log executing.
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public long getExecTime() {
        return execTime;
    }

    public void setExecTime(long execTime) {
        this.execTime = execTime;
    }

    public AtomicBoolean getFirstDdl() {
        return firstDdl;
    }

    public void setFirstDdl(AtomicBoolean firstDdl) {
        this.firstDdl = firstDdl;
    }

    public int getParallelSeq() {
        return parallelSeq;
    }

    public void setParallelSeq(int parallelSeq) {
        this.parallelSeq = parallelSeq;
    }

    public TableMeta getTableMeta() {
        return tableMeta;
    }

    public void setTableMeta(TableMeta tableMeta) {
        this.tableMeta = tableMeta;
    }

    /**
     * Returns the session options.
     */
    @Override
    public List<? extends DBMSOption> getOptions() {
        if (options != null) {
            return options;
        }
        return Collections.emptyList();
    }

    /**
     * Set the session option.
     */
    @Override
    public void setOptionValue(String name, Serializable value) {
        DBMSOption option = getOption(name);
        if (option != null) {
            option.setValue(value);
            return;
        }
        option = new DefaultOption(name, value);
        putOption(option);
    }

    /**
     * Add the session option.
     */
    @Override
    public DBMSOption putOption(DBMSOption option) {
        if (options == null) {
            options = new ArrayList<DBMSOption>(4);
        }

        DBMSOption exist = super.putOption(option);
        if (exist != null) {
            options.remove(exist);
        }
        options.add(option);
        return exist;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultQueryLog that = (DefaultQueryLog) o;
        return errorCode == that.errorCode && sqlMode == that.sqlMode && execTime == that.execTime
            && parallelSeq == that.parallelSeq && Objects.equals(schema, that.schema) && Objects.equals(
            query, that.query) && Objects.equals(timestamp, that.timestamp) && Objects.equals(options,
            that.options) && Objects.equals(tableMeta, that.tableMeta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, query, timestamp, errorCode, sqlMode, execTime, options, parallelSeq, tableMeta);
    }
}
