/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSON;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsExtractHotKey;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsMergePartition;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsSplitHotKey;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsSplitPartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddPartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropPartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableReorgPartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsAlterTablePartition;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableAlterFullTextIndex;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAnalyzeStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateRoleStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateUserStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.binlog.service.RplSyncPointService;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalize;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_ASYNC_DDL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_ASYNC_DDL_THRESHOLD_IN_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_APPLY_COLUMNAR_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_RETRY_INTERVAL_MILLS;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_RETRY_MAX_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_WAIT_ALIGN_INTERVAL_MILLS;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.canal.LogEventUtil.SYNC_POINT_PROCEDURE_NAME;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_DDL_ROUTE_PREFIX;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_TSO_PREFIX;
import static com.aliyun.polardbx.binlog.util.CommonUtils.extractPolarxOriginSql;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.binlog.util.SQLUtils.removeSomeHints;
import static com.aliyun.polardbx.rpl.applier.SqlContextExecutor.execUpdate;
import static com.aliyun.polardbx.rpl.common.RplConstants.ASYNC_DDL_HINTS;

/**
 * @author shicai.xsc 2021/4/5 23:22
 * @since 5.0.0.0
 */
@Slf4j
public class DdlApplyHelper {
    /**
     * Constants
     */
    private static final String DDL_TOKEN_PATTERN = "DDL_SUBMIT_TOKEN=%s";
    private static final String DDL_TOKEN_COMMENT = "/*" + DDL_TOKEN_PATTERN + "*/";
    private static final String COLUMN_DEF_JOB_ID = "JOB_ID";
    private static final String COLUMN_DEF_DDL_STMT = "DDL_STMT";
    private static final String COLUMN_DEF_STATE = "STATE";
    private static final String DDL_STATE_COMPLETED = "COMPLETED";
    private static final String DDL_STATE_PAUSED = "PAUSED";

    /**
     * Sql Scripts
     */
    private static final String SQL_SHOW_FULL_DDL = "SHOW FULL DDL";
    private static final String SQL_CONTINUE_DDL = "continue ddl %s";
    private static final String SQL_CHECK_DDL_STATE =
        "select * from metadb.ddl_engine_archive where state = '%s' and ddl_stmt like '%%%s%%' and gmt_created >= %s "
            + "order by gmt_created desc";

    /**
     * Patterns
     */
    private static final Pattern TSO_PATTERN =
        Pattern.compile(PRIVATE_DDL_TSO_PREFIX + "([\\d]+)");
    private static final Pattern DDL_ROUTE_MODE_PATTERN =
        Pattern.compile(PRIVATE_DDL_DDL_ROUTE_PREFIX + "([\\W\\w]+)");

    public static SqlContext getDdlSqlContext(DefaultQueryLog queryLog, String token, String tso) {

        SqlContext sqlContext = new SqlContext(queryLog.getQuery(), "", "", new ArrayList<>(),
            SqlModeUtil.convertSqlMode(queryLog.getSqlMode()));
        sqlContext.setDdlParallelSeq(queryLog.getParallelSeq());
        String originSql = DdlApplyHelper.getOriginSql(queryLog.getQuery());
        String sql = StringUtils.isNotBlank(originSql) ? originSql : queryLog.getQuery();
        sql = removeSomeHints(sql);

        // process for async ddl
        String asyncSql = tryAttachAsyncDdlHints(sql, queryLog.getExecTime());
        if (!StringUtils.equals(sql, asyncSql)) {
            LogUtil.getAsyncDdlLogger().info("[async mode ddl], tso: {}, token: {}, execTime:{} , sql: {}",
                tso, token, queryLog.getExecTime(), sql);

            sql = asyncSql;
            sqlContext.setAsyncDdl(true);
        }

        // process for columnar index
        DdlResult result = DruidDdlParser.parse(sql, queryLog.getSchema());
        if (!getBoolean(RPL_DDL_APPLY_COLUMNAR_ENABLED) && result != null && result.getSqlStatement() != null) {
            // TODO 增加判断逻辑：目标实例是否开通了columnar
            Pair<Boolean, Boolean> pair = tryRemoveColumnarIndex(result.getSqlStatement(), queryLog.getTableMeta());
            if (pair.getKey()) {
                if (pair.getValue()) {
                    log.info("ddl sql contains columnar index and reformatted, before: {}, after: {}.",
                        sql, result.getSqlStatement().toString());
                    sql = result.getSqlStatement().toString();
                } else {
                    log.info("ddl sql contains columnar index and will be ignored, sql: {}", sql);
                    return null;
                }
            }
            if (isCciDdl(queryLog.getQuery())) {
                log.info("ddl sql is cci and will be ignored, sql: {}", queryLog.getQuery());
                return null;
            }
        }

        // 如果sql头部含有 /* //1/ */，则把token comment放到sql的尾部，否则/* //1/ */对应的影子表机制不会生效
        String comment = String.format(DDL_TOKEN_COMMENT, token);
        if (StringUtils.startsWith(sql, "/* //1/ */")) {
            sqlContext.setSql(sql + comment);
        } else {
            sqlContext.setSql(comment + sql);
        }
        sqlContext.setDdlEventSchema(queryLog.getSchema());

        // use actual schemaName
        return processDdlSql(sqlContext, queryLog.getSchema(), result);
    }

    @SneakyThrows
    public static boolean checkIfDdlSucceed(DataSource dataSource, String token, Date ddlCreateTime) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            String checkSql = String.format(SQL_CHECK_DDL_STATE, DDL_STATE_COMPLETED, token, ddlCreateTime.getTime());

            rs = stmt.executeQuery(checkSql);
            while (rs.next()) {
                String ddlStmt = rs.getString(COLUMN_DEF_DDL_STMT);
                String tsoPattern = String.format(DDL_TOKEN_PATTERN, token);
                if (ddlStmt.contains(tsoPattern)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("failed in getAsyncDdlState, tso: {}", token, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    @SneakyThrows
    public static DdlJobInfo checkIfDdlRunning(DataSource dataSource, String token) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SQL_SHOW_FULL_DDL);
            rs = stmt.executeQuery(SQL_SHOW_FULL_DDL);
            while (rs.next()) {
                Long ddlJobId = rs.getLong(COLUMN_DEF_JOB_ID);
                String ddlJobState = rs.getString(COLUMN_DEF_STATE);
                String ddlStmt = rs.getString(COLUMN_DEF_DDL_STMT);

                String tsoPattern = String.format(DDL_TOKEN_PATTERN, token);
                if (ddlStmt.contains(tsoPattern)) {
                    return new DdlJobInfo(ddlJobId, ddlJobState);
                }
            }
            return null;
        } catch (Throwable e) {
            log.error("failed in getAsyncDdlState, tso: {}", token, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    @VisibleForTesting
    static void tryWaitCreateOrDropDatabase(DataSource dataSource, String token, long timeoutSecond)
        throws InterruptedException, SQLException {
        long startTime = System.currentTimeMillis();
        while (true) {
            if (timeoutSecond > 0 && System.currentTimeMillis() - startTime > timeoutSecond * 1000) {
                throw new TimeoutException("wait create or drop database timeout");
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            if (checkCreateOrDropDatabaseRunning(dataSource, token)) {
                StatisticalProxy.getInstance().heartbeat();
                Thread.sleep(1000);
            } else {
                break;
            }
        }
    }

    @VisibleForTesting
    private static boolean checkCreateOrDropDatabaseRunning(DataSource dataSource, String token) throws SQLException {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "show full processlist where info like '%" + token + "%' and info not like 'show full processlist%'");
            if (rs.next()) {
                return true;
            }
        } catch (Throwable e) {
            log.error("failed in show processlist for create or drop database, tso: {}", token, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return false;
    }

    public static String tryAttachAsyncDdlHints(String sql, long execTime) {
        boolean enableAsyncDdl = getBoolean(RPL_ASYNC_DDL_ENABLED);
        long asyncDdlTimeThreshold = DynamicApplicationConfig.getLong(RPL_ASYNC_DDL_THRESHOLD_IN_SECOND);
        if (!enableAsyncDdl || execTime < asyncDdlTimeThreshold) {
            return sql;
        }

        try {
            SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
            if (stmt instanceof MySqlAnalyzeStatement) {
                return ASYNC_DDL_HINTS + sql;
            } else if (stmt instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) stmt;
                List<SQLAlterTableItem> items = sqlAlterTableStatement.getItems();
                if (items != null && !items.isEmpty()) {
                    boolean flag = false;
                    for (SQLAlterTableItem item : items) {
                        if (isAlterTableWithIndex(item) /*|| isAlterTableWithPartition(item)*/) {
                            flag = true;
                        } else {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        return ASYNC_DDL_HINTS + sql;
                    }
                }
            } else if (stmt instanceof SQLCreateIndexStatement) {
                return ASYNC_DDL_HINTS + sql;
            } else if (stmt instanceof SQLDropIndexStatement) {
                return ASYNC_DDL_HINTS + sql;
            }
        } catch (Throwable t) {
            log.error("try attach async ddl hints failed, {}.", sql, t);
        }

        return sql;
    }

    private static boolean isAlterTableWithIndex(SQLAlterTableItem item) {
        return item instanceof SQLAlterTableAddIndex || item instanceof SQLAlterTableDropIndex;
    }

    private static boolean isAlterTableWithPartition(SQLAlterTableItem item) {
        return item instanceof DrdsAlterTablePartition ||
            item instanceof DrdsSplitPartition ||
            item instanceof SQLAlterTableDropPartition ||
            item instanceof SQLAlterTableAddPartition ||
            item instanceof DrdsSplitHotKey ||
            item instanceof DrdsExtractHotKey ||
            item instanceof DrdsMergePartition ||
            item instanceof SQLAlterTableReorgPartition;
    }

    public static SqlContext processDdlSql(SqlContext sqlContext, String schema) {
        DdlResult result = DruidDdlParser.parse(sqlContext.getSql(), schema);
        return processDdlSql(sqlContext, schema, result);
    }

    public static SqlContext processDdlSql(SqlContext sqlContext, String schema, DdlResult result) {
        // use actual schemaName
        sqlContext.setDstSchema(schema);
        sqlContext.setDstTable(result == null ? null : result.getTableName());
        if (result != null) {
            switch (result.getType()) {
            case CREATEDB:
                if (!result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create database",
                        "create database if not exists"));
                }
                sqlContext.setDstSchema("");
                break;
            case DROPDB:
                if (!result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop database",
                        "drop database if exists"));
                }
                sqlContext.setDstSchema("");
                break;
            case CREATE:
                if (!result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create table",
                        "create table if not exists"));
                }
                break;
            case ERASE:
                if (!result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop table",
                        "drop table if exists"));
                }
                break;
            case OTHER:
                if (result.getSqlStatement() instanceof MySqlCreateUserStatement
                    && !result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create user",
                        "create user if not exists"));
                } else if (result.getSqlStatement() instanceof MySqlCreateRoleStatement
                    && !result.getHasIfExistsOrNotExists()) {
                    sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create role",
                        "create role if not exists"));
                } else if (result.getSqlStatement() instanceof SQLCallStatement) {
                    SQLCallStatement stmt = (SQLCallStatement) result.getSqlStatement();
                    if (SYNC_POINT_PROCEDURE_NAME.equals(stmt.getProcedureName().getSimpleName())) {
                        sqlContext.setSyncPoint(true);
                    }
                }
            default:
                break;
            }
        }
        return sqlContext;
    }

    public static boolean isDdl(DBMSEvent dbmsEvent) {
        return dbmsEvent instanceof DefaultQueryLog;
    }

    public static String getTso(String sql, Timestamp timestamp, String pos) {
        Scanner scanner = new Scanner(sql);
        while (scanner.hasNextLine()) {
            Matcher matcher = TSO_PATTERN.matcher(scanner.nextLine());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return String.valueOf(sql.hashCode()) + timestamp.hashCode() + pos.hashCode();
    }

    public static String getOriginSql(String sql) {
        String originSql = extractPolarxOriginSql(sql);
        if (StringUtils.isBlank(originSql)) {
            return "";
        }

        SQLStatement statement = parseSQLStatement(originSql);
        if (!(statement instanceof SQLCreateTableStatement)) {
            return originSql;
        }

        // remove _drds_implicit_id_
        SQLCreateTableStatement createTable = (SQLCreateTableStatement) statement;
        Iterator<SQLTableElement> iter = createTable.getTableElementList().iterator();
        while (iter.hasNext()) {
            SQLTableElement cur = iter.next();
            if (cur instanceof SQLColumnDefinition) {
                SQLColumnDefinition column = (SQLColumnDefinition) cur;
                if (StringUtils.equalsIgnoreCase(column.getColumnName(), RplConstants.POLARX_IMPLICIT_ID)
                    || StringUtils.equalsIgnoreCase(column.getColumnName(), RplConstants.RDS_IMPLICIT_ID)) {
                    iter.remove();
                }
            }

            // remove rds/_drds_implicit_id_ primary key
            if (cur instanceof MySqlPrimaryKey) {
                MySqlPrimaryKey pk = (MySqlPrimaryKey) cur;
                String columnName = pk.getColumns().get(0).getExpr().toString();
                if (StringUtils.equalsIgnoreCase(columnName, RplConstants.POLARX_IMPLICIT_ID)
                    || StringUtils.equalsIgnoreCase(columnName, RplConstants.RDS_IMPLICIT_ID)) {
                    iter.remove();
                }
            }
        }

        return createTable.toString();
    }

    public static Map<String, Object> getPolarxVariables(String sql) {
        Scanner scanner = new Scanner(sql);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith(CommonUtils.PRIVATE_DDL_POLARX_VARIABLES_PREFIX)) {
                return JSON.parseObject(line.substring(CommonUtils.PRIVATE_DDL_POLARX_VARIABLES_PREFIX.length()));
            }
        }
        return Maps.newHashMap();
    }

    public static DdlRouteMode getDdlRouteMode(String sql) {
        Scanner scanner = new Scanner(sql);
        while (scanner.hasNextLine()) {
            Matcher matcher = DDL_ROUTE_MODE_PATTERN.matcher(scanner.nextLine());
            if (matcher.find()) {
                return DdlRouteMode.valueOf(matcher.group(1));
            }
        }
        return DdlRouteMode.BROADCAST;
    }

    @SneakyThrows
    public static RplDdl prepareDdlLogAndWait(SqlContext sqlContext, String ddlTso, boolean needAlign,
                                              long alignMasterTaskId, String token) {
        try {
            if (!needAlign || alignMasterTaskId == TaskContext.getInstance().getTaskId()) {
                // record ddl in DB
                RplDdl ddl = new RplDdl();
                ddl.setFsmId(TaskContext.getInstance().getStateMachineId());
                ddl.setDdlTso(ddlTso);
                ddl.setServiceId(TaskContext.getInstance().getServiceId());
                ddl.setState(DdlState.NOT_START.name());
                ddl.setDdlStmt(sqlContext.getSql());
                ddl.setToken(token);
                ddl.setAsyncFlag(sqlContext.isAsyncDdl());
                ddl.setAsyncState(DdlState.NOT_START.name());
                ddl.setSchemaName(sqlContext.getDdlEventSchema());
                ddl.setTableName(sqlContext.getDstTable());
                ddl.setParallelSeq(sqlContext.getDdlParallelSeq());
                ddl.setTaskId(TaskContext.getInstance().getTaskId());
                DbTaskMetaManager.addDdl(ddl);
            }
        } catch (DataIntegrityViolationException e) {
            // do nothing
            // 通过uk来实现回溯后跳过已执行的ddl
            // 如果需要重新执行，需要reset slave清空ddl执行记录
        } catch (Throwable t) {
            log.error("insert rpl ddl to database error!", t);
            throw t;
        }

        if (needAlign) {
            try {
                RplDdlSub rplDdlSub = new RplDdlSub();
                rplDdlSub.setFsmId(TaskContext.getInstance().getStateMachineId());
                rplDdlSub.setDdlTso(ddlTso);
                rplDdlSub.setTaskId(TaskContext.getInstance().getTaskId());
                rplDdlSub.setServiceId(TaskContext.getInstance().getServiceId());
                rplDdlSub.setState(DdlState.NOT_START.name());
                rplDdlSub.setSchemaName(sqlContext.getDdlEventSchema());
                rplDdlSub.setParallelSeq(sqlContext.getDdlParallelSeq());
                DbTaskMetaManager.addRplDdlSub(rplDdlSub);
            } catch (DataIntegrityViolationException e) {
                // do nothing
                // 通过uk来实现回溯后跳过已执行的ddl
                // 如果需要重新执行，需要reset slave清空ddl执行记录
            } catch (Throwable t) {
                log.error("insert rpl ddl sub to database error!", t);
                throw t;
            }

            if (alignMasterTaskId == TaskContext.getInstance().getTaskId()) {
                long lastWaitTime = 0;
                while (true) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    Pair<Boolean, Set<Long>> pair = isAllDdlSubAligned(ddlTso);
                    if (pair.getKey()) {
                        log.info("all ddl sub tasks is align with ddl tso {}", ddlTso);

                        TransactionTemplate template = getObject("metaTransactionTemplate");
                        template.execute(t -> {
                            DbTaskMetaManager.updateDdlStateByTso(
                                TaskContext.getInstance().getStateMachineId(),
                                ddlTso,
                                DdlState.RUNNING,
                                DdlState.NOT_START
                            );
                            DbTaskMetaManager.updateDdlSubStateByServiceIdOnce(
                                TaskContext.getInstance().getServiceId(),
                                TaskContext.getInstance().getStateMachineId(),
                                ddlTso,
                                DdlState.RUNNING,
                                DdlState.NOT_START);
                            return null;
                        });
                        break;
                    } else {
                        if (System.currentTimeMillis() - lastWaitTime > 1000) {
                            log.info("wait for all ddl sub tasks to align with ddl tso {}, not aligned task ids : {}",
                                ddlTso, pair.getValue());
                            lastWaitTime = System.currentTimeMillis();
                        }
                        long waitAlignSleepTime = DynamicApplicationConfig.getLong(RPL_DDL_WAIT_ALIGN_INTERVAL_MILLS);
                        Thread.sleep(waitAlignSleepTime);
                    }
                }
            } else {
                long lastWaitTime = 0;
                while (true) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    RplDdl rplDdl = DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
                    if (rplDdl != null && (DdlState.valueOf(rplDdl.getState()) == DdlState.RUNNING ||
                        DdlState.valueOf(rplDdl.getState()) == DdlState.SUCCEED)) {
                        log.info("rpl ddl state is " + rplDdl.getState() + ", will go continue , with tso : " + ddlTso);
                        break;
                    } else {
                        if (System.currentTimeMillis() - lastWaitTime > 1000) {
                            log.info("wait for rpl ddl state to running or succeed, current state is {}, with tso {}",
                                rplDdl == null ? DdlState.NOT_START : rplDdl.getState(), ddlTso);
                            lastWaitTime = System.currentTimeMillis();
                        }
                        long waitAlignSleepTime = DynamicApplicationConfig.getLong(RPL_DDL_WAIT_ALIGN_INTERVAL_MILLS);
                        Thread.sleep(waitAlignSleepTime);
                    }
                }
            }
        } else {
            DbTaskMetaManager.updateDdlStateByTso(
                TaskContext.getInstance().getStateMachineId(),
                ddlTso,
                DdlState.RUNNING,
                DdlState.NOT_START
            );
        }

        return DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
    }

    @SneakyThrows
    public static void waitDdlCompleted(String ddlTso, boolean asyncDdl) {
        long lastWaitTime = 0;
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            RplDdl rplDdl = DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
            if (DdlState.valueOf(rplDdl.getState()) == DdlState.SUCCEED ||
                (asyncDdl && DdlState.valueOf(rplDdl.getAsyncState()) == DdlState.RUNNING
                    || DdlState.valueOf(rplDdl.getAsyncState()) == DdlState.SUCCEED)) {
                break;
            } else {
                if (System.currentTimeMillis() - lastWaitTime > 1000) {
                    log.info("wait for rpl ddl state to succeed , current state is " + rplDdl.getState()
                        + " , with tso " + ddlTso);
                    lastWaitTime = System.currentTimeMillis();
                }
                long waitAlignSleepTime = DynamicApplicationConfig.getLong(RPL_DDL_WAIT_ALIGN_INTERVAL_MILLS);
                Thread.sleep(waitAlignSleepTime);
            }
        }
    }

    @SneakyThrows
    public static void executeDdl(DataSource dataSource, SqlContext sqlContext, AtomicBoolean firstDdl,
                                  String tso, RplDdl rplDdl, boolean needAlign, DbMetaCache dbMetaCache,
                                  boolean asyncDdl) {
        boolean retry = false;
        int count = 0;
        int retryMaxCount = DynamicApplicationConfig.getInt(RPL_DDL_RETRY_MAX_COUNT);
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            if (sqlContext.isSyncPoint()) {
                processSyncPoint(dbMetaCache.getDataSource("__cdc__"), tso);
                markDdlSucceed(tso, false);
                return;
            }

            boolean res;
            if (asyncDdl) {
                res = executeDdlInternalAsync(dataSource, sqlContext, firstDdl, tso, rplDdl, needAlign, retry);
            } else {
                res = executeDdlInternal(dataSource, sqlContext, firstDdl, tso, rplDdl,
                    needAlign, dbMetaCache, retry);
            }

            count++;
            if (res) {
                return;
            }

            if (DynamicApplicationConfig.getBoolean(RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR)) {
                if (isMissLocalPartitionError(sqlContext.getException())) {
                    log.warn("skip miss local partition error for  : {}, tso : {}", sqlContext.getSql(), tso);
                    markDdlSucceed(tso, asyncDdl);
                    return;
                }
            }

            if (count < retryMaxCount) {
                retry = true;
                Thread.sleep(DynamicApplicationConfig.getInt(RPL_DDL_RETRY_INTERVAL_MILLS));
            } else {
                // 这里省事没有把真正的下层Exception包进来，直接去看日志也行
                throw new PolardbxException("ddl execution exceeds max retry times");
            }
        }
    }

    public static boolean isMissLocalPartitionError(Throwable e) {
        return e != null && e.getMessage().matches(".*server error by local partition \\w+ doesn't exist.*");
    }

    private static boolean executeDdlInternalAsync(DataSource dataSource, SqlContext sqlContext,
                                                   AtomicBoolean firstDdlProcessing, String tso,
                                                   RplDdl rplDdl, boolean needAlign, boolean retry) {
        boolean isRunning = false;
        boolean isSucceed = false;
        if (((firstDdlProcessing != null && firstDdlProcessing.compareAndSet(true, false)) || retry)) {
            isRunning = checkIfDdlRunning(dataSource, rplDdl.getToken()) != null;
            isSucceed = !isRunning && checkIfDdlSucceed(
                dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
        }

        if (isSucceed) {
            markDdlSucceed(tso, true);
        } else {
            if (!isRunning) {
                try (Connection conn = dataSource.getConnection()) {
                    execUpdate(conn, sqlContext);
                } catch (Exception e) {
                    log.error("ddl execute runs into exception: ", e);
                    sqlContext.exception = e;
                    return false;
                }
            }
            // 有幂等控制，不管是否已经处在Running状态，都mark一下
            markAsyncDdlRunning(tso);
            // async ddl do not memory rt and apply count
            AsyncDdlMonitor.getInstance().submitNewDdl(rplDdl);
        }
        return true;
    }

    @SneakyThrows
    private static void processSyncPoint(DataSource dataSource, String primary_tso) {
        log.info("prepare to process sync point. primary tso:{}", primary_tso);

        // 等待心跳将DN上的tso推高
        pushDNLocalSeq(dataSource);

        String secondary_tso = null;
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select tso_timestamp()")) {
            if (rs.next()) {
                secondary_tso = rs.getString(1);
            }
            log.info("success to get tso from gms, secondary tso:{}", secondary_tso);
        }

        if (secondary_tso != null) {
            final RplSyncPointService service = getObject(RplSyncPointService.class);
            service.insert(primary_tso.substring(0, secondary_tso.length()), secondary_tso);
        }
    }

    @SneakyThrows
    private static void pushDNLocalSeq(DataSource dataSource) {
        long now = System.currentTimeMillis();
        String nowFormat = DateFormatUtils.format(now, "yyyy-MM-dd HH:mm:ss.SSS");
        final String sql = String.format(
            "replace into `__cdc__`.`__cdc_heartbeat__`(id, sname, gmt_modified) values(1, 'heartbeat', '%s')",
            nowFormat);
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {
            stmt.execute("SET TRANSACTION_POLICY = TSO");
            stmt.execute(sql);
        }
    }

    @SneakyThrows
    private static boolean executeDdlInternal(DataSource dataSource, SqlContext sqlContext,
                                              AtomicBoolean firstDdlProcessing, String tso,
                                              RplDdl rplDdl, boolean needAlign,
                                              DbMetaCache dbMetaCache, boolean retry) {
        // check if already running in target
        boolean isCreateOrDropDatabase = isCreateOrDropDatabase(rplDdl.getDdlStmt());
        boolean isRunning = false;
        boolean isSucceed = false;
        if (((firstDdlProcessing != null && firstDdlProcessing.compareAndSet(true, false)) || retry)) {
            if (isCreateOrDropDatabase) {
                tryWaitCreateOrDropDatabase(dataSource, rplDdl.getToken(), 0);
            } else {
                isRunning = checkIfDdlRunning(dataSource, rplDdl.getToken()) != null;
                isSucceed = !isRunning && checkIfDdlSucceed(
                    dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
            }
        }

        boolean res = false;
        if (isRunning) {
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                DdlJobInfo ddlJobInfo = checkIfDdlRunning(dataSource, rplDdl.getToken());
                if (ddlJobInfo != null) {
                    tryContinueDdl(dataSource, ddlJobInfo, tso);
                    StatisticalProxy.getInstance().heartbeat();
                    Thread.sleep(1000);
                } else {
                    res = checkIfDdlSucceed(dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
                    break;
                }
            }
        } else if (isSucceed) {
            res = true;
        } else {
            try (Connection conn = dataSource.getConnection()) {
                long startTime = System.currentTimeMillis();
                execUpdate(conn, sqlContext);
                long endTime = System.currentTimeMillis();
                StatMetrics.getInstance().addApplyCount(1);
                StatMetrics.getInstance().addRt(endTime - startTime);
                res = true;
            } catch (Exception e) {
                log.error("ddl execute runs into exception: ", e);
                sqlContext.exception = e;
            }
        }
        log.info("ddlApply end, result: {}, schema: {}, sql: {}, tso: {}", res, sqlContext.getDstSchema(),
            sqlContext.getSql(), tso);
        if (res) {
            tryRefreshMetaInfo(rplDdl, sqlContext, dbMetaCache);
            markDdlSucceed(tso, false);
        }
        return res;
    }

    private static void tryContinueDdl(DataSource dataSource, DdlJobInfo ddlJobInfo, String tso) {
        if (DDL_STATE_PAUSED.equals(ddlJobInfo.getState())) {
            Connection conn = null;
            Statement stmt = null;
            ResultSet rs = null;

            try {
                conn = dataSource.getConnection();
                stmt = conn.createStatement();
                stmt.executeUpdate(String.format(SQL_CONTINUE_DDL, ddlJobInfo.getJobId()));
            } catch (Throwable e) {
                log.error("try continue paused ddl failed , tso {}, ddl job info {}!", tso, ddlJobInfo, e);
            } finally {
                DataSourceUtil.closeQuery(rs, stmt, conn);
            }
        }
    }

    public static void tryRefreshMetaInfo(RplDdl rplDdl, SqlContext sqlContext, DbMetaCache dbMetaCache) {
        if (isDropDatabase(rplDdl.getDdlStmt())) {
            dbMetaCache.removeDataSource(sqlContext.getDdlEventSchema());
        } else {
            // refresh target table meta
            if (StringUtils.isNotBlank(sqlContext.getDdlEventSchema()) && StringUtils
                .isNotBlank(sqlContext.getDstTable())) {
                dbMetaCache.refreshTableInfo(sqlContext.getDdlEventSchema(), sqlContext.getDstTable());
            }
        }
    }

    private static Pair<Boolean, Set<Long>> isAllDdlSubAligned(String ddlTso) {
        List<RplTask> rplTasks = DbTaskMetaManager.listTaskByService(
            TaskContext.getInstance().getServiceId());
        List<RplDdlSub> rplDdlSubs = DbTaskMetaManager.listDdlSubByServiceIdOnce(
            TaskContext.getInstance().getServiceId(), TaskContext.getInstance().getStateMachineId(), ddlTso);

        Set<Long> set1 = rplTasks.stream().map(RplTask::getId).collect(Collectors.toSet());
        Set<Long> set2 = rplDdlSubs.stream().map(RplDdlSub::getTaskId).collect(Collectors.toSet());
        Set<Long> set3 = Sets.difference(set1, set2);

        return Pair.of(set1.equals(set2), set3);
    }

    public static void markDdlSucceed(String tso, boolean asyncDdl) {
        // update rpl ddl log state
        TransactionTemplate template = getObject("metaTransactionTemplate");
        template.execute(t -> {
            DbTaskMetaManager.updateDdlStateByTso(TaskContext.getInstance().getStateMachineId(),
                tso, DdlState.SUCCEED, DdlState.RUNNING);

            // 如果不需要对齐，则更新条数为0
            DbTaskMetaManager.updateDdlSubStateByServiceIdOnce(
                TaskContext.getInstance().getServiceId(), TaskContext.getInstance().getStateMachineId(),
                tso, DdlState.SUCCEED, DdlState.RUNNING);

            if (asyncDdl) {
                DbTaskMetaManager.updateAsyncDdlStateByTso(TaskContext.getInstance().getStateMachineId(),
                    tso, DdlState.SUCCEED, DdlState.RUNNING);
            }
            return null;
        });
    }

    public static Pair<Boolean, Boolean> tryRemoveColumnarIndex(SQLStatement statement, TableMeta tableMeta) {
        if (statement instanceof SQLCreateTableStatement) {
            SQLCreateTableStatement createTableStatement = (SQLCreateTableStatement) statement;
            return tryRemoveColumnarIndexForCreateTable(createTableStatement);

        } else if (statement instanceof SQLCreateIndexStatement) {
            SQLCreateIndexStatement createIndexStatement = (SQLCreateIndexStatement) statement;
            return tryRemoveColumnarIndexForCreateIndex(createIndexStatement);

        } else if (statement instanceof SQLDropIndexStatement) {
            SQLDropIndexStatement dropIndexStatement = (SQLDropIndexStatement) statement;
            return tryRemoveColumnarIndexForDropIndex(dropIndexStatement, tableMeta);

        } else if (statement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement alterTableStatement = (SQLAlterTableStatement) statement;
            return tryRemoveColumnarIndexForAlterTable(alterTableStatement, tableMeta);
        }

        return Pair.of(false, true);
    }

    public static boolean isCciDdl(String ddl) {
        Scanner scanner = new Scanner(ddl);
        while (scanner.hasNextLine()) {
            String next = scanner.nextLine();
            if (next.startsWith(CommonUtils.PRIVATE_DDL_DDL_TYPES_PREFIX) && next.contains("CCI")) {
                return true;
            }
        }
        return false;
    }

    private static Pair<Boolean, Boolean> tryRemoveColumnarIndexForAlterTable(
        SQLAlterTableStatement alterTableStatement, TableMeta tableMeta) {

        boolean hasColumnarIndex = false;
        Iterator<SQLAlterTableItem> iterator = alterTableStatement.getItems().iterator();

        while (iterator.hasNext()) {
            SQLAlterTableItem item = iterator.next();
            if (item instanceof SQLAlterTableAddIndex) {
                SQLAlterTableAddIndex alterTableAddIndex = (SQLAlterTableAddIndex) item;
                if (alterTableAddIndex.getIndexDefinition() != null &&
                    alterTableAddIndex.getIndexDefinition().isColumnar()) {
                    hasColumnarIndex = true;
                    iterator.remove();
                }
            } else if (item instanceof SQLAlterTableAddConstraint) {
                SQLAlterTableAddConstraint addConstraint = (SQLAlterTableAddConstraint) item;
                SQLConstraint sqlConstraint = addConstraint.getConstraint();

                if (sqlConstraint instanceof MySqlUnique) {
                    MySqlUnique mySqlUnique = (MySqlUnique) sqlConstraint;
                    if (mySqlUnique.isColumnar()) {
                        hasColumnarIndex = true;
                        iterator.remove();
                    }
                } else if (sqlConstraint instanceof MySqlTableIndex) {
                    MySqlTableIndex mySqlTableIndex = (MySqlTableIndex) sqlConstraint;
                    if (mySqlTableIndex.isColumnar()) {
                        hasColumnarIndex = true;
                        iterator.remove();
                    }
                }
            } else if (item instanceof SQLAlterTableDropIndex) {
                SQLAlterTableDropIndex dropIndex = (SQLAlterTableDropIndex) item;
                String indexName = normalize(dropIndex.getIndexName().getSimpleName());
                if (isColumnarIndex(tableMeta, indexName)) {
                    hasColumnarIndex = true;
                    iterator.remove();
                }
            } else if (item instanceof SQLAlterTableDropKey) {
                SQLAlterTableDropKey dropKey = (SQLAlterTableDropKey) item;
                String indexName = normalize(dropKey.getKeyName().getSimpleName());
                if (isColumnarIndex(tableMeta, indexName)) {
                    hasColumnarIndex = true;
                    iterator.remove();
                }
            } else if (item instanceof MySqlAlterTableAlterFullTextIndex) {
                MySqlAlterTableAlterFullTextIndex alterFullTextIndex = (MySqlAlterTableAlterFullTextIndex) item;
                String indexName = normalize(alterFullTextIndex.getIndexName().getSimpleName());
                if (isColumnarIndex(tableMeta, indexName)) {
                    hasColumnarIndex = true;
                    iterator.remove();
                }
            }
        }

        return Pair.of(hasColumnarIndex, true);
    }

    private static Pair<Boolean, Boolean> tryRemoveColumnarIndexForCreateTable(
        SQLCreateTableStatement sqlCreateTableStatement) {

        boolean hasColumnarIndex = false;
        Iterator<SQLTableElement> iterator = sqlCreateTableStatement.getTableElementList().iterator();

        while (iterator.hasNext()) {
            SQLTableElement element = iterator.next();
            if (element instanceof MySqlTableIndex) {
                MySqlTableIndex mySqlTableIndex = (MySqlTableIndex) element;
                if (mySqlTableIndex.isColumnar()) {
                    hasColumnarIndex = true;
                    iterator.remove();
                }
            } else if (element instanceof MySqlKey) {
                if (element instanceof MySqlPrimaryKey) {
                    MySqlPrimaryKey primaryKey = (MySqlPrimaryKey) element;
                    if (primaryKey.getIndexDefinition() != null && primaryKey.getIndexDefinition().isColumnar()) {
                        hasColumnarIndex = true;
                        iterator.remove();
                    }
                } else if (element instanceof MySqlUnique) {
                    MySqlUnique mySqlUnique = (MySqlUnique) element;
                    if (mySqlUnique.isColumnar()) {
                        hasColumnarIndex = true;
                        iterator.remove();
                    }
                } else {
                    MySqlKey mySqlKey = (MySqlKey) element;
                    if (mySqlKey.getIndexDefinition() != null && mySqlKey.getIndexDefinition().isColumnar()) {
                        hasColumnarIndex = true;
                        iterator.remove();
                    }
                }
            }
        }
        return Pair.of(hasColumnarIndex, true);
    }

    private static Pair<Boolean, Boolean> tryRemoveColumnarIndexForCreateIndex(
        SQLCreateIndexStatement createIndexStatement) {
        return createIndexStatement.isColumnar() ? Pair.of(true, false) :
            Pair.of(false, true);
    }

    private static Pair<Boolean, Boolean> tryRemoveColumnarIndexForDropIndex(
        SQLDropIndexStatement dropIndexStatement, TableMeta tableMeta) {
        String indexName = normalize(dropIndexStatement.getIndexName().getSimpleName());
        return isColumnarIndex(tableMeta, indexName) ? Pair.of(true, false) : Pair.of(false, true);
    }

    private static boolean isColumnarIndex(TableMeta tableMeta, String indexName) {
        if (tableMeta != null) {
            return tableMeta.getIndexes().values().stream()
                .anyMatch(i -> StringUtils.equalsIgnoreCase(i.getIndexName(), indexName) && i.isColumnar());
        }
        return false;
    }

    private static void markAsyncDdlRunning(String tso) {
        DbTaskMetaManager.updateAsyncDdlStateByTso(TaskContext.getInstance().getStateMachineId(), tso,
            DdlState.RUNNING, DdlState.NOT_START);
    }

    public static boolean isCreateOrDropDatabase(String ddlSql) {
        SQLStatement stmt = parseSQLStatement(ddlSql);
        return (stmt instanceof SQLCreateDatabaseStatement) || (stmt instanceof SQLDropDatabaseStatement);
    }

    private static boolean isDropDatabase(String ddlSql) {
        SQLStatement stmt = parseSQLStatement(ddlSql);
        return stmt instanceof SQLDropDatabaseStatement;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class DdlJobInfo {
        Long jobId;
        String state;
    }
}
