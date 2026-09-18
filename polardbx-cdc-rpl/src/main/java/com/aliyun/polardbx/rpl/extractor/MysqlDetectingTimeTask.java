/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

@Slf4j
public class MysqlDetectingTimeTask extends TimerTask {
    public static final String DEFAULT_HEARTBEAT_TABLE = "__system__mysql__heartbeat__";
    public static final String DEFAULT_HEARTBEAT_DATABASE = "__polardbx2__";

    private boolean reconnect = false;
    private boolean createHeartbeatTable;
    private MysqlConnection mysqlConnection;
    private String detectingSQL = "replace into %s(id,gmt_create) values(1,NOW())";
    private String heartbeatTableName = DEFAULT_HEARTBEAT_TABLE;
    private String heartbeatDatabaseName = DEFAULT_HEARTBEAT_DATABASE;
    private final boolean isLabEnv;
    private String serverId;

    public MysqlDetectingTimeTask(MysqlConnection mysqlConnection, boolean createHeartbeatTable) {
        this.mysqlConnection = mysqlConnection;
        this.createHeartbeatTable = createHeartbeatTable;
        this.isLabEnv = getBoolean(ConfigKeys.IS_LAB_ENV);
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public void createHeartbeatDatabaseAndTable(Statement stmt) throws SQLException {
        // 1、检查heartbeatDatabaseName是否有值，有则检查是否创建心跳库，没有创建一个。
        String createDatabaseSql = String.format("create database if not exists %s", heartbeatDatabaseName);
        log.info("create heartbeat database sql : " + createDatabaseSql);
        stmt.execute(createDatabaseSql);

        String createHeartbeatSql = String.format(
            "create table if not exists `%s`.`%s`(id int(4) AUTO_INCREMENT, gmt_create timestamp, PRIMARY KEY (`id`));",
            heartbeatDatabaseName, heartbeatTableName);
        log.info("create heartbeat table sql : " + createHeartbeatSql);
        stmt.execute(createHeartbeatSql);
    }

    public void buildDetectedSqlBefore() {
        // 构造更新心跳表的 detectingSQL
        if (detectingSQL.indexOf("%s") != -1) {
            String fullHeartbeatTableName = String.format("`%s`.`%s`", heartbeatDatabaseName, heartbeatTableName);
            detectingSQL = String.format(detectingSQL, fullHeartbeatTableName);
            log.info("detecting sql : " + detectingSQL);
        }
    }

    public boolean findIfExists(Statement stmt) throws SQLException {
        // Executing another query on the same Statement closes its current ResultSet. Materialize the
        // database names first, otherwise a missing heartbeat table in the first database aborts the scan.
        List<String> databases = new ArrayList<>();
        try (ResultSet resultSet = stmt.executeQuery("show databases")) {
            while (resultSet.next()) {
                databases.add(resultSet.getString(1));
            }
        }
        for (String databaseName : databases) {
            // 忽略系统表
            if (databaseName.equalsIgnoreCase("information_schema") ||
                databaseName.equalsIgnoreCase("mysql") ||
                databaseName.equalsIgnoreCase("performance_schema") ||
                databaseName.equalsIgnoreCase("sys")) {
                continue;
            }

            // 检查这个库中是否有心跳表
            String tableName = StringUtils.isEmpty(heartbeatTableName) ? DEFAULT_HEARTBEAT_TABLE : heartbeatTableName;
            try {
                String checkTableSql = String.format("select 1 from `%s`.`%s` limit 1", databaseName, tableName);
                try (ResultSet ignored = stmt.executeQuery(checkTableSql)) {
                    heartbeatDatabaseName = databaseName;
                    heartbeatTableName = tableName;
                    return true;
                }
            } catch (SQLException e) {
                // 表不存在，继续查找
            }
        }
        return false;
    }

    public void findAndCreateIfNotExists(Statement stmt) throws SQLException {
        ResultSet resultSet = stmt.executeQuery("show databases");
        String defaultDatabase = null;
        while (resultSet.next()) {
            String databaseName = resultSet.getString(1);
            // 忽略系统表
            if (databaseName.equalsIgnoreCase("information_schema") ||
                databaseName.equalsIgnoreCase("mysql") ||
                databaseName.equalsIgnoreCase("performance_schema") ||
                databaseName.equalsIgnoreCase("sys")) {
                continue;
            }
            defaultDatabase = databaseName;
            break;
        }

        // 在找到的第一个非系统数据库中创建心跳表
        if (defaultDatabase != null) {
            String tableName = StringUtils.isEmpty(heartbeatTableName) ? DEFAULT_HEARTBEAT_TABLE : heartbeatTableName;
            String createHeartbeatSql = String.format(
                "create table if not exists `%s`.`%s`(id int(4) AUTO_INCREMENT, gmt_create DATETIME(3), PRIMARY KEY (`id`));",
                defaultDatabase, tableName);
            log.info("create heartbeat table sql : " + createHeartbeatSql);
            stmt.execute(createHeartbeatSql);
            heartbeatDatabaseName = defaultDatabase;
            heartbeatTableName = tableName;
        }
    }

    /**
     * 本方法执行一下逻辑
     * 1、检查heartbeatDatabaseName是否有值，有择检查是否创建心跳库，没有创建一个。
     * 2、没有设置heartbeatDatabaseName，遍历当前所有数据库，查找是否有创建好的heartbeatTableName， 有则构造更新心跳表的 detectingSQL，没有则直接在当前库创建一个心跳表。
     * 3、如果已经按照 heartbeatDatabaseName 创建好心跳库，判断是否库中已经创建好heartbeatTableName，没有则创建一个。同时构造更新心跳表的 detectingSQL
     */
    public void run() {
        try {
            if (reconnect) {
                reconnect = false;
                mysqlConnection.reconnect();
            } else if (mysqlConnection.getConn() == null) {
                mysqlConnection.connect();
            }
            Long startTime = System.currentTimeMillis();
            Statement stmt = null;
            try {
                Connection conn = mysqlConnection.getConn();

                stmt = conn.createStatement();

                if (isLabEnv && StringUtils.isNotBlank(serverId)) {
                    String setServerIdSql = String.format("set polardbx_server_id=%d",
                        Math.abs(Long.valueOf(serverId).intValue()));
                    // set server id before
                    stmt.execute(setServerIdSql);
                }

                // 优先创建心跳表
                if (createHeartbeatTable) {
                    // 检查是否设置了heartbeatDatabaseName
                    if (!StringUtils.isEmpty(heartbeatDatabaseName)) {
                        createHeartbeatDatabaseAndTable(stmt);
                    } else {
                        // 2、没有设置heartbeatDatabaseName，遍历当前所有数据库，查找是否有创建好的heartbeatTableName
                        boolean foundHeartbeatTable = findIfExists(stmt);

                        // 没有找到心跳表，则在第一个非系统数据库中创建一个心跳表
                        if (!foundHeartbeatTable) {
                            findAndCreateIfNotExists(stmt);
                        }
                    }
                    createHeartbeatTable = false;
                } else {
                    buildDetectedSqlBefore();
                    // 执行心跳检测SQL
                    stmt.execute(detectingSQL);
                }
            } catch (SQLException e) {
                throw new PolardbxException(e);
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
            }
            Long endTime = System.currentTimeMillis();
            // log.info("heartbeat cost :  " + (endTime - startTime));
        } catch (Throwable e) {
            reconnect = true;
            log.warn("connect failed by " + ExceptionUtils.getStackTrace(e));
        }

    }

    public MysqlConnection getMysqlConnection() {
        return mysqlConnection;
    }
}
