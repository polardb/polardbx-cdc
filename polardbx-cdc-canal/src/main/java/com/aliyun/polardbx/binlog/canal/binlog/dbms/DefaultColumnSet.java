/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * This class creates a default SQL column set implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultColumnSet extends DBMSColumnSet {
    private static final long serialVersionUID = -3429762238191668175L;

    protected List<? extends DBMSColumn> columns;
    protected Set<String> externalizedColumnNames;

    public DefaultColumnSet() {
    }

    /**
     * Create a new <code>DefaultColumnSet</code> object.
     */
    public DefaultColumnSet(List<? extends DBMSColumn> columns) {
        this.columns = columns;
        initColumns(columns);
    }

    /**
     * Create a column set carrying the event-version externalized-column metadata.
     */
    public DefaultColumnSet(List<? extends DBMSColumn> columns, Set<String> externalizedColumnNames) {
        this(columns);
        setExternalizedColumnNames(externalizedColumnNames);
    }

    /**
     * Return all columns in object.
     */
    public List<? extends DBMSColumn> getColumns() {
        return columns;
    }

    @Override
    public Set<String> getExternalizedColumnNames() {
        return externalizedColumnNames == null ? Collections.emptySet() : externalizedColumnNames;
    }

    public void setExternalizedColumnNames(Set<String> externalizedColumnNames) {
        if (externalizedColumnNames == null || externalizedColumnNames.isEmpty()) {
            this.externalizedColumnNames = Collections.emptySet();
            return;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String columnName : externalizedColumnNames) {
            normalizedNames.add(columnName.toLowerCase(Locale.ROOT));
        }
        this.externalizedColumnNames = Collections.unmodifiableSet(normalizedNames);
    }

    /**
     *
     */
    public void setColumns(List<? extends DBMSColumn> columns) {
        this.columns = columns;
    }
}
