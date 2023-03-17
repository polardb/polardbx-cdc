/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
