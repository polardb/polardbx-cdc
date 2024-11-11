/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_APPLY_USE_CACHED_THREAD_POOL_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.getDdlSqlContext;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.processDdlSql;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.tryRefreshMetaInfo;

/**
 * @author shicai.xsc 2020/12/9 16:24
 * @since 5.0.0.0
 */
@Slf4j
public class MysqlApplier extends BaseApplier {

    protected final HostInfo hostInfo;
    protected final long startTimeStamp;
    protected final ConflictStrategy conflictStrategy;

    protected final HostInfo srcHostInfo;

    protected ExecutorService executorService;
    protected DbMetaCache dbMetaCache;

    public MysqlApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig);
        this.hostInfo = hostInfo;
        this.srcHostInfo = srcHostInfo;
        this.startTimeStamp = System.currentTimeMillis();
        this.conflictStrategy = applierConfig.getConflictStrategy();
    }

    @Override
    public void init() throws Exception {
        log.info("init Applier: " + this.getClass().getName());
        if (hostInfo.getServerId() == RplConstants.SERVER_ID_NULL) {
            hostInfo.setServerId(getSrcServerId());
        }
        buildMaxPoolSize();
        buildExecutorService();
        dbMetaCache = new DbMetaCache(hostInfo, applierConfig.getMinPoolSize(), applierConfig.getMaxPoolSize(),
            false);

        DmlApplyHelper.setCompareAll(applierConfig.isCompareAll());
        DmlApplyHelper.setInsertOnUpdateMiss(applierConfig.isInsertOnUpdateMiss());
        DmlApplyHelper.setDbMetaCache(dbMetaCache);
    }

    @Override
    public void start() {
        super.start();
        AsyncDdlMonitor.getInstance().setDbMetaCache(dbMetaCache);
        AsyncDdlMonitor.getInstance().start();
    }

    @Override
    public void stop() {
        super.stop();
        AsyncDdlMonitor.getInstance().stop();
    }

    @Override
    public void apply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }
        if (dbmsEvents.size() == 1 && DdlApplyHelper.isDdl(dbmsEvents.get(0))) {
            ddlApply(dbmsEvents.get(0));
        } else {
            dmlApply(dbmsEvents);
        }
        logCommitInfo(dbmsEvents);
    }

    @Override
    public void applyDdlSql(String schema, String sql) throws Exception {
        SqlContext sqlContext = new SqlContext(sql, schema, null, null);
        DataSource dataSource = dbMetaCache.getDataSource(schema);
        try (Connection conn = dataSource.getConnection()) {
            DmlApplyHelper.execSqlContext(conn, processDdlSql(sqlContext, schema));
        }
    }

    protected void ddlApply(DBMSEvent dbmsEvent) throws Exception {
        if (!applierConfig.isEnableDdl()) {
            log.warn("ddlApply ignore since ddl is not enabled, dbmsEvent: {}", dbmsEvent);
            return;
        }

        // prepare tso & token
        DefaultQueryLog queryLog = (DefaultQueryLog) dbmsEvent;
        String position = queryLog.getPosition();
        String tso = DdlApplyHelper.getTso(queryLog.getQuery(), queryLog.getTimestamp(), position);
        String token = UUID.randomUUID().toString();

        // prepare ddlSqlContext
        SqlContext sqlContext = getDdlSqlContext(queryLog, token, tso);
        if (sqlContext == null) {
            log.info("ddl sql is ignored, sql: {}, tso: {}.", queryLog.getQuery(), tso);
            return;
        } else {
            log.info("ddlApply start, schema: {}, sql: {}, tso: {}, async: {}", sqlContext.getDstSchema(),
                sqlContext.getSql(), tso, sqlContext.isAsyncDdl());
        }

        // check if need align cross multi streams
        boolean needAlign;
        long alignMasterTaskId;
        List<RplTask> rplTasks = DbTaskMetaManager.listTaskByService(TaskContext.getInstance().getServiceId());
        Map<String, Object> polarxVariables = DdlApplyHelper.getPolarxVariables(queryLog.getQuery());
        DdlRouteMode mode = DdlApplyHelper.getDdlRouteMode(queryLog.getQuery());
        needAlign = rplTasks.size() > 1 && mode == DdlRouteMode.BROADCAST;
        alignMasterTaskId = rplTasks.stream().map(RplTask::getId).min(Long::compareTo).get();

        // prepare ddl log
        RplDdl rplDdl = DdlApplyHelper.prepareDdlLogAndWait(sqlContext, tso, needAlign, alignMasterTaskId, token);
        sqlContext.setSql(rplDdl.getDdlStmt());

        if (polarxVariables.containsKey("FP_OVERRIDE_NOW")) {
            // 实验室中改变cn ddl 时间戳的用户变量
            sqlContext.setFpOverrideNow(
                (String) polarxVariables.get("FP_OVERRIDE_NOW"));
        }

        // for ddl like: create table db1.t1_1(id int, f1 int, f2 int),
        // the schema will be db2 if there is a record (db1, db2) in rewriteDbs,
        // db2 may not exists, but the ddl should be executed in db1
        DataSource dataSource = dbMetaCache.getDataSource(sqlContext.getDstSchema());

        // execute ddl if get ddl lock
        if (DdlState.valueOf(rplDdl.getState()) == DdlState.RUNNING) {
            if (!needAlign || alignMasterTaskId == TaskContext.getInstance().getTaskId()) {
                // 由于没有实现DdlState和DDL实际执行状态的完全一致
                // 至多一个不一致的ddl
                // 因此需要对第一个running状态的ddl进行一致性检测
                DdlApplyHelper.executeDdl(dataSource, sqlContext, queryLog.getFirstDdl(), tso, rplDdl, needAlign,
                    dbMetaCache, sqlContext.isAsyncDdl());
            } else {
                DdlApplyHelper.waitDdlCompleted(tso, sqlContext.isAsyncDdl());
                tryRefreshMetaInfo(rplDdl, sqlContext, dbMetaCache);
            }
        } else if (DdlState.valueOf(rplDdl.getState()) == DdlState.SUCCEED) {
            // apply ddl succeed
            tryRefreshMetaInfo(rplDdl, sqlContext, dbMetaCache);
        } else {
            throw new PolardbxException("invalid rpl ddl state : " + rplDdl.getState());
        }
    }

    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        trivialDmlApply(dbmsEvents);
    }

    protected void trivialDmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        DataSource dataSource = dbMetaCache.getDataSource(dbmsEvents.get(0).getSchema());
        try (Connection conn = dataSource.getConnection()) {
            DmlApplyHelper.executeDML(conn, (List<DefaultRowChange>) (List<?>) dbmsEvents, conflictStrategy);
        }
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

    private void buildMaxPoolSize() {
        if (getBoolean(IS_LAB_ENV)) {
            List<RplTask> rplTasks = DbTaskMetaManager.listTaskByService(TaskContext.getInstance().getServiceId());
            int newSize = applierConfig.getMaxPoolSize() / rplTasks.size();
            applierConfig.setMaxPoolSize(newSize);
        }
    }

    void buildExecutorService() {
        if (getBoolean(RPL_APPLY_USE_CACHED_THREAD_POOL_ENABLED)) {
            executorService = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat("mysqlApplier-%d").build());
        } else {
            executorService = ThreadPoolUtil.createExecutorWithFixedNum(
                applierConfig.getMaxPoolSize(), "mysqlApplier");
        }
    }
}
