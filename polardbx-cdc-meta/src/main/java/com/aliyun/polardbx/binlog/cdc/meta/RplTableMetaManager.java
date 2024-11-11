/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.repository.CdcSchemaStoreProvider;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_TABLE_META_EXPIRE_TIME_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_TABLE_META_MAX_CACHE_SIZE;
import static com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo.parseExtInfo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RplTableMetaManager {
    static String DEFAULT_TABLE_NAME = "metadb.binlog_logic_meta_history";

    private final String SELECT_MAX_SNAPSHOT_CTS_SQL;
    private final String SELECT_SNAPSHOT_TOPOLOGY_SQL;
    private final String SELECT_DDL_HISTORY_SQL;

    private final Connection polardbxConnection;
    private final MemoryTableMeta memoryTableMeta;

    public RplTableMetaManager(Connection polardbxConnection) {
        this.polardbxConnection = polardbxConnection;
        this.memoryTableMeta = new MemoryTableMeta(log, CdcSchemaStoreProvider.getInstance(),
            DynamicApplicationConfig.getInt(RPL_TABLE_META_MAX_CACHE_SIZE),
            DynamicApplicationConfig.getInt(RPL_TABLE_META_EXPIRE_TIME_MINUTES), false);
        this.memoryTableMeta.setIgnoreImplicitPrimaryKey(true);

        this.SELECT_MAX_SNAPSHOT_CTS_SQL = "select max(tso) tso from " + DEFAULT_TABLE_NAME + " where "
            + " tso <= '%s' and type = '" + MetaType.SNAPSHOT.getValue() + "'";
        this.SELECT_SNAPSHOT_TOPOLOGY_SQL = "select topology from " + DEFAULT_TABLE_NAME + " where tso = '%s'";
        this.SELECT_DDL_HISTORY_SQL = "select * from " + DEFAULT_TABLE_NAME +
            " where tso > '%s' and tso <= '%s' and type = '" + MetaType.DDL.getValue() + "' and need_apply = 1";
    }

    public TableMeta getTableMeta(String schema, String table) {
        return memoryTableMeta.find(schema, table);
    }

    public void apply(BinlogPosition position, String schema, String ddl) {
        memoryTableMeta.apply(position, schema, ddl, null);
    }

    public void rollback(String cts) {
        if (StringUtils.isBlank(cts)) {
            throw new PolardbxException("rollback cts can not be null or empty : [" + cts + "]");
        }

        log.info("start to rollback table meta to tso : " + cts);
        String maxSnapshotCts = getMaxSnapshotCts(cts);
        applySnapshot(maxSnapshotCts);
        applyHistory(maxSnapshotCts, cts);
    }

    private void applySnapshot(String maxSnapshotCts) {
        log.info("start build basic table meta snapshot with tso :" + maxSnapshotCts);
        LogicMetaTopology topology = getSnapshotTopology(maxSnapshotCts);
        topology.getLogicDbMetas().forEach(s -> {
            String schema = s.getSchema();
            String charset = s.getCharset();
            memoryTableMeta.getRepository().setDefaultSchemaWithCharset(schema, charset);
            s.getLogicTableMetas().forEach(t -> {
                String createSql = t.getCreateSql();
                memoryTableMeta.apply(null, schema, createSql, null);
            });
        });
    }

    @SneakyThrows
    private void applyHistory(String maxSnapshotCts, String cts) {
        try (Statement stmt = polardbxConnection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(String.format(SELECT_DDL_HISTORY_SQL, maxSnapshotCts, cts))) {
                while (rs.next()) {
                    String tso = rs.getString("tso");
                    String dbName = rs.getString("db_name");
                    String ddl = rs.getString("ddl");
                    String ext = rs.getString("ext_info");
                    if (StringUtils.isNotBlank(ext)) {
                        DDLExtInfo ddlExtInfo = parseExtInfo(ext);
                        if (ddlExtInfo != null && StringUtils.isNotBlank(ddlExtInfo.getActualOriginalSql())) {
                            ddl = ddlExtInfo.getActualOriginalSql();
                        }
                    }
                    memoryTableMeta.apply(new BinlogPosition(null, tso), dbName, ddl, null);
                }
            }
        }
    }

    @SneakyThrows
    private String getMaxSnapshotCts(String cts) {
        try (Statement stmt = polardbxConnection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(String.format(SELECT_MAX_SNAPSHOT_CTS_SQL, cts))) {
                if (rs.next()) {
                    return rs.getString(1);
                } else {
                    throw new PolardbxException("can`t find snapshot for cts " + cts);
                }
            }
        }
    }

    @SneakyThrows
    private LogicMetaTopology getSnapshotTopology(String snapshotCts) {
        try (Statement stmt = polardbxConnection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(String.format(SELECT_SNAPSHOT_TOPOLOGY_SQL, snapshotCts))) {
                if (rs.next()) {
                    return JSONObject.parseObject(rs.getString(1), LogicMetaTopology.class);
                } else {
                    throw new PolardbxException("can`t find topology for cts " + snapshotCts);
                }
            }
        }
    }
}
