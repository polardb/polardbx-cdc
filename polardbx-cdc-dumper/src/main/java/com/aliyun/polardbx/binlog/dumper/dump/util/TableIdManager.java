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
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_TABLE_ID_BASE_VALUE;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT_V1;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class TableIdManager {

    /**
     * TableId最大可占用6个字节
     */
    private static final long TABLE_ID_MAX_VALUE = 0XFFFFFFFFFFFFL;
    /**
     * TableId达到最大值的一半时，就尝试进行一下Reset，避免越界
     */
    private static final long TABLE_ID_RESET_THRESHOLD = TABLE_ID_MAX_VALUE >> 1;
    /**
     * TableId的初始值
     */
    private static final long TABLE_ID_BASE_VALUE = DynamicApplicationConfig.getLong(BINLOG_WRITE_TABLE_ID_BASE_VALUE);

    private final HashMap<String, Long> cache = new HashMap<>();
    private AtomicLong counter;

    //只有当创建了一个新的Binlog文件时，tryReset才可以设置为true
    public TableIdManager(long initValue, boolean tryReset) {
        if (tryReset && initValue >= TABLE_ID_RESET_THRESHOLD) {
            log.info("reset table id , from initValue {} to baseValue {}.", initValue, TABLE_ID_BASE_VALUE);
            initValue = TABLE_ID_BASE_VALUE;
        }
        init(initValue);
    }

    public void invalidate(String schema, String table) {
        if (StringUtils.isBlank(schema) || StringUtils.isBlank(table)) {
            return;
        }
        cache.remove(buildKey(schema, table));
    }

    //非线程安全
    public long getTableId(String schema, String table) {
        String key = buildKey(schema, table);
        Long value = cache.get(key);
        if (value == null) {
            return cache.computeIfAbsent(key, k -> {
                long newValue = TableIdManager.this.counter.addAndGet(1);
                //容错逻辑，实际场景不应该出现大于MAX_VALUE的场景，触达TABLE_ID_RESET_THRESHOLD后就应该被reset了
                while (newValue > TABLE_ID_MAX_VALUE) {
                    newValue = newValue - TABLE_ID_MAX_VALUE;
                }
                return newValue;
            });
        } else {
            return value;
        }
    }

    public void tryReset() {
        if (counter.get() >= TABLE_ID_RESET_THRESHOLD) {
            init(TABLE_ID_BASE_VALUE);
            log.info("table id counter is reset.");
        }
    }

    private void init(long initValue) {
        counter = new AtomicLong(initValue);
    }

    private String buildKey(String schema, String table) {
        return schema + "." + table;
    }

    public static boolean containsTableId(byte logEventType) {
        return logEventType == UPDATE_ROWS_EVENT || logEventType == UPDATE_ROWS_EVENT_V1
            || logEventType == DELETE_ROWS_EVENT || logEventType == DELETE_ROWS_EVENT_V1
            || logEventType == WRITE_ROWS_EVENT || logEventType == WRITE_ROWS_EVENT_V1
            || logEventType == TABLE_MAP_EVENT;
    }

    public static void main(String[] args) {
        System.out.println(TABLE_ID_MAX_VALUE);
        System.out.println(TABLE_ID_RESET_THRESHOLD);
    }
}
