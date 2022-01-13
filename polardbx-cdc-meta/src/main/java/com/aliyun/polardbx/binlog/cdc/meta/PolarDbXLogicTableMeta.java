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

package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.CommonUtils.escape;

/**
 * Created by Shuguang
 */
public class PolarDbXLogicTableMeta extends MemoryTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(PolarDbXLogicTableMeta.class);
    private static final byte TYPE_SNAPSHOT = 1;
    private static final byte TYPE_DDL = 2;
    private static final int SIZE = 100;
    private static final Gson GSON = new GsonBuilder().create();
    private final BinlogLogicMetaHistoryMapper binlogLogicMetaHistoryMapper = SpringContextHolder.getObject(
        BinlogLogicMetaHistoryMapper.class);

    private AtomicBoolean initialized = new AtomicBoolean(false);
    private String destination;
    private TopologyManager topologyManager;

    private MemoryTableMeta phyMeta = new MemoryTableMeta(logger);

    public PolarDbXLogicTableMeta(TopologyManager topologyManager) {
        super(logger);
        this.topologyManager = topologyManager;
    }

    @Override
    public boolean init(final String destination) {
        if (initialized.compareAndSet(false, true)) {
            this.destination = destination;
        }
        return true;
    }

    public boolean applyBase(BinlogPosition position, LogicMetaTopology topology) {
        topology.getLogicDbMetas().forEach(s -> {
            String schema = s.getSchema();
            s.getLogicTableMetas().forEach(t -> {
                String createSql = t.getCreateSql();
                apply(position, schema, createSql, null);
                if (StringUtils.isNotEmpty(t.getCreateSql4Phy())) {
                    phyMeta.apply(position, schema, t.getCreateSql4Phy(), null);
                }
            });
        });
        topologyManager.setTopology(topology);
        DDLRecord record = DDLRecord.builder().schemaName("*").ddlSql(GSON.toJson(snapshot())).metaInfo(
            GSON.toJson(topology)).build();
        applySnapshotToDB(position, record, TYPE_SNAPSHOT, null);
        return true;
    }

    public boolean apply(BinlogPosition position, DDLRecord record, String extra) {
        //apply meta
        if (checkBeforeApply(position.getRtso(), record.getSchemaName(), record.getTableName(), record.getDdlSql())) {
            apply(position, record.getSchemaName(), record.getDdlSql(), extra);
            if (record.getExtInfo() != null && StringUtils.isNotEmpty(record.getExtInfo().getCreateSql4PhyTable())) {
                phyMeta.apply(position, record.getSchemaName(), record.getExtInfo().getCreateSql4PhyTable(), extra);
            }
            //apply topology
            TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);

            fixOrDropPhyMeta(position.getRtso(), record.getSchemaName(), record.getTableName(), record.getSqlKind(),
                record.getDdlSql());
            dropTopology(position.getRtso(), record.getSchemaName(), record.getTableName(), record.getDdlSql());

            topologyManager.apply(position.getRtso(), record.getSchemaName(), record.getTableName(), r);
        }

        // store with db
        applySnapshotToDB(position, record, TYPE_DDL, extra);
        return true;
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        // 每次rollback需要重新构建一次memory data
        destory();
        //获取快照tso
        BinlogPosition snapshotPosition = getSnapshotPosition(position);
        if (snapshotPosition.getRtso() != null) {
            //apply snapshot
            applySnapshot(snapshotPosition.getRtso());
            //重放ddl和topology
            applyHistory(snapshotPosition.getRtso(), position.getRtso());
        }
        return true;
    }

    private void applySnapshot(String snapshotTso) {
        Optional<BinlogLogicMetaHistory> snapshot = binlogLogicMetaHistoryMapper.selectOne(s -> s
            .where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isEqualTo(snapshotTso))
        );

        snapshot.ifPresent(s -> {
            logger.warn("apply logic snapshot: [id={}, dbName={}, tso={}]", s.getId(), s.getDbName(), s.getTso());
            LogicMetaTopology topology = GSON.fromJson(s.getTopology(), LogicMetaTopology.class);
            topology.getLogicDbMetas().forEach(x -> {
                String schema = x.getSchema();
                x.getLogicTableMetas().forEach(t -> {
                    String createSql = t.getCreateSql();
                    apply(null, schema, createSql, null);
                    if (StringUtils.isNotEmpty(t.getCreateSql4Phy())) {
                        phyMeta.apply(null, schema, t.getCreateSql4Phy(), null);
                    }
                });
            });
            topologyManager.setTopology(topology);
        });
    }

    private void applyHistory(String snapshotTso, String rollbackTso) {
        BinlogPosition position = new BinlogPosition(null, snapshotTso);
        while (true) {
            List<BinlogLogicMetaHistory> histories = binlogLogicMetaHistoryMapper.select(s -> s
                .where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isGreaterThan(position.getRtso()))
                .and(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThanOrEqualTo(rollbackTso))
                .orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso).limit(SIZE)
            );
            histories.forEach(h -> {
                if (checkBeforeApply(position.getRtso(), h.getDbName(), h.getTableName(), h.getDdl())) {
                    super.apply(null, h.getDbName(), h.getDdl(), null);
                    if (StringUtils.isNotEmpty(h.getExtInfo())) {
                        DDLExtInfo extInfo = GSON.fromJson(h.getExtInfo(), DDLExtInfo.class);
                        if (extInfo != null && StringUtils.isNotEmpty(extInfo.getCreateSql4PhyTable())) {
                            phyMeta.apply(null, h.getDbName(), extInfo.getCreateSql4PhyTable(), null);
                        }
                    }
                    if (StringUtils.isNotEmpty(h.getTopology())) {
                        TopologyRecord topologyRecord = GSON.fromJson(h.getTopology(), TopologyRecord.class);
                        logger
                            .warn("apply logic ddl: [id={}, dbName={}, tso={}]", h.getId(), h.getDbName(), h.getTso());
                        topologyManager.apply(h.getTso(), h.getDbName(), h.getTableName(), topologyRecord);
                    }
                    fixOrDropPhyMeta(h.getTso(), h.getDbName(), h.getTableName(), h.getSqlKind(), h.getDdl());
                    dropTopology(h.getTso(), h.getDbName(), h.getTableName(), h.getDdl());
                }
            });
            if (histories.size() == SIZE) {
                position.setRtso(histories.get(SIZE - 1).getTso());
            } else {
                break;
            }
        }
    }

    /**
     * 快照备份到存储, 这里只需要备份变动的table
     */
    private boolean applySnapshotToDB(BinlogPosition position, DDLRecord record, byte type, String extra) {
        if (position == null) {
            return false;
        }
        try {
            BinlogLogicMetaHistory history = BinlogLogicMetaHistory.builder()
                .tso(position.getRtso())
                .dbName(record.getSchemaName())
                .tableName(record.getTableName())
                .sqlKind(record.getSqlKind())
                .ddl(record.getDdlSql())
                .topology(record.getMetaInfo()).type(type)
                .extInfo(record.getExtInfo() != null ? GSON.toJson(record.getExtInfo()) : null)
                .build();
            binlogLogicMetaHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            logger.warn("ddl record already applied, ignore this time, record info is " + record);
        }
        return true;
    }

    /**
     * 从存储从获取快照位点
     */
    protected BinlogPosition getSnapshotPosition(BinlogPosition position) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        String tso = metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + position.getRtso() + "' and type = "
                + TYPE_SNAPSHOT,
            String.class);
        return new BinlogPosition(null, tso);
    }

    public Map<String, String> phySnapshot() {
        Collection<Schema> schemas = phyMeta.getRepository().getSchemas();
        schemas.forEach(schema -> {
            logger.warn("to be replaced phySchema:{}, tables:{}", schema.getCatalog(), schema.showTables());
        });
        return phyMeta.snapshot();
    }

    private void dropTopology(String tso, String schema, String tableName, String ddl) {
        SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql,
            FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);
        if (stmt instanceof SQLDropDatabaseStatement) {
            String databaseName = ((SQLDropDatabaseStatement) stmt).getDatabaseName();
            databaseName = SQLUtils.normalize(databaseName);
            Preconditions.checkArgument(StringUtils.equalsIgnoreCase(databaseName, schema),
                "drop database record should be coincident DDL(" + databaseName + "), History(" + schema + ")");
            topologyManager.removeTopology(tso, schema.toLowerCase(), null);
        }
        if (stmt instanceof SQLDropTableStatement) {
            for (SQLExprTableSource tableSource : ((SQLDropTableStatement) stmt).getTableSources()) {
                String tn = tableSource.getTableName(true);
                Preconditions.checkArgument(StringUtils.equalsIgnoreCase(tn, tableName),
                    "drop table record should be coincident DDL(" + tn + "), History(" + tableName + ")");
                topologyManager.removeTopology(tso, schema.toLowerCase(), tableName.toLowerCase());
            }
        }
    }

    private boolean checkBeforeApply(String tso, String schema, String tableName, String ddl) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        // 如果是create database sql，做一下double check，将元数据尝试进行一下清理
        // 正常不应该有元数据的，但是不排除意外情况，比如：polarx内核针对drop database未接入ddl引擎，sql执行和cdc打标无法保证原子性
        if (stmt instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) stmt;
            // 对于含有if not exist的sql来说，无法判断当前create database操作是否是有效操作，所以不予处理
            if (!createDatabaseStatement.isIfNotExists()) {
                String databaseName = createDatabaseStatement.getDatabaseName();
                databaseName = SQLUtils.normalize(databaseName);
                Preconditions.checkArgument(StringUtils.equalsIgnoreCase(databaseName, schema),
                    "create database record should be coincident DDL(" + databaseName + "), History(" + schema + ")");
                super.apply(null, schema, "drop database if exists `" + escape(databaseName) + "`", null);
                topologyManager.removeTopology(tso, schema.toLowerCase(), null);
            }
        }

        if (stmt instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) stmt;
            return !createTableStatement.isIfNotExists() || find(schema, tableName) == null;
        }

        return true;
    }

    private void fixOrDropPhyMeta(String tso, String schema, String tableName, String sqlKind, String ddlSql) {
        if (phyMeta.find(schema, tableName) != null) {
            if (StringUtils.equals(sqlKind, "DROP_DATABASE") || StringUtils.equals(sqlKind, "DROP_TABLE")
                || StringUtils.equals(sqlKind, "RENAME_TABLE")) {
                phyMeta.apply(new BinlogPosition(null, tso), schema, ddlSql, null);
            }
        }
    }
}
