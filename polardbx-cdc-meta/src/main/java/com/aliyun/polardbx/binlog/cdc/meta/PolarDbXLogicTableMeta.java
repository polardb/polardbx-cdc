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
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
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
        apply(position, record.getSchemaName(), record.getDdlSql(), extra);
        //apply topology
        TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);

        topologyManager.apply(record.getSchemaName(), record.getTableName(), r);
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
            //Map<String, String> schemas = JSON.parseObject(s.getDdl(), Map.class);
            //schemas.forEach((k, v) -> super.apply(null, k, v, null));
            LogicMetaTopology topology = GSON.fromJson(s.getTopology(), LogicMetaTopology.class);
            topology.getLogicDbMetas().forEach(x -> {
                String schema = x.getSchema();
                x.getLogicTableMetas().forEach(t -> {
                    String createSql = t.getCreateSql();
                    apply(null, schema, createSql, null);
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
                super.apply(null, h.getDbName(), h.getDdl(), null);
                if (StringUtils.isNotEmpty(h.getTopology())) {
                    TopologyRecord topologyRecord = GSON.fromJson(h.getTopology(), TopologyRecord.class);

                    logger.warn("apply logic ddl: [id={}, dbName={}, tso={}]", h.getId(), h.getDbName(), h.getTso());
                    topologyManager.apply(h.getDbName(), h.getTableName(), topologyRecord);
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
                .ddl(record.getDdlSql())
                .topology(record.getMetaInfo()).type(type)
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
}
