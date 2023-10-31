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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author shicai.xsc 2020/12/9 16:24
 * @since 5.0.0.0
 */
@Slf4j
public class MysqlApplier extends BaseApplier {

    protected HostInfo hostInfo;
    protected HostInfo srcHostInfo;
    protected DbMetaCache dbMetaCache;
    protected ExecutorService executorService;
    protected DataSource defaultDataSource;
    protected AtomicBoolean firstDdl = new AtomicBoolean(true);

    public MysqlApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig);
        this.applierConfig = applierConfig;
        this.hostInfo = hostInfo;
    }

    public void setSrcHostInfo(HostInfo srcHostInfo) {
        this.srcHostInfo = srcHostInfo;
    }

    @Override
    public void init() throws Exception {
        log.info("init Applier: " + this.getClass().getName());
        if (hostInfo.getServerId() == RplConstants.SERVER_ID_NULL) {
            hostInfo.setServerId(getSrcServerId());
        }
        dbMetaCache = new DbMetaCache(hostInfo, applierConfig.getMaxPoolSize());
        defaultDataSource = dbMetaCache.getDataSource();
        executorService =
            ThreadPoolUtil
                .createExecutorWithFixedNum(applierConfig.getMaxPoolSize(), "mysqlApplier");
    }

    @Override
    public void apply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }
        if (dbmsEvents.size() == 1 && ApplyHelper.isDdl(dbmsEvents.get(0))) {
            ddlApply(dbmsEvents.get(0));
        } else {
            dmlApply(dbmsEvents);
        }
        logCommitInfo(dbmsEvents);
    }

    @Override
    public void applyDdlSql(String schema, String sql) throws Exception {
        SqlContext sqlContext = new SqlContext(sql, schema, null, null);
        execSqlContexts(Collections.singletonList(ApplyHelper.processDdlSql(sqlContext, schema)));
    }

    protected void ddlApply(DBMSEvent dbmsEvent) throws Exception {
        if (!applierConfig.isEnableDdl()) {
            log.warn("ddlApply ignore since ddl is not enabled, dbmsEvent: {}", dbmsEvent);
            return;
        }

        // prepare tso & token
        DefaultQueryLog queryLog = (DefaultQueryLog) dbmsEvent;
        String position = (String) (queryLog.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
        String tso = DdlHelper.getTso(queryLog.getQuery(), queryLog.getTimestamp(), position);
        String token = UUID.randomUUID().toString();

        // prepare ddlSqlContext
        SqlContext sqlContext = ApplyHelper.getDdlSqlContext(queryLog, token);
        if (sqlContext == null) {
            throw new PolardbxException("get ddl sql context error" + dbmsEvent);
        }
        log.info("ddlApply start, schema: {}, sql: {}, tso: {}", sqlContext.getDstSchema(), sqlContext.getSql(), tso);

        // check if need align cross multi streams
        boolean needAlign;
        long alignMasterTaskId;
        List<RplTask> rplTasks = DbTaskMetaManager.listTaskByService(TaskContext.getInstance().getServiceId());
        DdlRouteMode mode = DdlHelper.getDdlRouteMode(queryLog.getQuery());
        needAlign = rplTasks.size() > 1 && mode == DdlRouteMode.BROADCAST;
        alignMasterTaskId = rplTasks.stream().map(RplTask::getId).min(Long::compareTo).get();

        // prepare ddl log
        RplDdl rplDdl = DdlHelper.prepareDdlLogAndWait(tso, sqlContext.getSql(), needAlign, alignMasterTaskId, token);
        sqlContext.setSql(rplDdl.getDdlStmt());

        // for ddl like: create table db1.t1_1(id int, f1 int, f2 int),
        // the schema will be db2 if there is a record (db1, db2) in rewriteDbs,
        // db2 may not exists, but the ddl should be executed in db1
        DataSource dataSource = StringUtils.isNotBlank(sqlContext.getDstSchema()) ? dbMetaCache
            .getDataSource(sqlContext.getDstSchema()) : dbMetaCache.getDefaultDataSource();

        // execute ddl if get ddl lock
        if (rplDdl.getState() == DdlState.RUNNING.getValue()) {
            if (!needAlign || alignMasterTaskId == TaskContext.getInstance().getTaskId()) {
                // 由于没有实现DdlState和DDL实际执行状态的完全一致
                // 至多一个不一致的ddl
                // 因此需要对第一个running状态的ddl进行一致性检测
                DdlHelper.executeDdl(dataSource, sqlContext, firstDdl, tso, rplDdl, needAlign, dbMetaCache);
            } else {
                DdlHelper.waitDdlCompleted(tso);
            }
        } else if (rplDdl.getState() == DdlState.SUCCEED.getValue()) {
            // apply ddl succeed : do nothing
        } else {
            throw new PolardbxException("invalid rpl ddl state : " + rplDdl.getState());
        }
    }

    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        trivialDmlApply(dbmsEvents);
    }

    protected void trivialDmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        List<SqlContext> allSqlContexts = new ArrayList<>();
        for (DBMSEvent event : dbmsEvents) {
            DBMSRowChange rowChangeEvent = (DBMSRowChange) event;
            List<SqlContext> sqlContexts = getSqlContexts(rowChangeEvent, safeMode);
            allSqlContexts.addAll(sqlContexts);
        }
        // todo by jiyue use tran exec for serial apply
        execSqlContexts(allSqlContexts);
    }

    protected List<SqlContext> getSqlContexts(DBMSRowChange rowChangeEvent, boolean safeMode) {
        try {
            List<SqlContext> sqlContexts = new ArrayList<>();
            TableInfo dstTableInfo = dbMetaCache.getTableInfo(rowChangeEvent.getSchema(), rowChangeEvent.getTable());

            if (safeMode) {
                switch (rowChangeEvent.getAction()) {
                case INSERT:
                    sqlContexts = ApplyHelper.getInsertSqlExecContext(rowChangeEvent, dstTableInfo,
                        RplConstants.INSERT_MODE_REPLACE);
                    break;
                case UPDATE:
                    sqlContexts = ApplyHelper.getDeleteThenReplaceSqlExecContext(rowChangeEvent, dstTableInfo);
                    break;
                case DELETE:
                    sqlContexts = ApplyHelper.getDeleteSqlExecContext(rowChangeEvent, dstTableInfo);
                    break;
                default:
                    log.error("receive " + rowChangeEvent.getAction().name() + " action message, action is "
                        + rowChangeEvent.getAction());
                    break;
                }
            } else {
                switch (rowChangeEvent.getAction()) {
                case INSERT:
                    sqlContexts = ApplyHelper.getDeleteSqlExecContext(rowChangeEvent, dstTableInfo);
                    sqlContexts.addAll(ApplyHelper.getInsertSqlExecContext(rowChangeEvent, dstTableInfo,
                        RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE));
                    break;
                case UPDATE:
                    sqlContexts = ApplyHelper.getUpdateSqlExecContext(rowChangeEvent, dstTableInfo);
                    break;
                case DELETE:
                    sqlContexts = ApplyHelper.getDeleteSqlExecContext(rowChangeEvent, dstTableInfo);
                    break;
                default:
                    log.error("receive " + rowChangeEvent.getAction().name() + " action message, action is "
                        + rowChangeEvent.getAction());
                    break;
                }
            }
            return sqlContexts;
        } catch (Exception e) {
            throw new PolardbxException("getSqlContexts failed, dstTbName:" + rowChangeEvent.getSchema()
                + "." + rowChangeEvent.getTable(), e);
        }
    }

    protected void execSqlContexts(List<SqlContext> sqlContexts) throws Exception {
        if (sqlContexts == null || sqlContexts.isEmpty()) {
            return;
        }
        for (SqlContext sqlContext : sqlContexts) {
            long startTime = System.currentTimeMillis();
            DataSource dataSource = StringUtils.isNotBlank(sqlContext.getDstSchema()) ? dbMetaCache
                .getDataSource(sqlContext.getDstSchema()) : dbMetaCache.getDefaultDataSource();
            ApplyHelper.execUpdate(dataSource, sqlContext);
            long endTime = System.currentTimeMillis();
            StatMetrics.getInstance().addApplyCount(1);
            StatMetrics.getInstance().addRt(endTime - startTime);
        }
    }

    protected void execSqlContextsV2(List<SqlContextV2> sqlContexts) {
        if (sqlContexts == null || sqlContexts.isEmpty()) {
            return;
        }
        for (SqlContextV2 sqlContext : sqlContexts) {
            long startTime = System.currentTimeMillis();
            ApplyHelper.execUpdate(defaultDataSource, sqlContext);
            long endTime = System.currentTimeMillis();
            StatMetrics.getInstance().addApplyCount(1);
            StatMetrics.getInstance().addRt(endTime - startTime);
        }
    }

    protected void tranExecSqlContexts(List<SqlContext> sqlContexts) throws Exception {
        long startTime = System.currentTimeMillis();
        ApplyHelper.tranExecUpdate(defaultDataSource, sqlContexts);
        long endTime = System.currentTimeMillis();
        StatMetrics.getInstance().addApplyCount(1);
        StatMetrics.getInstance().addRt(endTime - startTime);
    }

    protected List<Integer> getIdentifyColumnsIndex(Map<String, List<Integer>> allTbIdentifyColumns,
                                                    String fullTbName,
                                                    DefaultRowChange rowChange) throws Exception {
        if (allTbIdentifyColumns.containsKey(fullTbName)) {
            return allTbIdentifyColumns.get(fullTbName);
        }

        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        List<String> identifyColumnNames = tbInfo.getIdentifyKeyList();
        List<Integer> identifyColumnsIndex = new ArrayList<>();
        for (String columnName : identifyColumnNames) {
            identifyColumnsIndex.add(rowChange.getColumnIndex(columnName));
        }
        allTbIdentifyColumns.put(fullTbName, identifyColumnsIndex);
        return identifyColumnsIndex;
    }

    protected List<Integer> getWhereColumnsIndex(Map<String, List<Integer>> allTbWhereColumns,
                                                 String fullTbName,
                                                 DefaultRowChange rowChange) throws Exception {
        if (allTbWhereColumns.containsKey(fullTbName)) {
            return allTbWhereColumns.get(fullTbName);
        }

        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        List<String> whereColumnNames = tbInfo.getKeyList();
        List<Integer> whereColumns = new ArrayList<>();
        for (String columnName : whereColumnNames) {
            whereColumns.add(rowChange.getColumnIndex(columnName));
        }
        allTbWhereColumns.put(fullTbName, whereColumns);
        return whereColumns;
    }

    private long getSrcServerId() throws Exception {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setAddress(new InetSocketAddress(srcHostInfo.getHost(), srcHostInfo.getPort()));
        authenticationInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        authenticationInfo.setUsername(srcHostInfo.getUserName());
        authenticationInfo.setPassword(srcHostInfo.getPassword());
        MysqlConnection connection = new MysqlConnection(authenticationInfo);
        connection.connect();
        long serverId = findServerId(connection);
        connection.disconnect();
        return serverId;
    }

    /**
     * 查询当前db的serverId信息
     */
    protected Long findServerId(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show variables like 'server_id'", rs -> {
            if (rs.next()) {
                return rs.getLong(2);
            } else {
                throw new CanalParseException(
                    "command : show variables like 'server_id' has an error! pls check. you need (at least one "
                        + "of) the SUPER,REPLICATION CLIENT privilege(s) for this operation");
            }
        });
    }

}
