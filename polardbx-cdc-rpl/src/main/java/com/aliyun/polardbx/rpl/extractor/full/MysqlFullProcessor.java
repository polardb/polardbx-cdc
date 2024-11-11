/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.full;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import org.apache.commons.lang3.StringUtils;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.StringUtils2;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author shicai.xsc 2020/12/9 17:46
 * @since 5.0.0.0
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
    private String orderKey;
    private ColumnInfo orderKeyColumnInfo;
    private Object orderKeyStart;
    private boolean useRdsImplicitId;

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
            orderKey = null;
            if (tableInfo.getPks() != null && !tableInfo.getPks().isEmpty()) {
                orderKey = tableInfo.getPks().get(0);
                orderKeyColumnInfo = tableInfo.getColumns().stream().filter(c -> c.getName()
                    .equals(orderKey.toLowerCase())).findFirst().orElse(null);
            } else if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FULL_USE_IMPLICIT_ID)) {
                // 无主键表，但启动隐藏主键
                orderKey = RplConstants.RDS_IMPLICIT_ID;
                if (checkOrderKeyExist()) {
                    orderKeyColumnInfo = new ColumnInfo(RplConstants.RDS_IMPLICIT_ID, Types.BIGINT, null,
                        false, false, null, 0);
                    useRdsImplicitId = true;
                } else {
                    orderKey = null;
                    orderKeyColumnInfo = null;
                    useRdsImplicitId = false;
                }
            }
            if (orderKeyColumnInfo == null) {
                log.error("can't find orderKey and orderKeyColumnInfo for schema:{}, tbName:{}", schema, tbName);
            }
            orderKeyStart = null;
            if (orderKeyColumnInfo != null) {
                if (StringUtils.isBlank(fullPosition.getPosition())) {
                    orderKeyStart = getMinOrderKey();
                } else {
                    orderKeyStart = fullPosition.getPosition();
                }
            }
            fetchData();
        } catch (Exception e) {
            log.error("failed to start full processor", e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_FULL_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            StatisticalProxy.getInstance().recordLastError(e.toString());
            TaskContext.getInstance().getPipeline().stop();
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
            // init full progress record
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
        if (orderKeyColumnInfo != null) {
            return orderKeyStart == null ?
                String.format("select %s from `%s` order by `%s`", nameSqlSb, tbName, orderKey) : String
                .format("select %s from `%s` where `%s` >= ? order by `%s`", nameSqlSb, tbName, orderKey, orderKey);
        }
        return String.format("select %s from `%s`", nameSqlSb, tbName);
    }

    private void fetchData() throws Exception {
        log.info("starting fetching Data, tbName:{}", fullTableName);

        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;

        String fetchSql = getFetchSql();

        try {
            conn = dataSource.getConnection();
            // 为了设置fetchSize,必须设置为false
            conn.setAutoCommit(false);
            RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, schema, tbName, DBMSAction.INSERT);
            // List<DBMSEvent> events = new ArrayList<>();
            // prepared statement
            stmt = conn.prepareStatement(fetchSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
            if (orderKeyColumnInfo != null && orderKeyStart != null) {
                stmt.setObject(1, orderKeyStart);
            }

            // execute fetch sql
            rs = stmt.executeQuery();
            log.info("fetching data, tbName:{}, orderKeyStart:{}", tbName, orderKeyStart);

            while (rs.next()) {
                ExtractorUtil.addRowData(builder, tableInfo, rs);
                if (extractorConfig.getFetchBatchSize() == builder.getRowDatas().size()) {
                    DefaultRowChange rowChange = builder.build();
                    transfer(Collections.singletonList(rowChange));
                    if (orderKeyColumnInfo != null) {
                        orderKeyStart = ExtractorUtil.getColumnValue(rs, orderKeyColumnInfo.getName(),
                            orderKeyColumnInfo.getType());
                        String position = StringUtils2.safeToString(orderKeyStart);
                        updateDbFullPosition(fullTableName, extractorConfig.getFetchBatchSize(), position,
                            RplConstants.NOT_FINISH);
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
            log.info("fetching data done, dbName:{} tbName:{}, last orderKeyValue:{}", schema, tbName, orderKeyStart);
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

    private Object getMinOrderKey() throws SQLException {
        String sql = String.format("select min(`%s`) from `%s`", orderKey, tbName);
        return getMetaInfo(sql);
    }

    private boolean checkOrderKeyExist() {
        try {
            getMinOrderKey();
        } catch (SQLException e) {
            log.warn("SQLException:", e);
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
            rs = stmt.executeQuery(sql);
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
}
