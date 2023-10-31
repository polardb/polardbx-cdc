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
