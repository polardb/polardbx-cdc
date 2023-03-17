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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_PRINT_DETAIL_AFTER_APPLY_SWITCH;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PRINT_DETAIL_AFTER_APPLY_TABLES;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PRINT_SUMMARY_AFTER_APPLY_SWITCH;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PRINT_SUMMARY_AFTER_APPLY_TABLES;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class Printer {
    private final static boolean IS_PRINT_SUMMARY =
        DynamicApplicationConfig.getBoolean(META_PRINT_SUMMARY_AFTER_APPLY_SWITCH);

    private final static boolean IS_PRINT_DETAIL =
        DynamicApplicationConfig.getBoolean(META_PRINT_DETAIL_AFTER_APPLY_SWITCH);

    private final static String SUMMARY_TABLES_PATTERN_STR =
        DynamicApplicationConfig.getString(META_PRINT_SUMMARY_AFTER_APPLY_TABLES);

    private final static String DETAIL_TABLES_PATTERN_STR =
        DynamicApplicationConfig.getString(META_PRINT_DETAIL_AFTER_APPLY_TABLES);

    private final static Pattern SUMMARY_PATTERN = Pattern.compile(SUMMARY_TABLES_PATTERN_STR);

    private final static Pattern DETAIL_PATTERN = Pattern.compile(DETAIL_TABLES_PATTERN_STR);

    public static boolean isSupportPrint() {
        return IS_PRINT_SUMMARY || IS_PRINT_DETAIL;
    }

    public static void tryPrint(BinlogPosition position, String schema, String table,
                                MemoryTableMeta memoryTableMeta) {
        if (IS_PRINT_SUMMARY) {
            printSummary(position, schema, table, memoryTableMeta);
        }
        if (IS_PRINT_DETAIL) {
            printDetail(position, schema, table, memoryTableMeta);
        }
    }

    private static void printSummary(BinlogPosition position, String schema, String table,
                                     MemoryTableMeta memoryTableMeta) {
        String fullTable = schema + "." + table;
        Matcher matcher = SUMMARY_PATTERN.matcher(fullTable);
        if (matcher.find()) {
            TableMeta tableMeta = memoryTableMeta.find(schema, table);
            log.info("print summary : tso {}, fileName {}, filePosition {}, schema {}, table {}, tableMetaNullable {} ",
                position.getRtso(), position.getFileName(), position.getPosition(), schema, table, tableMeta == null);

        }
    }

    private static void printDetail(BinlogPosition position, String schema, String table,
                                    MemoryTableMeta memoryTableMeta) {
        String fullTable = schema + "." + table;
        Matcher matcher = DETAIL_PATTERN.matcher(fullTable);
        if (matcher.find()) {
            TableMeta tableMeta = memoryTableMeta.find(schema, table);
            log.info("print summary : tso {}, fileName {}, filePosition {}, schema {}, table {}, tableMetaNullable {},"
                    + " tableMetaInfo {}", position.getRtso(), position.getFileName(), position.getPosition(), schema,
                table, tableMeta == null, tableMeta);
        }
    }
}
