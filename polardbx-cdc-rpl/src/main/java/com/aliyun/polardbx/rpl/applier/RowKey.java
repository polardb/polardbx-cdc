/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/2/20 10:25
 * @since 5.0.0.0
 */
public class RowKey {

    private final String schema;
    private final String table;
    private final Map<Integer, Serializable> keys;

    public RowKey(DefaultRowChange rowChange, List<Integer> identifyColumns) {
        Map<Integer, Serializable> keys = new HashMap<>(identifyColumns.size());
        for (Integer columnIndex : identifyColumns) {
            keys.put(columnIndex, rowChange.getRowValue(1, columnIndex));
        }
        this.keys = keys;
        this.schema = rowChange.getSchema();
        this.table = rowChange.getTable();
    }

    public RowKey(DefaultRowChange rowChange, Map<Integer, Serializable> keys) {
        this.keys = keys;
        this.schema = rowChange.getSchema();
        this.table = rowChange.getTable();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((keys == null) ? 0 : keys.hashCode());
        result = prime * result + ((schema == null) ? 0 : schema.hashCode());
        result = prime * result + ((table == null) ? 0 : table.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        RowKey other = (RowKey) obj;

        if (keys == null) {
            if (other.keys != null) {
                return false;
            }
        } else if (!keys.equals(other.keys)) {
            return false;
        }

        if (schema == null) {
            if (other.schema != null) {
                return false;
            }
        } else if (!schema.equals(other.schema)) {
            return false;
        }

        if (table == null) {
            if (other.table != null) {
                return false;
            }
        } else if (!table.equals(other.table)) {
            return false;
        }

        return true;
    }
}
