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
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                    .createExecutor(applierConfig.getMinPoolSize(), applierConfig.getMaxPoolSize(), "mysqlApplier");
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
            boolean res = true;
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

    protected boolean ddlApply(DBMSEvent dbmsEvent) throws Throwable {
        // get tso
        DefaultQueryLog queryLog = (DefaultQueryLog) dbmsEvent;
        String position = (String) (queryLog.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION_STR).getValue());
        String tso = DdlHelper.getTso(queryLog.getQuery(), queryLog.getTimestamp(), position);

        // get sqlExeContext
        DdlSqlContext sqlContext = ApplyHelper.getDdlSqlExeContext(queryLog, tso);
        log.info("ddlApply start, schema: {}, sql: {}", sqlContext.getSchema(), sqlContext.getSql());
        if (!applierConfig.isEnableDdl()) {
            log.info("ddlApply ignore since ddl is not enabled");
            return true;
        }

        // try get ddl lock
        boolean lock = DdlHelper.getDdlLock(tso, sqlContext.getSql());
        RplDdl ddl = DbTaskMetaManager.getDdl(tso);

        // for ddl like: create table db1.t1_1(id int, f1 int, f2 int),
        // the schema will be db2 if there is a record (db1, db2) in rewriteDbs,
        // db2 may not exists, but the ddl should be executed in db1
        DataSource dataSource = StringUtils.isNotBlank(sqlContext.getSchema()) ? dbMetaCache
            .getDataSource(sqlContext.getSchema()) : dbMetaCache.getDefaultDataSource();
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
            log.info("ddlApply end, result: {}, schema: {}, sql: {}, pos: {}", res, sqlContext.getSchema(),
                sqlContext.getSql(), position);
            if (!res) {
                log.error("ddlApply failed when submit ddl job");
                return res;
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
            } else {
                Thread.sleep(1000);
            }
        }

        // check ddl succeed
        if (ddl.getState() == DdlState.SUCCEED.getValue()) {
            // refresh metadata
            if (StringUtils.isNotBlank(sqlContext.getSchema()) && StringUtils.isNotBlank(sqlContext.getDstTable())) {
                dbMetaCache.refreshTableInfo(sqlContext.getSchema(), sqlContext.getDstTable());
            }
            // recordTablePositionForDdl(sqlContext.getSchema() + sqlContext.getDstTable(), queryLog);
            return true;
        } else {
            log.error("ddlApply failed after ddl finished");
            return false;
        }
    }

    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        List<SqlContext> allSqlContexts = new ArrayList<>();
        for (DBMSEvent event : dbmsEvents) {
            DBMSRowChange rowChangeEvent = (DBMSRowChange) event;
            List<SqlContext> sqlContexts = getSqlContexts(rowChangeEvent, rowChangeEvent.getTable());
            allSqlContexts.addAll(sqlContexts);
        }
        // for no merge apply, can NOT execute parallel
        return execSqlContexts(allSqlContexts);
    }

    protected List<SqlContext> getSqlContexts(DBMSRowChange rowChangeEvent, String dstTbName) {
        try {
            List<SqlContext> sqlContexts = new ArrayList<>();
            TableInfo dstTableInfo = dbMetaCache.getTableInfo(rowChangeEvent.getSchema(), dstTbName);

            switch (rowChangeEvent.getAction()) {
            case INSERT:
                sqlContexts = ApplyHelper.getDeleteThenInsertSqlExecContext(rowChangeEvent, dstTableInfo);
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
            return sqlContexts;
        } catch (Throwable e) {
            log.error("getSqlContexts failed, dstTbName: {}", dstTbName, e);
            return null;
        }
    }

    protected boolean execSqlContexts(List<SqlContext> sqlContexts) {
        if (sqlContexts == null || sqlContexts.size() == 0) {
            return true;
        }

        boolean res = true;
        List<SqlContext> batchSqlContexts = mergeSendBatchSqlContexts(sqlContexts);
        for (SqlContext sqlContext : batchSqlContexts) {
            long startTime = System.currentTimeMillis();
            res &= ApplyHelper.execUpdate(defaultDataSource, sqlContext);
            if (!res) {
                log.error("execSqlContexts failed, mergeSqlContext: {}", sqlContext);
                if (skipAllException) {
                    StatisticalProxy.getInstance().addSkipExceptionCount(1);
                }
                return skipAllException;
            }

            // counter
            long endTime = System.currentTimeMillis();
            StatisticalProxy.getInstance().addApplyCount(1);
            StatisticalProxy.getInstance().addRt(endTime - startTime);
        }
        return true;
    }

    protected boolean tranExecSqlContexts(List<SqlContext> sqlContexts) {
        long startTime = System.currentTimeMillis();
        List<SqlContext> mergeSqlContexts = mergeSendBatchSqlContexts(sqlContexts);
        boolean res = ApplyHelper.tranExecUpdate(defaultDataSource, mergeSqlContexts);
        if (res) {
            long endTime = System.currentTimeMillis();
            StatisticalProxy.getInstance().addApplyCount(1);
            StatisticalProxy.getInstance().addRt(endTime - startTime);
        }
        return res;
    }

    protected void recordTablePosition(String tbName, DefaultRowChange lastRowChange) {
        logCommitInfo(Arrays.asList(lastRowChange));
        StatisticalProxy.getInstance().recordTablePosition(tbName, lastRowChange);
    }

    protected boolean filterCommitedEvent(String fullTbName, DefaultRowChange rowChange) {
        Map<String, String> lastRunTablePositions = StatisticalProxy.getInstance().getLastRunTablePositions();

        if (!lastRunTablePositions.containsKey(fullTbName)) {
            return false;
        }

        String lastRunTablePosition = lastRunTablePositions.get(fullTbName);
        if (StringUtils.isBlank(lastRunTablePosition)) {
            return false;
        }

        String position = (String) (rowChange.getOption(RplConstants.BINLOG_EVENT_OPTION_POSITION).getValue());
        if (CommonUtil.comparePosition(position, lastRunTablePosition) <= 0) {
            log.info("event filtered, tbName: {}, position: {}, lastRunTablePosition:{}", fullTbName, position,
                lastRunTablePosition);
            return true;
        }

        // 走到这里，说明当前位点已经超过了上次任务退出时记录的该表最后位点，则后续都不需要校验是否 filter
        lastRunTablePositions.remove(fullTbName);
        return false;
    }

    protected List<Integer> getIdentifyColumns(Map<String, List<Integer>> allTbIdentifyColumns,
                                               String fullTbName,
                                               DefaultRowChange rowChange) throws Throwable {
        if (allTbIdentifyColumns.containsKey(fullTbName)) {
            return allTbIdentifyColumns.get(fullTbName);
        }

        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        List<String> identifyColumnNames = ApplyHelper.getIdentifyColumns(tbInfo);
        List<Integer> whereColumns = new ArrayList<>();
        for (String columnName : identifyColumnNames) {
            whereColumns.add(rowChange.getColumnIndex(columnName));
        }
        allTbIdentifyColumns.put(fullTbName, whereColumns);
        return whereColumns;
    }

    private List<SqlContext> mergeSendBatchSqlContexts(List<SqlContext> sqlContexts) {
        List<SqlContext> results = new ArrayList<>();

        int i = 0;
        while (i < sqlContexts.size()) {
            StringBuilder sqlSb = new StringBuilder();
            List<Serializable> params = new ArrayList<>();
            for (int j = 0; j < applierConfig.getSendBatchSize() && i < sqlContexts.size(); j++) {
                SqlContext sqlContext = sqlContexts.get(i);
                sqlSb.append(sqlContext.getSql()).append(";");
                params.addAll(sqlContext.getParams());
                i++;
            }

            SqlContext batchSqlContext = new SqlContext(sqlSb.toString(), "", params);
            results.add(batchSqlContext);
        }

        return results;
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
