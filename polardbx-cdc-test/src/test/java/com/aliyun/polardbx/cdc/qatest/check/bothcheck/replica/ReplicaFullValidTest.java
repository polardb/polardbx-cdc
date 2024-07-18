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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskStage;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskState;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.rmi.UnexpectedException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yudong
 * @since 2023/11/20 15:38
 **/
@Slf4j
public class ReplicaFullValidTest extends RplBaseTestCase {

    private static final String CHECK_REPLIA_TABLE_SQL_FORMAT = "CHECK REPLICA TABLE `%s`.`%s`";

    @Test
    public void baseTest() {
        sendTokenAndWait(CheckParameter.builder().build());

        List<String> databaseList = getDatabaseList();
        String checkDbBlackList = PropertiesUtil.getCdcCheckDbBlackList();
        if (StringUtils.isNotBlank(checkDbBlackList)) {
            String[] blackList = StringUtils.split(
                StringUtils.lowerCase(checkDbBlackList), ";");
            for (String db : blackList) {
                databaseList.remove(db);
            }
        }

        log.info("check db list:{}", databaseList);

        List<Pair<String, String>> tableList = new ArrayList<>();
        for (String db : databaseList) {
            List<String> tables = getTableList(db);
            for (String tb : tables) {
                if (!filterTable(Pair.of(db, tb))) {
                    tableList.add(Pair.of(db, tb));
                }
            }
        }

        log.info("check table list:{}", tableList);

        // 提交全量校验任务
        try (Connection conn = getCdcSyncDbConnection();
            Statement stmt = conn.createStatement()) {
            for (Pair<String, String> dbAndTb : tableList) {
                try {
                    String checkSql =
                        String.format(CHECK_REPLIA_TABLE_SQL_FORMAT, escape(dbAndTb.getLeft()),
                            escape(dbAndTb.getRight()));
                    log.info("exec check sql:{}", checkSql);
                    stmt.execute(checkSql);
                } catch (Exception e) {
                    log.error("check replica table failed!", e);
                }
            }
        } catch (Exception e) {
            log.error("check replica table error!", e);
        }

        List<String> checkFailedTables = new ArrayList<>();
        try (Connection conn = getCdcSyncDbConnection()) {
            for (Pair<String, String> table : tableList) {
                boolean b = checkStatus(conn, table.getLeft(), table.getRight());
                if (!b) {
                    checkFailedTables.add(table.getLeft() + "." + table.getRight());
                }
            }
        } catch (Exception e) {
            log.error("check status exception!", e);
            throw new RuntimeException("check status failed!", e);
        }

        if (!checkFailedTables.isEmpty()) {
            log.info("check failed tables:{}", checkFailedTables);
        }

        Assert.assertTrue(checkFailedTables.isEmpty());
    }

    private boolean checkStatus(Connection conn, String db, String table) {
        String sqlPattern = "CHECK REPLICA TABLE `%s`.`%s` SHOW PROGRESS";
        String checkSql = String.format(sqlPattern, escape(db), escape(table));

        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();

        boolean res;
        try {
            res = retryer.call(() -> {
                long start = System.currentTimeMillis();
                Statement stmt = conn.createStatement();
                while (System.currentTimeMillis() - start <= 60 * 1000) {
                    try (ResultSet resultSet = stmt.executeQuery(checkSql)) {
                        if (resultSet.next()) {
                            String stage = resultSet.getString("STAGE");
                            String status = resultSet.getString("STATUS");
                            if (!isFinished(stage, status)) {
                                Thread.sleep(1000);
                            } else {
                                String summary = resultSet.getString("SUMMARY");
                                if (!isSuccess(summary)) {
                                    log.info("table {}.{} check failed. summary:{}", db, table, summary);
                                    return false;
                                } else {
                                    return true;
                                }
                            }
                        } else {
                            throw new UnexpectedException("check replica return no result!");
                        }
                    }
                }

                throw new UnexpectedException("check replica timeout!");
            });
        } catch (Exception e) {
            log.error("retry check status failed! db {}, tb {}", db, table, e);
            res = false;
        }
        return res;
    }

    @SneakyThrows
    private List<String> getDatabaseList() {
        try (Connection conn = getCdcSyncDbConnection()) {
            return JdbcUtil.showDatabases(conn);
        }
    }

    @SneakyThrows
    private List<String> getTableList(String db) {
        try (Connection conn = getCdcSyncDbConnection()) {
            return JdbcUtil.showTables(conn, db);
        }
    }

    private boolean isFinished(String stage, String status) {
        return StringUtils.equalsIgnoreCase(ReplicaFullValidTaskStage.CHECK.name(), stage) &&
            (StringUtils.equalsIgnoreCase(ReplicaFullValidTaskState.FINISHED.name(), status)
                || StringUtils.equalsIgnoreCase(ReplicaFullValidTaskState.ERROR.name(), status));
    }

    private boolean isSuccess(String summary) {
        return StringUtils.equalsIgnoreCase("SUCCESS", summary);
    }

    private boolean filterTable(Pair<String, String> tablePair) {
        String database = tablePair.getKey();
        String table = tablePair.getValue();
        String fullTable = StringUtils.lowerCase(database + "." + table);

        String checkTableBlackList = PropertiesUtil.getCdcCheckTableBlackList();
        if (StringUtils.isNotBlank(checkTableBlackList)) {
            String[] patterns = StringUtils.split(
                StringUtils.lowerCase(checkTableBlackList), ";");
            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(fullTable);
                if (matcher.find()) {
                    log.info("table is filtered, table name is {}, "
                        + "with pattern {}", fullTable, patternStr);
                    return true;
                }
            }
        }

        try {
            if (hasNoPrimaryKeys(tablePair.getLeft(), tablePair.getRight())) {
                log.info("table is filtered, table name is {}.{}, no primary key", tablePair.getLeft(),
                    tablePair.getRight());
                return true;
            }
        } catch (SQLException e) {
            log.error("query primary key meet exception", e);
            return true;
        }

        return false;
    }

    private boolean hasNoPrimaryKeys(String db, String table) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            List<String> pks = JdbcUtil.getPrimaryKeyNames(conn, db, table);
            return CollectionUtils.isEmpty(pks);
        }
    }

}
