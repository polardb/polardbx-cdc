/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import lombok.extern.slf4j.Slf4j;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_LOG_TABLE_META_DETAIL_ENABLED;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class Printer {
    private static final boolean IS_PRINT_DETAIL =
        DynamicApplicationConfig.getBoolean(META_BUILD_LOG_TABLE_META_DETAIL_ENABLED);

    public static boolean isSupportPrint() {
        return IS_PRINT_DETAIL;
    }

    public static void tryPrint(BinlogPosition position, String schema, String table,
                                MemoryTableMeta memoryTableMeta) {
        if (IS_PRINT_DETAIL) {
            printDetail(position, schema, table, memoryTableMeta);
        }
    }

    private static void printDetail(BinlogPosition position, String schema, String table,
                                    MemoryTableMeta memoryTableMeta) {
        TableMeta tableMeta = memoryTableMeta.find(schema, table);
        log.info("print summary : tso {}, fileName {}, filePosition {}, schema {}, table {}, tableMetaInfo {}",
            position.getRtso(), position.getFileName(), position.getPosition(), schema, table, tableMeta);
    }
}
