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
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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
    protected boolean firstDdl = true;

    public MysqlApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig);
        this.applierConfig = applierConfig;
        this.hostInfo = hostInfo;
    }

    public void setSrcHostInfo(HostInfo srcHostInfo) {
        this.srcHostInfo = srcHostInfo;
    }

    @Override
    public boolean init() {
        try {
            log.info("init Applier: " + this.getClass().getName());
            if (hostInfo.getServerId() == RplConstants.SERVER_ID_NULL) {
                hostInfo.setServerId(getSrcServerId());
            }
            dbMetaCache = new DbMetaCache(hostInfo, applierConfig.getMaxPoolSize());
            defaultDataSource = dbMetaCache.getDataSource();
            executorService =
                ThreadPoolUtil
                    .createExecutorWithFixedNum(applierConfig.getMaxPoolSize(), "mysqlApplier");
            return true;
        } catch (Throwable e) {
            log.error("MysqlApplier init failed", e);
            return false;
        }
    }

    @Override
    public boolean apply(List<DBMSEvent> dbmsEvents) {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }

        try {
            boolean res;
            if (dbmsEvents.size() == 1 && ApplyHelper.isDdl(dbmsEvents.get(0))) {
                res = ddlApply(dbmsEvents.get(0));
            } else {
                res = dmlApply(dbmsEvents);
            }
            if (res) {
                logCommitInfo(dbmsEvents);
            }
            return res;
        } catch (Throwable e) {
            log.error("apply failed", e);
            return false;
        }
    }

    @Override
    public boolean applyDdlSql(String schema, String sql) throws Exception {
        log.debug("direct apply sql, schema: {}, sql: {}", schema, sql);
        DataSource dataSource = StringUtils.isNotBlank(schema) ? dbMetaCache
            .getDataSource(schema) : dbMetaCache.getDefaultDataSource();
        SqlContext sqlContext = new SqlContext(sql, schema, null, null);
        return ApplyHelper.execUpdate(dataSource, ApplyHelper.processDdlSql(sqlContext, schema));
    }


    protected boolean ddlApply(DBMSEvent dbmsEvent) throws Throwable {
        if (!applierConfig.isEnableDdl()) {
            log.warn("ddlApply ignore since ddl is not enabled, dbmsEvent: {}", dbmsEvent);
            return true;
        }
        // get tso
        DefaultQueryLog queryLog = (DefaultQueryLog) dbmsEvent;
        String position = (String) (queryLog.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
        String tso = DdlHelper.getTso(queryLog.getQuery(), queryLog.getTimestamp(), position);

        // get ddlSqlContext
        SqlContext sqlContext = ApplyHelper.getDdlSqlContext(queryLog, tso, applierConfig.isUseOriginalSql());
        if (sqlContext == null) {
            log.warn("get ddl sql context error");
            return true;
        }
        log.info("ddlApply start, schema: {}, sql: {}", sqlContext.getDstSchema(), sqlContext.getSql());

        // try get ddl lock
        RplDdl ddl = new RplDdl();
        boolean lock = DdlHelper.getDdlLock(tso, sqlContext.getSql(), ddl);

        // for ddl like: create table db1.t1_1(id int, f1 int, f2 int),
        // the schema will be db2 if there is a record (db1, db2) in rewriteDbs,
        // db2 may not exists, but the ddl should be executed in db1
        DataSource dataSource = StringUtils.isNotBlank(sqlContext.getDstSchema()) ? dbMetaCache
            .getDataSource(sqlContext.getDstSchema()) : dbMetaCache.getDefaultDataSource();

        // only repair state of first ddl when restart
        if (firstDdl) {
            firstDdl = false;
            ApplyHelper.tryRepairRplDdlState(dataSource, tso, ddl);
        }

        // execute ddl if get ddl lock
        if (lock && ddl.getState() == DdlState.NOT_START.getValue()) {
            boolean res = ApplyHelper.execUpdate(dataSource, sqlContext);
            if (res) {
                // set ddl status to RUNNING
                RplDdl newDdl = new RplDdl();
                newDdl.setId(ddl.getId());
                newDdl.setState(DdlState.RUNNING.getValue());
                DbTaskMetaManager.updateDdl(newDdl);
            }
            log.info("ddlApply end, result: {}, schema: {}, sql: {}", res, sqlContext.getDstSchema(),
                sqlContext.getSql());
            if (!res) {
                log.error("ddlApply failed when submit ddl job");
                return false;
            }
        }

        // wait ddl finish
        while (!DdlState.from(ddl.getState()).isFinished()) {
            StatisticalProxy.getInstance().heartbeat();
            if (lock) {
                // check if ddl is done by "show full ddl"
                DdlState state = ApplyHelper.getAsyncDdlState(dataSource, tso);
                ddl.setState(state.getValue());
                DbTaskMetaManager.updateDdl(ddl);
            } else {
                ddl = DbTaskMetaManager.getDdl(tso);
            }

            if (DdlState.from(ddl.getState()).isFinished()) {
                break;
            }
            Thread.sleep(100);
        }

        // check ddl succeed
        if (ddl.getState() == DdlState.SUCCEED.getValue()) {
            // refresh metadata
            if (StringUtils.isNotBlank(sqlContext.getDstSchema()) && StringUtils.isNotBlank(sqlContext.getDstTable())) {
                dbMetaCache.refreshTableInfo(sqlContext.getDstSchema(), sqlContext.getDstTable());
            }
            return true;
        } else {
            log.error("ddlApply failed after ddl finished");
            return false;
        }
    }

    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        return trivialDmlApply(dbmsEvents);
    }

    protected boolean trivialDmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        List<SqlContext> allSqlContexts = new ArrayList<>();
        for (DBMSEvent event : dbmsEvents) {
            DBMSRowChange rowChangeEvent = (DBMSRowChange) event;
            List<SqlContext> sqlContexts = getSqlContexts(rowChangeEvent, safeMode);
            allSqlContexts.addAll(sqlContexts);
        }
        // todo by jiyue use tran exec for serial apply
        return execSqlContexts(allSqlContexts);
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
        } catch (Throwable e) {
            log.error("getSqlContexts failed, dstTbName: {}",
                rowChangeEvent.getSchema() + "." + rowChangeEvent.getTable(), e);
            return null;
        }
    }

    protected boolean execSqlContexts(List<SqlContext> sqlContexts) {
        if (sqlContexts == null || sqlContexts.size() == 0) {
            return true;
        }
        boolean res;
        for (SqlContext sqlContext : sqlContexts) {
            long startTime = System.currentTimeMillis();
            res = ApplyHelper.execUpdate(defaultDataSource, sqlContext);
            if (!res) {
                return false;
            }
            long endTime = System.currentTimeMillis();
            StatisticalProxy.getInstance().addApplyCount(1);
            StatisticalProxy.getInstance().addRt(endTime - startTime);
        }
        return true;
    }

    protected boolean execSqlContextsV2(List<SqlContextV2> sqlContexts) {
        if (sqlContexts == null || sqlContexts.size() == 0) {
            return true;
        }
        boolean res;
        for (SqlContextV2 sqlContext : sqlContexts) {
            long startTime = System.currentTimeMillis();
            res = ApplyHelper.execUpdate(defaultDataSource, sqlContext);
            if (!res) {
                return false;
            }
            long endTime = System.currentTimeMillis();
            StatisticalProxy.getInstance().addApplyCount(1);
            StatisticalProxy.getInstance().addRt(endTime - startTime);
        }
        return true;
    }

    protected boolean tranExecSqlContexts(List<SqlContext> sqlContexts) {
        long startTime = System.currentTimeMillis();
        boolean res = ApplyHelper.tranExecUpdate(defaultDataSource, sqlContexts);
        if (res) {
            long endTime = System.currentTimeMillis();
            StatisticalProxy.getInstance().addApplyCount(1);
            StatisticalProxy.getInstance().addRt(endTime - startTime);
        }
        return res;
    }


    protected List<Integer> getIdentifyColumnsIndex(Map<String, List<Integer>> allTbIdentifyColumns,
                                                    String fullTbName,
                                                    DefaultRowChange rowChange) throws Throwable {
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
                                                 DefaultRowChange rowChange) throws Throwable {
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
        return mysqlConnection.query("show variables like 'server_id'", new MysqlConnection.ProcessJdbcResult<Long>() {

            @Override
            public Long process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    return rs.getLong(2);
                } else {
                    throw new CanalParseException(
                        "command : show variables like 'server_id' has an error! pls check. you need (at least one "
                            + "of) the SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

}
