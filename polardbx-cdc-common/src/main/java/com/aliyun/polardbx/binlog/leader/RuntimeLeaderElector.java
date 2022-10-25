/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.leader;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;

/**
 * Created by ShuGuang
 */
@Slf4j
public class RuntimeLeaderElector {
    private static volatile Connection connection;
    private static volatile Throwable scannerError;
    private static final AtomicBoolean scannerStarted = new AtomicBoolean(false);
    private static final AtomicBoolean isDaemonLeader = new AtomicBoolean(false);
    private static final ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "daemon-leadership-scanner");
            t.setDaemon(true);
            return t;
        });

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

    public static boolean isDumperLeader(String taskName) {
        String config = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        if (StringUtils.isBlank(config)) {
            return false;
        } else {
            ClusterSnapshot clusterSnapshot = JSONObject.parseObject(config, ClusterSnapshot.class);
            return taskName.equals(clusterSnapshot.getDumperMaster());
        }
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
                connection = null;
                log.warn("connection is invalid, {} return false directly", name);
                return false;
            }
        } catch (Throwable t) {
            log.warn("leader check fail {}", name, t);
            return false;
        }

        try (Statement statement = getConnection().createStatement()) {
            ResultSet resultSet = statement.executeQuery(
                "SELECT GET_LOCK('" + name + "',1)");
            if (resultSet.next()) {
                return resultSet.getInt(1) == 1;
            } else {
                return false;
            }
        } catch (SQLException e) {
            log.warn("leader check fail {}", name, e);
            return false;
        }

    }

    private static void buildConn() {
        try {
            connection = DriverManager.getConnection(
                SpringContextHolder.getPropertiesValue("metaDb_url"),
                SpringContextHolder.getPropertiesValue("metaDb_username"),
                tryDecryptPassword(SpringContextHolder.getPropertiesValue("metaDb_password")));
        } catch (SQLException e) {
            connection = null;
            log.error("RuntimeLeaderElector init connection fail", e);
        }
    }

    private static String tryDecryptPassword(String password) {
        boolean useEncryptedPassword = DynamicApplicationConfig.getBoolean(ConfigKeys.USE_ENCRYPTED_PASSWORD);
        return useEncryptedPassword ? PasswdUtil.decryptBase64(password) : password;
    }

}
