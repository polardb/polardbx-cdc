/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DnHealthChecker {
    private static final String CHECK_SQL = "select 1";
    private static final String CHECK_FOLLOWER_SQL = "select ROLE from information_schema.ALISQL_CLUSTER_LOCAL";
    private static final String FOLLOWER_ROLE = "Follower";
    private static final String FOLLOWER_DELAY_SQL = "show slave status";
    private static final Logger logger = LoggerFactory.getLogger(DnHealthChecker.class);
    private final AuthenticationInfo auth;
    private MysqlConnection conn;
    private long lastConnectTime;

    public DnHealthChecker(AuthenticationInfo auth) {
        this.auth = auth;
    }

    public MysqlConnection buildConn() {
        MysqlConnection conn = new MysqlConnection(auth);
        int timeoutSec = (int) TimeUnit.SECONDS.toMillis(
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_CONN_TIMEOUT_SEC));
        conn.setSoTimeout(timeoutSec);
        conn.setConnTimeout(timeoutSec);
        return conn;
    }

    public String getStorageInstId() {
        return auth.getStorageInstId();
    }

    public void check() throws IOException {
        if (conn == null) {
            conn = buildConn();
            conn.connect();
            lastConnectTime = System.currentTimeMillis();
        }
        conn.query(CHECK_SQL, rs -> 0);
        if (logger.isDebugEnabled()) {
            logger.debug("check dn health success {} {} {}", auth.getStorageInstId(), auth.getAddress().getHostString(),
                auth.getAddress().getPort());
        }

        followerDelayCheck();

        long timeout = TimeUnit.SECONDS.toMillis(
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_TIMEOUT_SEC));
        if (System.currentTimeMillis() - lastConnectTime > timeout) {
            if (logger.isDebugEnabled()) {
                logger.debug("check dn health timeout, will reconnect {} {} {}", auth.getStorageInstId(),
                    auth.getAddress().getHostString(), auth.getAddress().getPort());
            }
            conn.disconnect();
            conn = null;
        }
    }

    public void followerDelayCheck() {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_FOLLOWER_DELAY_CHECK_SWITCH)
            && isFollower()) {
            int delay = followerDelay();
            int threshold =
                DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_FOLLOWER_DELAY_THRESHOLD_SEC);
            if (logger.isDebugEnabled()) {
                logger.debug("dn : {} follower delay is {}s, threshold : {}s", auth.getStorageInstId(), delay,
                    threshold);
            }
            if (delay != -1 && delay > threshold) {
                logger.warn("follower delay detected, storageInstId:{}, delay:{}s, threshold:{}s",
                    auth.getStorageInstId(), delay, threshold);
                throw new PolardbxException("follower delay detected for " + auth.getStorageInstId());
            }
        }
    }

    public boolean isFollower() {
        String role = conn.query(CHECK_FOLLOWER_SQL, rs -> {
            if (rs.next()) {
                return rs.getString(1);
            }
            return "leader";
        });

        return FOLLOWER_ROLE.equalsIgnoreCase(role);
    }

    public int followerDelay() {
        return conn.query(FOLLOWER_DELAY_SQL, rs -> {
            if (rs.next()) {
                return rs.getInt("Seconds_Behind_Master");
            }
            return -1;
        });
    }

    public void startCheck() {
        DnHealthCheckerManager manager = SpringContextHolder.getObject(DnHealthCheckerManager.class);
        manager.registerTask(this);
    }

    public void stopCheck() {
        DnHealthCheckerManager manager = SpringContextHolder.getObject(DnHealthCheckerManager.class);
        manager.unregisterTask(this);
    }

}
