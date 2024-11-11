/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.leader;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.MetaDbDataSource;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ShuGuang
 */
@Slf4j
public class RuntimeLeaderElector {
    private static final AtomicBoolean scannerStarted = new AtomicBoolean(false);
    private static final AtomicBoolean isDaemonLeader = new AtomicBoolean(false);
    private static final ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "daemon-leadership-scanner");
            t.setDaemon(true);
            return t;
        });
    private static volatile Connection connection;
    private static volatile Throwable scannerError;

    public static Pair<String, Integer> getDaemonLeaderInfo() {
        if (isDaemonLeader()) {
            return Pair.of(DynamicApplicationConfig.getString(INST_IP),
                DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        } else {
            NodeInfoMapper mapper = getObject(NodeInfoMapper.class);
            List<NodeInfo> nodeInfoList = mapper.select(
                s -> s.where(NodeInfoDynamicSqlSupport.clusterId,
                        isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID)))
                    .and(NodeInfoDynamicSqlSupport.role, isEqualTo(NodeRole.MASTER.getName())));
            return nodeInfoList.isEmpty() ? null :
                Pair.of(nodeInfoList.get(0).getIp(), nodeInfoList.get(0).getDaemonPort());
        }
    }

    public static boolean isDaemonLeader() {
        String name = DynamicApplicationConfig.getString(CLUSTER_ID) + "-daemon-leader";
        if (scannerStarted.compareAndSet(false, true)) {
            try {
                isDaemonLeader.set(tryAcquireLeaderShip(name));
                scheduledExecutorService.scheduleAtFixedRate(() -> {
                    try {
                        isDaemonLeader.set(tryAcquireLeaderShip(name));
                        scannerError = null;
                    } catch (Throwable e) {
                        scannerError = e;
                        log.error("daemon leadership scan error!", e);
                    }
                }, 0, 1000, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                scannerStarted.set(false);
                throw t;
            }
        }
        if (scannerError != null) {
            throw new PolardbxException("daemon leadership scan error", scannerError);
        }
        return isDaemonLeader.get();
    }

    public static boolean isDumperMaster(long version, String taskName) {
        String config = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        if (StringUtils.isBlank(config)) {
            return false;
        }

        ClusterSnapshot clusterSnapshot = JSONObject.parseObject(config, ClusterSnapshot.class);
        return version == clusterSnapshot.getVersion() && taskName.equals(clusterSnapshot.getDumperMaster());
    }

    public static boolean isDumperX(long version, TaskType taskType) {
        String config = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        if (StringUtils.isBlank(config)) {
            return false;
        }

        ClusterSnapshot clusterSnapshot = JSONObject.parseObject(config, ClusterSnapshot.class);
        return version == clusterSnapshot.getVersion() && taskType.equals(TaskType.DumperX);
    }

    public static boolean isDumperMasterOrX(long version, TaskType taskType, String taskName) {
        return isDumperMaster(version, taskName) || isDumperX(version, taskType);
    }

    public static boolean isLeader(String name) {
        return tryAcquireLeaderShip(name);
    }

    private static Connection getConnection() {
        if (connection == null) {
            synchronized (RuntimeLeaderElector.class) {
                if (connection == null) {
                    buildConn();
                }
            }
        }
        return connection;
    }

    private static boolean tryAcquireLeaderShip(String name) {
        if (getConnection() == null) {
            log.error("connection is null, {} return false directly", name);
            return false;
        }

        try {
            if (!getConnection().isValid(1)) {
                tryCloseConnection();
                log.warn("connection is invalid, {} return false directly", name);
                return false;
            }
        } catch (Throwable t) {
            log.warn("leader check fail {}", name, t);
            tryCloseConnection();
            return false;
        }

        try (Statement statement = getConnection().createStatement()) {
            // Tries to obtain a lock with a name given by the string str, using a timeout of timeout seconds.
            // Returns 1 if the lock was obtained successfully, 0 if the attempt timed out
            ResultSet resultSet = statement.executeQuery(
                "SELECT GET_LOCK('" + name + "',1)");
            if (resultSet.next()) {
                return resultSet.getInt(1) == 1;
            } else {
                return false;
            }
        } catch (SQLException e) {
            log.warn("leader check fail {}", name, e);
            tryCloseConnection();
            return false;
        }
    }

    private static void buildConn() {
        try {
            MetaDbDataSource metaDs = getObject("metaDataSource");
            connection = DriverManager.getConnection(metaDs.getUrl(), metaDs.getUsername(), metaDs.getPassword());
        } catch (SQLException e) {
            connection = null;
            log.error("RuntimeLeaderElector init connection fail", e);
        }
    }

    private static void tryCloseConnection() {
        // 释放connection
        if (connection != null) {
            try {
                connection.close();
            } catch (Throwable e1) {
            }
            connection = null;
        }
    }
}
