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

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.google.common.base.Preconditions;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by ziyang.lb
 */
public class PolarDbXStorageTableMeta extends MemoryTableMeta {
    private static Logger logger = LoggerFactory.getLogger(PolarDbXStorageTableMeta.class);
    private static final int SIZE = 200;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private String destination;
    private String storageInstId;

    private BinlogPhyDdlHistoryMapper binlogPhyDdlHistoryMapper = SpringContextHolder.getObject(
        BinlogPhyDdlHistoryMapper.class);

    private PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private TopologyManager topologyManager;

    public PolarDbXStorageTableMeta(String storageInstId,
                                    PolarDbXLogicTableMeta polarDbXLogicTableMeta, TopologyManager topologyManager) {
        super(logger);
        this.storageInstId = storageInstId;
        this.polarDbXLogicTableMeta = polarDbXLogicTableMeta;
        this.topologyManager = topologyManager;
    }

    @Override
    public boolean init(final String destination) {
        if (initialized.compareAndSet(false, true)) {
            this.destination = destination;
        }
        return true;
    }

    public boolean applyBase(BinlogPosition position) {
        // 每次rollback需要重新构建一次memory data
        final Map<String, String> snapshot = polarDbXLogicTableMeta.snapshot();
        snapshot.forEach((k, v) -> super.apply(position, k, v, null));

        // 根据逻辑MetaSnapshot构建物理
        List<PhyTableTopology> phyTables = topologyManager.getPhyTables(storageInstId);

        for (PhyTableTopology phyTable : phyTables) {
            final List<String> tables = phyTable.getPhyTables();
            if (tables != null) {
                for (String table : tables) {
                    LogicDbTopology logicSchema = topologyManager.getLogicSchema(phyTable.getSchema(), table);
                    Preconditions.checkNotNull(logicSchema,
                        "phyTable " + phyTable.getSchema() + "." + table + "'s logicSchema should not be null!");
                    Preconditions.checkNotNull(logicSchema.getLogicTableMetas(),
                        "phyTable " + phyTable.getSchema() + "." + table + "'s logicTables should not be null!");
                    Preconditions.checkNotNull(logicSchema.getLogicTableMetas().get(0),
                        "phyTable " + phyTable.getSchema() + "." + table + "'s logicTable should not be null!");
                    String tableName = logicSchema.getLogicTableMetas().get(0).getTableName();
                    String createTable = "create table `" + table + "` like `" + logicSchema.getSchema() + "`.`"
                        + tableName
                        + "`";
                    logger.warn("apply from logic table phy:{}.{}, logic:{}.{} [{}] ...", phyTable.getSchema(), table,
                        logicSchema.getSchema(), tableName, createTable);
                    super.apply(null, phyTable.getSchema(), createTable, null);
                }
            }
        }
        //drop 逻辑库
        snapshot.forEach((k, v) -> {
            super.apply(position, k, "drop database `" + k + "`", null);
        });
        return true;
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        // 首先记录到内存结构
        lock.writeLock().lock();
        try {
            if (super.apply(position, schema, ddl, extra)) {
                // 同步每次变更给远程做历史记录，只记录ddl，不记录快照
                applyHistoryToDB(position, schema, ddl, extra);
                return true;
            } else {
                throw new RuntimeException("apply to memory is failed");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        // 每次rollback需要重新构建一次memory data
        applyBase(position);

        BinlogPosition snapshotPosition = getSnapshotPosition(position);
        //2do 根据MetaHistory重放ddl
        if (position != null && snapshotPosition != null && snapshotPosition.getRtso() != null) {
            while (true) {
                List<BinlogPhyDdlHistory> ddlHistories = binlogPhyDdlHistoryMapper.select(
                    s -> s
                        .where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                        .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso,
                            SqlBuilder.isGreaterThan(snapshotPosition.getRtso()))
                        .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso,
                            SqlBuilder.isLessThanOrEqualTo(position.getRtso()))
                        .orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.tso).limit(SIZE)
                );
                for (BinlogPhyDdlHistory ddlHistory : ddlHistories) {
                    logger.warn("apply phy ddl: [id={}, dbName={}, tso={}]", ddlHistory.getId(), ddlHistory.getDbName(),
                        ddlHistory.getTso());
                    super.apply(position, ddlHistory.getDbName(), ddlHistory.getDdl(), ddlHistory.getExtra());
                }
                if (ddlHistories.size() == SIZE) {
                    snapshotPosition.setRtso(ddlHistories.get(SIZE - 1).getTso());
                } else {
                    break;
                }
            }
        }
        return true;
    }

    private void applyHistoryToDB(BinlogPosition position, String schema, String ddl, String extra) {
        try {
            binlogPhyDdlHistoryMapper.insert(BinlogPhyDdlHistory.builder().storageInstId(storageInstId).binlogFile(
                position.getFileName()).tso(
                position.getRtso()).dbName(schema)
                .ddl(ddl).extra(extra).build());
        } catch (DuplicateKeyException e) {
            logger.warn(
                "already applyHistoryToDB, ignore this time, position is : {}, schema is {}, ddl is {}, extra is {}",
                position, schema, ddl, extra);
        }
    }

    /**
     * 从存储从获取快照位点，用于补齐逻辑和物理之间的差距
     */
    protected BinlogPosition getSnapshotPosition(BinlogPosition position) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        String tso = metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + position.getRtso() + "'",
            String.class);
        return new BinlogPosition(null, tso);
    }
}
