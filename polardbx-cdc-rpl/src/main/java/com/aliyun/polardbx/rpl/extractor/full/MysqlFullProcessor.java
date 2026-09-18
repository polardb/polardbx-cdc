/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.full;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.JdbcParameterBinder;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author shicai.xsc
 * 联合主键全量抽取 + 多列 checkpoint 实现
 */
@Slf4j
@Data
public class MysqlFullProcessor {

    private DataSource dataSource;
    private String schema;
    private String tbName;
    private String fullTableName;
    private String logicalSchema;
    private String logicalTbName;
    private TableInfo tableInfo;
    private FullExtractorConfig extractorConfig;
    private HostInfo hostInfo;
    private BasePipeline pipeline;

    private boolean useRdsImplicitId;

    /**
     * 主键列信息（根据 TableInfo.getPks 顺序）
     */
    private List<ColumnInfo> primaryKeyColumns;

    /**
     * 当前 checkpoint 的主键值列表（与 primaryKeyColumns 一一对应）。
     * 第一次抽取时，如果 position 为空则为 null。
     */
    private List<Object> startPkValues;

    public static synchronized void initDbFullPosition(String fullTableName, long totalCount,
                                                       String endPosition) {
        DbTaskMetaManager.addDbFullPosition(TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance()
            .getServiceId(), TaskContext.getInstance().getTaskId(), fullTableName, totalCount, endPosition);
    }

    public static synchronized void updateDbFullPosition(String fullTableName, long incFinishedCount,
                                                         String position, int finished) {
        RplDbFullPosition record =
            DbTaskMetaManager.getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
        RplDbFullPosition newRecord = new RplDbFullPosition();
        newRecord.setId(record.getId());
        newRecord.setFinishedCount(record.getFinishedCount() + incFinishedCount);
        newRecord.setPosition(position);
        newRecord.setFinished(finished);
        DbTaskMetaManager.updateDbFullPosition(newRecord);
    }

    public void preStart() {
        fullTableName = schema + "." + tbName;
        initFullPositionIfNotExist();
    }

    public void start() {
        try {
            RplDbFullPosition fullPosition =
                DbTaskMetaManager.getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
            if (fullPosition.getFinished() == RplConstants.FINISH) {
                log.info("full copy done, position is finished. schema:{}, tbName:{}", schema, tbName);
                return;
            }

            tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, hostInfo.getType());

            initPrimaryKeyColumns();

            if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
                log.warn("no primary key for {}.{}, full copy will scan without checkpoint", schema, tbName);
            }

            // 初始化起始主键值（从 position 解析）
            String positionStr = fullPosition.getPosition();
            if (StringUtils.isBlank(positionStr)) {
                startPkValues = null; // 从头开始
            } else {
                startPkValues = parsePkValuesFromPosition(positionStr);
            }

            fetchData();
        } catch (Exception e) {
            log.error("failed to start full processor, schema:{}, tbName:{}", schema, tbName, e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_FULL_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            StatisticalProxy.getInstance().recordLastError(e.toString());
            TaskContext.getInstance().getPipeline().stop();
        }
    }

    private void initPrimaryKeyColumns() {
        primaryKeyColumns = new ArrayList<>();
        useRdsImplicitId = false;

        if (tableInfo.getPks() != null && !tableInfo.getPks().isEmpty()) {
            for (String pkName : tableInfo.getPks()) {
                ColumnInfo ci = tableInfo.getColumns().stream()
                    .filter(c -> c.getName().equals(pkName.toLowerCase()))
                    .findFirst().orElse(null);
                if (ci != null) {
                    primaryKeyColumns.add(ci);
                } else {
                    log.warn("pk column {} not found in columns for table {}.{}", pkName, schema, tbName);
                }
            }
        } else if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FULL_USE_IMPLICIT_ID)) {
            // 无主键表但开启隐藏主键
            if (checkImplicitIdExist()) {
                ColumnInfo ci = new ColumnInfo(RplConstants.RDS_IMPLICIT_ID, Types.BIGINT, null,
                    false, false, null, 0);
                primaryKeyColumns.add(ci);
                useRdsImplicitId = true;
            } else {
                log.warn("no pk and no implicit id for {}.{}, checkpoint is not available", schema, tbName);
            }
        }
    }

    private void initFullPositionIfNotExist() {
        try {
            RplDbFullPosition record =
                DbTaskMetaManager.getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
            if (record != null) {
                log.info("full position for: {} already exist, totalCount:{}", fullTableName, record.getTotalCount());
                return;
            }
            long totalCount = getTotalCount();
            initDbFullPosition(fullTableName, totalCount, null);
            StatisticalProxy.getInstance().heartbeat();
            log.info("init full position for: {}, totalCount:{}", fullTableName, totalCount);
        } catch (SQLException e) {
            log.error("failed to init full position for: {} because of : ", fullTableName, e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_FULL_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            StatisticalProxy.getInstance().recordLastError(e.toString());
            TaskContext.getInstance().getPipeline().stop();
        }
    }

    /**
     * 构造 SELECT SQL（带联合主键 where 和 order by）
     */
    private String getFetchSql() {
        StringBuilder nameSqlSb = new StringBuilder();
        Iterator<ColumnInfo> it = tableInfo.getColumns().iterator();
        while (it.hasNext()) {
            ColumnInfo column = it.next();
            nameSqlSb.append("`");
            nameSqlSb.append(column.getName());
            nameSqlSb.append("`");
            if (it.hasNext()) {
                nameSqlSb.append(",");
            }
        }
        if (useRdsImplicitId) {
            nameSqlSb = new StringBuilder("`" + RplConstants.RDS_IMPLICIT_ID + "`," + nameSqlSb);
        }

        String orderBy = buildOrderByClause();

        // 没主键：不做 checkpoint，直接全表扫
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return String.format("select %s from `%s`", nameSqlSb, tbName);
        }

        // 没有 position：从头开始，只 order by 主键
        if (startPkValues == null) {
            return String.format("select %s from `%s` %s", nameSqlSb, tbName, orderBy);
        }

        // 有 position：构造联合主键字典序条件
        String whereClause = buildMultiPkWhereClause();
        return String.format("select %s from `%s` where %s %s", nameSqlSb, tbName, whereClause, orderBy);
    }

    /**
     * ORDER BY pk1, pk2, ...
     */
    private String buildOrderByClause() {
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("order by ");
        for (int i = 0; i < primaryKeyColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("`").append(primaryKeyColumns.get(i).getName()).append("`");
        }
        return sb.toString();
    }

    /**
     * 联合主键 where 条件：
     * (pk1 > ?) OR (pk1 = ? AND pk2 > ?) OR (pk1 = ? AND pk2 = ? AND pk3 > ?) ...
     */
    private String buildMultiPkWhereClause() {
        int pkCount = primaryKeyColumns.size();
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < pkCount; i++) {
            if (i > 0) {
                where.append(" OR ");
            }
            where.append("(");
            // 前 i 列等于
            for (int j = 0; j < i; j++) {
                String col = primaryKeyColumns.get(j).getName();
                if (j > 0) {
                    where.append(" AND ");
                }
                where.append("`").append(col).append("` = ?");
            }
            if (i > 0) {
                where.append(" AND ");
            }
            // 第 i 列 >
            String col = primaryKeyColumns.get(i).getName();
            where.append("`").append(col).append("` > ?");
            where.append(")");
        }
        return where.toString();
    }

    /**
     * 绑定联合主键参数：
     * 顺序与 buildMultiPkWhereClause 对应：
     * (pk1 > ?1)
     * OR (pk1 = ?2 AND pk2 > ?3)
     * OR (pk1 = ?4 AND pk2 = ?5 AND pk3 > ?6)
     */
    private void bindStartKeyParams(PreparedStatement stmt) throws SQLException {
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return;
        }
        if (startPkValues == null || startPkValues.isEmpty()) {
            return;
        }
        if (startPkValues.size() != primaryKeyColumns.size()) {
            throw new IllegalStateException("startPkValues size != primaryKeyColumns size");
        }

        int pkCount = primaryKeyColumns.size();
        int index = 1;
        for (int i = 0; i < pkCount; i++) {
            // 前 i 列等于
            for (int j = 0; j < i; j++) {
                JdbcParameterBinder.bind(stmt, index++, startPkValues.get(j));
            }
            // 第 i 列 >
            JdbcParameterBinder.bind(stmt, index++, startPkValues.get(i));
        }
    }

    private void fetchData() throws Exception {
        log.info("starting fetching Data, tbName:{}, startPkValues:{}",
            fullTableName, JSON.toJSONString(startPkValues));

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;

        String fetchSql = getFetchSql();

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, schema, tbName, DBMSAction.INSERT);

            stmt = conn.prepareStatement(fetchSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);

            // 绑定 checkpoint 参数
            bindStartKeyParams(stmt);

            rs = stmt.executeQuery();
            log.info("fetching data, tbName:{}, startPkValues:{}", tbName, JSON.toJSONString(startPkValues));

            while (rs.next()) {
                ExtractorUtil.addRowData(builder, tableInfo, rs);
                if (extractorConfig.getFetchBatchSize() == builder.getRowDatas().size()) {
                    DefaultRowChange rowChange = builder.build();
                    transfer(Collections.singletonList(rowChange));

                    // 更新 position 为本批次最后一行的 PK 值
                    if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                        List<Object> lastPkValues = new ArrayList<>();
                        for (ColumnInfo ci : primaryKeyColumns) {
                            Object v = ExtractorUtil.getColumnValue(rs, ci.getName(), ci.getType());
                            lastPkValues.add(v);
                        }
                        String position = buildPosition(lastPkValues);
                        updateDbFullPosition(fullTableName, extractorConfig.getFetchBatchSize(), position,
                            RplConstants.NOT_FINISH);
                        startPkValues = lastPkValues;
                    } else {
                        updateDbFullPosition(fullTableName, extractorConfig.getFetchBatchSize(), null,
                            RplConstants.NOT_FINISH);
                    }
                    StatisticalProxy.getInstance().heartbeat();
                    builder.getRowDatas().clear();
                }
            }

            int resiSize = builder.getRowDatas().size();
            if (resiSize > 0) {
                transfer(Collections.singletonList(builder.build()));
            }
            updateDbFullPosition(fullTableName, resiSize, null, RplConstants.FINISH);
            log.info("fetching data done, dbName:{} tbName:{}, lastPkValues:{}",
                schema, tbName, JSON.toJSONString(startPkValues));
            DataSourceUtil.closeQuery(rs, stmt, conn);
        } catch (Exception e) {
            log.error("fetching data failed, schema:{}, tbName:{}, sql:{}", schema, tbName,
                fetchSql, e);
            DataSourceUtil.closeQuery(rs, stmt, conn);
            throw e;
        }
    }

    private void transfer(List<DBMSEvent> events) throws Exception {
        physicalToLogical(events);
        pipeline.directApply(events);
    }

    private void physicalToLogical(List<DBMSEvent> events) {
        for (DBMSEvent event : events) {
            event.setSchema(logicalSchema);
            ((DefaultRowChange) event).setTable(logicalTbName);
        }
    }

    private boolean checkImplicitIdExist() {
        try {
            String sql = String.format("select min(`%s`) from `%s`", RplConstants.RDS_IMPLICIT_ID, tbName);
            getMetaInfo(sql);
        } catch (SQLException e) {
            log.warn("check implicit id SQLException:", e);
            return false;
        }
        return true;
    }

    private long getTotalCount() throws SQLException {
        String sql = String.format("select count(1) from `%s`", tbName);
        Object res = getMetaInfo(sql);
        if (res == null) {
            return -1;
        }
        return Long.parseLong(String.valueOf(res));
    }

    private Object getMetaInfo(String sql) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getObject(1);
            }
        } catch (SQLException e) {
            log.error("failed in getMetaInfo: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }

        return null;
    }

    /**
     * position 统一用 JSON 数组存储 PK 值
     */
    private String buildPosition(List<Object> pkValues) {
        return JSON.toJSONString(pkValues);
    }

    private List<Object> parsePkValuesFromPosition(String position) {
        try {
            JSONArray arr = JSON.parseArray(position);
            if (arr.size() != primaryKeyColumns.size()) {
                throw new IllegalStateException("position pk size not match primaryKeyColumns size");
            }
            List<Object> res = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                res.add(arr.get(i));
            }
            return res;
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse pk position: " + position, e);
        }
    }
}
