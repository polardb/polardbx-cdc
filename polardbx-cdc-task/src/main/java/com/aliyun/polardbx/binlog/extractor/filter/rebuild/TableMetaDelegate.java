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

package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class TableMetaDelegate implements ITableMetaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(TableMetaDelegate.class);
    private final PolarDbXTableMetaManager target;
    private final List<TableMetaChangeListener> changeListenerList = new CopyOnWriteArrayList<>();
    private String lastApplyLogicTSO;

    public TableMetaDelegate(PolarDbXTableMetaManager target) {
        this.target = target;
    }

    @Override
    public TableMeta find(String phyDbName, String phyTableName) {
        return target.findPhyTable(phyDbName, phyTableName);
    }

    @Override
    public void apply(BinlogPosition position, String schema, String ddl, String extra, RuntimeContext rc) {
        target.applyPhysical(position, schema, ddl, extra);
        fireEvent(schema, ddl, true, false, rc);
    }

    @Override
    public void applyLogic(BinlogPosition position, DDLRecord record, String ext, RuntimeContext rc) {
        if (!StringUtils.isBlank(lastApplyLogicTSO) && lastApplyLogicTSO.compareTo(position.getRtso()) >= 0) {
            logger.warn("ignore duplicate apply logic ddl " + record.getDdlSql() + " tso : " + position.getRtso());
            return;
        }
        target.applyLogic(position, record, ext);
        fireEvent(record.getSchemaName(), record.getDdlSql(), false, StringUtils.isNotBlank(record.getMetaInfo()), rc);
        lastApplyLogicTSO = position.getRtso();
    }

    private void fireEvent(String schema, String ddl, boolean isPhy, boolean isTopoChange, RuntimeContext rc) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> stmtList = parser.parseStatementList();

        for (SQLStatement stmt : stmtList) {
            if (stmt instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement renameStmt = (MySqlRenameTableStatement) stmt;
                for (MySqlRenameTableStatement.Item item : renameStmt.getItems()) {
                    TableMetaChangeEvent event = new TableMetaChangeEvent();
                    event.setBiTable(item.getName().getSimpleName());
                    event.setSchema(schema);
                    event.setAiTable(item.getTo().getSimpleName());
                    event.setChangeAction(TableMetaChangeAction.RENAME_TABLE);
                    event.setPhyChange(isPhy);
                    event.setRc(rc);
                    event.setTopologyChange(isTopoChange);
                    onEvent(event);
                }
            } else if (stmt instanceof SQLDropTableStatement) {
                SQLDropTableStatement dropTableStatement = (SQLDropTableStatement) stmt;
                List<SQLExprTableSource> tableSources = dropTableStatement.getTableSources();
                for (SQLExprTableSource source : tableSources) {
                    TableMetaChangeEvent event = new TableMetaChangeEvent();
                    event.setBiTable(source.getTableName());
                    event.setSchema(schema);
                    event.setChangeAction(TableMetaChangeAction.DROP_TABLE);
                    event.setPhyChange(isPhy);
                    event.setRc(rc);
                    event.setTopologyChange(isTopoChange);
                    onEvent(event);
                }

            } else if (stmt instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement createTableStatement = (SQLCreateTableStatement) stmt;
                TableMetaChangeEvent event = new TableMetaChangeEvent();
                event.setAiTable(createTableStatement.getTableName());
                event.setSchema(schema);
                event.setChangeAction(TableMetaChangeAction.CREATE_TABLE);
                event.setPhyChange(isPhy);
                event.setRc(rc);
                event.setTopologyChange(isTopoChange);
                onEvent(event);

            } else if (stmt instanceof SQLCreateDatabaseStatement) {
                SQLCreateDatabaseStatement statement = (SQLCreateDatabaseStatement) stmt;
                TableMetaChangeEvent event = new TableMetaChangeEvent();
                event.setSchema(statement.getDatabaseName());
                event.setChangeAction(TableMetaChangeAction.CREATE_DATABASE);
                event.setPhyChange(isPhy);
                event.setRc(rc);
                event.setTopologyChange(isTopoChange);
                onEvent(event);
            } else if (stmt instanceof SQLDropDatabaseStatement) {
                SQLDropDatabaseStatement dropDatabaseStatement = (SQLDropDatabaseStatement) stmt;
                TableMetaChangeEvent event = new TableMetaChangeEvent();
                event.setSchema(dropDatabaseStatement.getDatabaseName());
                event.setChangeAction(TableMetaChangeAction.DROP_DATABASE);
                event.setPhyChange(isPhy);
                event.setRc(rc);
                event.setTopologyChange(isTopoChange);
                onEvent(event);
            } else {
                TableMetaChangeEvent event = new TableMetaChangeEvent();
                event.setTopologyChange(isTopoChange);
                onEvent(event);
            }
        }

    }

    private void onEvent(TableMetaChangeEvent event) {
        changeListenerList.forEach(l -> {
            l.onTableChange(event);
        });
    }

    @Override
    public void addTableChangeListener(TableMetaChangeListener listener) {
        changeListenerList.add(listener);
    }

    @Override
    public void destroy() {
        target.destroy();
    }

    @Override
    public TableMeta findLogic(String phySchema, String phyTable) {
        LogicMetaTopology.LogicDbTopology logicTopology = target.getLogicSchema(phySchema, phyTable);
        return target
            .findLogicTable(logicTopology.getSchema(), logicTopology.getLogicTableMetas().get(0).getTableName());
    }

    @Override
    public Map<String, Set<String>> buildTableFilter(String storageInstanceId) {
        List<LogicMetaTopology.PhyTableTopology> phyTableTopologyList = target.getPhyTables(storageInstanceId);
        Map<String, Set<String>> tmpFilter = new HashMap<>();
        phyTableTopologyList.forEach(s -> {
            final Set<String> phyTables = s.getPhyTables()
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            final String schema = s.getSchema().toLowerCase();
            Set<String> tmpTablesSet = tmpFilter.computeIfAbsent(schema, k -> new HashSet<>());
            tmpTablesSet.addAll(phyTables);
        });

        LogicMetaTopology topology = target.getTopology();
        List<LogicMetaTopology.LogicDbTopology> logicDbTopologyList = topology.getLogicDbMetas();
        logicDbTopologyList.forEach(t -> {
            t.getPhySchemas().forEach(phyDbTopology -> {
                if (phyDbTopology.getStorageInstId().equalsIgnoreCase(storageInstanceId)) {
                    String phySchema = phyDbTopology.getSchema().toLowerCase();
                    if (!tmpFilter.containsKey(phySchema)) {
                        tmpFilter.put(phySchema, new HashSet<>());
                    }
                }
            });
        });
        return tmpFilter;
    }

    /**
     * 获取存储实例id下面的所有物理库表信息
     */
    public List<LogicMetaTopology.PhyTableTopology> getPhyTables(String storageInstId) {
        return target.getPhyTables(storageInstId);
    }

    public Pair<LogicMetaTopology.LogicDbTopology, LogicMetaTopology.LogicTableMetaTopology> getTopology(
        String logicSchema, String logicTable) {
        return target.getTopology(logicSchema, logicTable);
    }

    public LogicMetaTopology getTopology() {
        return target.getTopology();
    }

    /**
     * 通过物理库表获取逻辑库表信息
     */
    @Override
    public LogicMetaTopology.LogicDbTopology getLogicSchema(String phySchema, String phyTable) {
        return target.getLogicSchema(phySchema, phyTable);
    }

    @Override
    public LogicTableMeta compare(String schema, String table) {
        return target.compare(schema, table);
    }

    @Override
    public Set<String> findIndexes(String schema, String table) {
        return target.findIndexes(schema, table);
    }
}
