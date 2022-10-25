/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;

import java.util.Map;
import java.util.Set;

public interface ITableMetaDelegate {

    TableMeta find(String phyDbName, String phyTableName);

    void apply(BinlogPosition position, String schema, String ddl, String extra, RuntimeContext rc);

    void applyLogic(BinlogPosition position, DDLRecord record, String ext, RuntimeContext rc);

    void addTableChangeListener(TableMetaChangeListener listener);

    void destroy();

    TableMeta findLogic(String phySchema, String phyTable);

    Map<String, Set<String>> buildTableFilter(String storageInstanceId);

    /**
     * 通过物理库表获取逻辑库表信息
     */
    LogicMetaTopology.LogicDbTopology getLogicSchema(String phySchema, String phyTable);

    LogicTableMeta compare(String schema, String table, int columnCount);

    Set<String> findIndexes(String schema, String table);
}
