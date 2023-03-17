/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.extractor.full;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import org.apache.commons.lang3.StringUtils;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
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
    private Object orderKeyStart;


    public void preStart() {
        fullTableName = schema + "." + tbName;
        initFullPositionIfNotExist();
    }

    public void start() {
        try {
            tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, hostInfo.getType());
            orderKey = null;
            if (tableInfo.getPks() != null && tableInfo.getPks().size() != 0) {
                orderKey = tableInfo.getPks().get(0);
            } else if (tableInfo.getUks() != null && tableInfo.getUks().size() != 0) {
                for (ColumnInfo column: tableInfo.getColumns()) {
                    if (column.getName().equals(tableInfo.getUks().get(0))) {
                        if (!column.isNullable()) {
                            orderKey = tableInfo.getUks().get(0);
                        }
                        break;
                    }
                }
            }
            if (orderKey == null) {
                // 暂不支持不含主键或非空uk的表，会直接忽略并标识成功
                log.error("can't find orderKey for schema:{}, tbName:{}", schema, tbName);
                updateDbFullPosition(fullTableName, 0, null,  RplConstants.FINISH);
                return;
            }

            RplDbFullPosition fullPosition =
                DbTaskMetaManager.getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
            if (fullPosition.getFinished() == RplConstants.FINISH) {
                log.info("full copy done, position is finished. schema:{}, tbName:{}", schema, tbName);
                return;
            }
            orderKeyStart = null;
            if (StringUtils.isBlank(fullPosition.getPosition())) {
                orderKeyStart = getMinOrderKey();
            } else {
                orderKeyStart = fullPosition.getPosition();
            }
            if (orderKeyStart == null) {
                if (getTotalCount() == 0) {
                    updateDbFullPosition(fullTableName, 0, null,  RplConstants.FINISH);
                    log.info("full transfer done, 0 record in table, schema:{}, tbName:{}", schema, tbName);
                }
            }
            fetchData();
        } catch (Throwable e) {
            log.error("failed to start com.aliyun.polardbx.extractor", e);
            MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_FULL_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
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
            log.error("failed to init full position for: {} because of :{}", fullTableName, e);
            System.exit(1);
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
        if (orderKeyStart != null) {
            return String
                .format("select %s from `%s` where `%s` >= ? order by `%s`", nameSqlSb, tbName, orderKey, orderKey);
        }
        return String.format("select %s from `%s` order by `%s`", nameSqlSb, tbName, orderKey);
    }

    private void fetchData() throws Throwable {
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
            if (orderKeyStart != null) {
                stmt.setObject(1, orderKeyStart);
            }

            // execute fetch sql
            rs = stmt.executeQuery();
            log.info("fetching data, tbName:{}, orderKeyStart:{}", tbName, orderKeyStart);

            while (rs.next()) {
                ExtractorUtil.addRowData(builder, tableInfo, rs);
                if (extractorConfig.getFetchBatchSize() == builder.getRowDatas().size()) {
                    DBMSRowChange rowChange = builder.build();
                    transfer(Collections.singletonList(rowChange));
                    orderKeyStart = rowChange.getRowValue(extractorConfig.getFetchBatchSize(), orderKey);
                    String position = StringUtils2.safeToString(orderKeyStart);
                    updateDbFullPosition(fullTableName, extractorConfig.getFetchBatchSize(), position,
                        RplConstants.NOT_FINISH);
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
        } catch (Throwable e) {
            log.error("fetching data failed, schema:{}, tbName:{}, sql:{}", schema, tbName,
                fetchSql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    private void transfer(List<DBMSEvent> events) {
        physicalToLogical(events);
        pipeline.directApply(events);
    }

    private void physicalToLogical(List<DBMSEvent> events) {
        for (DBMSEvent event : events) {
            event.setSchema(logicalSchema);
            ((DefaultRowChange)event).setTable(logicalTbName);
        }
    }

    private Object getMinOrderKey() throws SQLException {
        String sql = String.format("select min(`%s`) from `%s`", orderKey, tbName);
        return getMetaInfo(sql);
    }

    private Object getMaxOrderKey() throws SQLException {
        String sql = String.format("select max(`%s`) from `%s`", orderKey, tbName);
        return getMetaInfo(sql);
    }

    private long getTotalCount() throws SQLException {
        String sql = String.format("select count(1) from `%s`", tbName);
        Object res = getMetaInfo(sql);
        if (res == null) {
            return -1;
        }
        return Long.valueOf(String.valueOf(res));
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

    public static synchronized RplDbFullPosition initDbFullPosition(String fullTableName, long totalCount,
                                                                    String endPosition) {
        return DbTaskMetaManager
            .addDbFullPosition(TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getServiceId(),
                TaskContext.getInstance().getTaskId(), fullTableName, totalCount, endPosition);
    }

    public static synchronized RplDbFullPosition updateDbFullPosition(String fullTableName, long incFinishedCount,
                                                                      String position, int finished) {
        RplDbFullPosition record =
            DbTaskMetaManager.getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
        RplDbFullPosition newRecord = new RplDbFullPosition();
        newRecord.setId(record.getId());
        newRecord.setFinishedCount(record.getFinishedCount() + incFinishedCount);
        newRecord.setPosition(position);
        newRecord.setFinished(finished);
        return DbTaskMetaManager
            .updateDbFullPosition(newRecord);
    }
}
