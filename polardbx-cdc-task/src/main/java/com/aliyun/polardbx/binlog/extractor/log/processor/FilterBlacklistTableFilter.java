/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log.processor;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public class FilterBlacklistTableFilter implements EventFilter {
    private Pattern pattern;

    public FilterBlacklistTableFilter(String regex) {
        pattern = Pattern.compile(regex);
    }

    public boolean doFilter1(String dbName, String tableName) {
        String key = dbName + "." + tableName;
        boolean ignore = pattern.matcher(key).matches();
        if (ignore) {
            log.warn("detected event : " + key + " match black table, will ignore it");
        }
        return ignore;
    }

    @Override
    public boolean doFilter(Transaction transaction, LogEvent event) {
        if (pattern == null) {
            return false;
        }
        if (event.getHeader().getType() == LogEvent.TABLE_MAP_EVENT) {
            TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
            return doFilter1(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName());
        }
        if (event instanceof RowsLogEvent) {
            RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
            TableMapLogEvent tableMapLogEvent = rowsLogEvent.getTable();
            return doFilter1(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName());
        }
        return false;
    }
}
