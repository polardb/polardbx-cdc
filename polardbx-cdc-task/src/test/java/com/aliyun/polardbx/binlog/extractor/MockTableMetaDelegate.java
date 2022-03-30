/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ITableMetaDelegate;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMetaChangeListener;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockTableMetaDelegate implements ITableMetaDelegate {

    private TableMeta tableMeta;
    private String dbName;

    public MockTableMetaDelegate(TableMeta tableMeta, String dbName) {
        this.tableMeta = tableMeta;
        this.dbName = dbName;
    }

    @Override
    public TableMeta find(String phyDbName, String phyTableName) {
        return tableMeta;
    }

    @Override
    public void apply(BinlogPosition position, String schema, String ddl, String extra, RuntimeContext rc) {

    }

    @Override
    public void applyLogic(BinlogPosition position, DDLRecord record, String ext, RuntimeContext rc) {

    }

    @Override
    public void addTableChangeListener(TableMetaChangeListener listener) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public TableMeta findLogic(String phySchema, String phyTable) {
        return tableMeta;
    }

    @Override
    public Map<String, Set<String>> buildTableFilter(String storageInstanceId) {
        Map<String, Set<String>> filter = new HashMap<>();
        Set<String> tableSet = new HashSet<>();
        tableSet.add(tableMeta.getTable());
        filter.put(dbName, tableSet);
        return filter;
    }

    @Override
    public LogicMetaTopology.LogicDbTopology getLogicSchema(String phySchema, String phyTable) {
        return null;
    }

    @Override
    public LogicTableMeta compare(String schema, String table) {
        LogicTableMeta logicTableMeta = new LogicTableMeta();
        List<TableMeta.FieldMeta> fieldMetaList = tableMeta.getFields();
        for (int i = 0; i < fieldMetaList.size(); i++) {
            TableMeta.FieldMeta fieldMeta = fieldMetaList.get(i);
            LogicTableMeta.FieldMetaExt fieldMetaExt = new LogicTableMeta.FieldMetaExt(fieldMeta, i, i);
            logicTableMeta.add(fieldMetaExt);
        }
        logicTableMeta.setCompatible(true);
        logicTableMeta.setLogicSchema(schema);
        logicTableMeta.setLogicTable(table);
        logicTableMeta.setPhySchema(schema);
        logicTableMeta.setPhyTable(table);
        return logicTableMeta;
    }

    @Override
    public Set<String> findIndexes(String schema, String table) {
        return Sets.newHashSet();
    }
}
