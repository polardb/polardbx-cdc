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

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_RETRY_INTERVAL_MILLS;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_RETRY_MAX_COUNT;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_DDL_PREFIX;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_DDL_ROUTE_PREFIX;
import static com.aliyun.polardbx.binlog.util.CommonUtils.PRIVATE_DDL_TSO_PREFIX;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * @author shicai.xsc 2021/4/5 23:22
 * @since 5.0.0.0
 */
@Slf4j
public class DdlHelper {

    private static final Pattern TSO_PATTERN = Pattern.compile(PRIVATE_DDL_TSO_PREFIX + "([\\d]+)");
    private static final Pattern ORIGIN_SQL_PATTERN = Pattern.compile(PRIVATE_DDL_DDL_PREFIX + "([\\W\\w]+)");
    private static final Pattern DDL_ROUTE_MODE_PATTERN =
        Pattern.compile(PRIVATE_DDL_DDL_ROUTE_PREFIX + "([\\W\\w]+)");

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
        String originSql = null;
        Scanner scanner = new Scanner(sql);
        while (scanner.hasNextLine()) {
            String str = scanner.nextLine();
            Matcher matcher = ORIGIN_SQL_PATTERN.matcher(str);
            if (matcher.find()) {
                originSql = matcher.group(1);
                break;
            }
        }
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
    public static RplDdl prepareDdlLogAndWait(String ddlTso, String sql, boolean needAlign, long alignMasterTaskId,
                                              String token) {
        try {
            // record ddl in DB
            RplDdl ddl = new RplDdl();
            ddl.setFsmId(TaskContext.getInstance().getStateMachineId());
            ddl.setDdlTso(ddlTso);
            ddl.setServiceId(TaskContext.getInstance().getServiceId());
            ddl.setState(DdlState.NOT_START.getValue());
            ddl.setDdlStmt(sql);
            ddl.setToken(token);
            DbTaskMetaManager.addDdl(ddl);
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
                rplDdlSub.setState(DdlState.NOT_START.getValue());
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
                    Pair<Boolean, Set<Long>> pair = isAllDdlSubAligned(ddlTso);
                    if (pair.getKey()) {
                        log.info("all ddl sub tasks is align with ddl tso {}", ddlTso);

                        TransactionTemplate template = getObject("metaTransactionTemplate");
                        template.execute(t -> {
                            DbTaskMetaManager.updateDdlStateByTso(
                                TaskContext.getInstance().getStateMachineId(),
                                ddlTso,
                                DdlState.RUNNING.getValue(),
                                DdlState.NOT_START.getValue()
                            );
                            DbTaskMetaManager.updateDdlSubStateByServiceIdOnce(
                                TaskContext.getInstance().getServiceId(),
                                TaskContext.getInstance().getStateMachineId(),
                                ddlTso,
                                DdlState.RUNNING.getValue(),
                                DdlState.NOT_START.getValue());
                            return null;
                        });
                        break;
                    } else {
                        if (System.currentTimeMillis() - lastWaitTime > 1000) {
                            log.info("wait for all ddl sub tasks to align with ddl tso {}, not aligned task ids : {}",
                                ddlTso, pair.getValue());
                            lastWaitTime = System.currentTimeMillis();
                        }
                        Thread.sleep(10);
                    }
                }
            } else {
                long lastWaitTime = 0;
                while (true) {
                    RplDdl rplDdl = DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
                    if (rplDdl.getState() == DdlState.RUNNING.getValue() ||
                        rplDdl.getState() == DdlState.SUCCEED.getValue()) {
                        log.info("rpl ddl state is " + DdlState.from(rplDdl.getState())
                            + ", will go continue , with tso : " + ddlTso);
                        break;
                    } else {
                        if (System.currentTimeMillis() - lastWaitTime > 1000) {
                            log.info("wait for rpl ddl state to running or succeed, current state is " +
                                DdlState.from(rplDdl.getState()) + " , with tso : " + ddlTso);
                            lastWaitTime = System.currentTimeMillis();
                        }
                        Thread.sleep(10);
                    }
                }
            }
        } else {
            DbTaskMetaManager.updateDdlStateByTso(
                TaskContext.getInstance().getStateMachineId(),
                ddlTso,
                DdlState.RUNNING.getValue(),
                DdlState.NOT_START.getValue()
            );
        }

        return DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
    }

    @SneakyThrows
    public static void waitDdlCompleted(String ddlTso) {
        while (true) {
            RplDdl rplDdl = DbTaskMetaManager.getDdl(TaskContext.getInstance().getStateMachineId(), ddlTso);
            if (rplDdl.getState() == DdlState.SUCCEED.getValue()) {
                break;
            } else {
                log.info("wait for rpl ddl state to succeed , current state is " + DdlState.from(rplDdl.getState())
                    + " , with tso " + ddlTso);
                Thread.sleep(1000);
            }
        }
    }

    @SneakyThrows
    public static void executeDdl(DataSource dataSource, SqlContext sqlContext, AtomicBoolean firstDdl,
                                  String tso, RplDdl rplDdl, boolean needAlign, DbMetaCache dbMetaCache) {
        boolean retry = false;
        int count = 0;
        int retryMaxCount = DynamicApplicationConfig.getInt(RPL_DDL_RETRY_MAX_COUNT);
        while (true) {
            boolean res = executeDdlInternal(dataSource, sqlContext, firstDdl, tso, rplDdl,
                needAlign, dbMetaCache, retry);
            count++;
            if (res) {
                return;
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

    @SneakyThrows
    private static boolean executeDdlInternal(DataSource dataSource, SqlContext sqlContext,
                                              AtomicBoolean firstDdlProcessing, String tso,
                                              RplDdl rplDdl, boolean needAlign,
                                              DbMetaCache dbMetaCache, boolean retry) {
        // check if already running in target
        boolean isCreateOrDropDatabase = isCreateOrDropDatabase(rplDdl.getDdlStmt());
        boolean isRunning = false;
        boolean isSucceed = false;
        if ((firstDdlProcessing.compareAndSet(true, false) || retry) && !isCreateOrDropDatabase) {
            isRunning = ApplyHelper.checkIfDdlRunning(dataSource, rplDdl.getToken());
            isSucceed = !isRunning && ApplyHelper.checkIfDdlSucceed(
                dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
        }

        boolean res;
        if (isRunning) {
            while (true) {
                if (ApplyHelper.checkIfDdlRunning(dataSource, rplDdl.getToken())) {
                    StatisticalProxy.getInstance().heartbeat();
                    Thread.sleep(1000);
                } else {
                    res = ApplyHelper.checkIfDdlSucceed(dataSource, rplDdl.getToken(), rplDdl.getGmtCreated());
                    break;
                }
            }
        } else if (isSucceed) {
            res = true;
        } else {
            try {
                ApplyHelper.execUpdate(dataSource, sqlContext);
                res = true;
            } catch (Exception e) {
                log.error("ddl execute runs into exception: ", e);
                res = false;
            }
        }
        log.info("ddlApply end, result: {}, schema: {}, sql: {}, tso: {}", res, sqlContext.getDstSchema(),
            sqlContext.getSql(), tso);
        if (res) {
            // refresh target table meta
            if (StringUtils.isNotBlank(sqlContext.getDstSchema()) && StringUtils
                .isNotBlank(sqlContext.getDstTable())) {
                dbMetaCache.refreshTableInfo(sqlContext.getDstSchema(), sqlContext.getDstTable());
            }
            markDdlSucceed(needAlign, tso);
        }
        return res;
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

    private static void markDdlSucceed(boolean needAlign, String tso) {
        // update rpl ddl log state
        TransactionTemplate template = getObject("metaTransactionTemplate");
        template.execute(t -> {
            DbTaskMetaManager.updateDdlStateByTso(TaskContext.getInstance().getStateMachineId(),
                tso, DdlState.SUCCEED.getValue(), DdlState.RUNNING.getValue());

            if (needAlign) {
                DbTaskMetaManager.updateDdlSubStateByServiceIdOnce(
                    TaskContext.getInstance().getServiceId(), TaskContext.getInstance().getStateMachineId(),
                    tso, DdlState.SUCCEED.getValue(), DdlState.RUNNING.getValue());
            }
            return null;
        });
    }

    private static boolean isCreateOrDropDatabase(String ddlSql) {
        SQLStatement stmt = parseSQLStatement(ddlSql);

        return (stmt instanceof SQLCreateDatabaseStatement) || (stmt instanceof SQLDropDatabaseStatement);
    }

}
