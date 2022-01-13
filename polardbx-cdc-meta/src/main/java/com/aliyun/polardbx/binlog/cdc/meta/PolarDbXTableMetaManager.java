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
import com.aliyun.polardbx.binlog.canal.ReplicateFilter;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.TableMetaTSDB;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta.FieldMetaExt;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicTableMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Created by Shuguang
 */
@Slf4j
public class PolarDbXTableMetaManager implements TableMetaTSDB {
    private static final Gson GSON = new GsonBuilder().create();
    private AtomicBoolean initialized = new AtomicBoolean(false);

    private PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private PolarDbXStorageTableMeta polarDbXStorageTableMeta;
    private String storageInstId;
    private ReplicateFilter filter;
    private TopologyManager topologyManager;

    private BinlogLogicMetaHistoryMapper binlogLogicMetaHistoryMapper = SpringContextHolder.getObject(
        BinlogLogicMetaHistoryMapper.class);

    public PolarDbXTableMetaManager(String storageInstId, MysqlConnection storageConnection, ReplicateFilter filter) {
        this.storageInstId = storageInstId;
        this.filter = filter;
        this.filter = filter;
    }

    @Override
    public boolean init(String destination) {
        if (initialized.compareAndSet(false, true)) {
            this.topologyManager = new TopologyManager();

            this.polarDbXLogicTableMeta = new PolarDbXLogicTableMeta(this.topologyManager);
            this.polarDbXLogicTableMeta.init(destination);
            this.polarDbXStorageTableMeta = new PolarDbXStorageTableMeta(storageInstId,
                polarDbXLogicTableMeta, this.topologyManager);
            this.polarDbXStorageTableMeta.init(destination);

        }
        return true;
    }

    @Override
    public void destory() {
        this.polarDbXStorageTableMeta.destory();
        this.polarDbXLogicTableMeta.destory();
    }

    @Override
    public TableMeta find(String schema, String table) {
        return polarDbXStorageTableMeta.find(schema, table);
    }

    public TableMeta findLogic(String schema, String table) {
        return polarDbXLogicTableMeta.find(schema, table);
    }

    public LogicTableMeta compare(String schema, String table) {
        TableMeta phy = find(schema, table);
        Preconditions.checkNotNull(phy,
            "phyTable " + schema + "." + table + "'s tableMeta should not be null!");
        LogicDbTopology logicTopology = getLogicSchema(schema, table);

        Preconditions.checkArgument(logicTopology != null && logicTopology.getLogicTableMetas() != null
            && logicTopology.getLogicTableMetas().size() == 1, "can not find logic meta " + logicTopology);

        TableMeta logic = findLogic(logicTopology.getSchema(),
            logicTopology.getLogicTableMetas().get(0).getTableName());
        Preconditions.checkNotNull(logic,
            "phyTable [" + schema + "." + table + "], logic tableMeta[" + logicTopology.getSchema() + "."
                + logicTopology
                .getLogicTableMetas().get(0).getTableName() + "] should not be null!");

        final List<String> columnNames = phy.getFields().stream().map(FieldMeta::getColumnName).collect(
            Collectors.toList());
        LogicTableMeta meta = new LogicTableMeta();
        meta.setLogicSchema(logic.getSchema());
        meta.setLogicTable(logic.getTable());
        meta.setPhySchema(schema);
        meta.setPhyTable(table);
        meta.setCompatible(phy.getFields().size() == logic.getFields().size());
        FieldMeta hiddenPK = null;
        if (!meta.isCompatible()) {
            log.warn("meta is not compatible {} {}", phy, logic);
        }
        int logicIndex = 0;
        for (int i = 0; i < logic.getFields().size(); i++) {
            FieldMeta fieldMeta = logic.getFields().get(i);
            final int x = columnNames.indexOf(fieldMeta.getColumnName());
            if (x != i) {
                meta.setCompatible(false);
            }
            // 隐藏主键忽略掉
            if (SystemDB.isDrdsImplicitId(fieldMeta.getColumnName())) {
                meta.setCompatible(false);
                hiddenPK = fieldMeta;
                continue;
            }
            meta.add(new FieldMetaExt(fieldMeta, logicIndex++, x));
        }
        if (!meta.isCompatible()) {
            log.warn("meta is not compatible {}", meta);
        }
        // 如果有隐藏主键，直接放到最后
        if (hiddenPK != null) {
            final int x = columnNames.indexOf(hiddenPK.getColumnName());
            meta.add(new FieldMetaExt(hiddenPK, logicIndex, x));
        }

        return meta;
    }

    public boolean applyBase(BinlogPosition position, LogicMetaTopology topology) {
        this.polarDbXLogicTableMeta.applyBase(position, topology);
        this.polarDbXStorageTableMeta.applyBase(position);
        return true;
    }

    public boolean applyLogic(BinlogPosition position, DDLRecord record, String extra) {
        if (StringUtils.isNotEmpty(extra)) {
            record.setExtInfo(GSON.fromJson(extra, DDLExtInfo.class));
        }
        this.polarDbXLogicTableMeta.apply(position, record, extra);
        return true;
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        this.polarDbXStorageTableMeta.apply(position, schema, ddl, extra);
        return true;
    }

    @Override
    public boolean rollback(BinlogPosition position) {
        Stopwatch sw = Stopwatch.createStarted();
        polarDbXLogicTableMeta.rollback(position);
        polarDbXStorageTableMeta.rollback(position);
        sw.stop();
        log.warn("Final task rollback to tso:{}, cost {}", position.getRtso(), sw);
        return true;
    }

    @Override
    public Map<String, String> snapshot() {
        log.info("Logic: {}", polarDbXLogicTableMeta.snapshot());
        log.info("Storage: {}", polarDbXStorageTableMeta.snapshot());
        throw new RuntimeException("not support for PolarDbXTableMetaManager");
    }

    //------------------------------------------拓扑相关---------------------------------------

    /**
     * 通过物理库获取逻辑库信息
     */
    public LogicDbTopology getLogicSchema(String phySchema) {
        return topologyManager.getLogicSchema(phySchema);
    }

    /**
     * 通过物理库表获取逻辑库表信息
     */
    public LogicDbTopology getLogicSchema(String phySchema, String phyTable) {
        return topologyManager.getLogicSchema(phySchema, phyTable);
    }

    /**
     * 获取存储实例id下面的所有物理库表信息
     */
    public List<PhyTableTopology> getPhyTables(String storageInstId) {
        return topologyManager.getPhyTables(storageInstId);
    }

    public Pair<LogicDbTopology, LogicTableMetaTopology> getTopology(String logicSchema, String logicTable) {
        return topologyManager.getTopology(logicSchema, logicTable);
    }

    public LogicMetaTopology getTopology() {
        return topologyManager.getTopology();
    }

}
