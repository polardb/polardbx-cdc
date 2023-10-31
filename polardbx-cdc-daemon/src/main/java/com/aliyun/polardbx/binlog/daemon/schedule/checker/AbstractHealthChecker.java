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
package com.aliyun.polardbx.binlog.daemon.schedule.checker;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public abstract class AbstractHealthChecker implements HealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractHealthChecker.class);

    protected StorageInfo storageInfo;
    protected MysqlConnection connection;
    protected long delay;
    private long lastInjectTimestamp = System.currentTimeMillis();
    private long injectInterval = TimeUnit.MINUTES.toMillis(10);

    public AbstractHealthChecker(StorageInfo storageInfo) {
        this.storageInfo = storageInfo;
    }

    @Override
    public long check() throws Exception {
        boolean inject = DynamicApplicationConfig.getBoolean(ConfigKeys.DAEMON_DN_HEALTH_CHECKER_ERROR_INJECT);
        if (inject) {
            long now = System.currentTimeMillis();
            if (now - lastInjectTimestamp > injectInterval) {
                try {
                    if (randomInject()) {
                        LabEventManager.logEvent(LabEventType.DN_FOLLOWER_DELAY_ERROR_INJECT,
                            "storageId:" + storageInfo.getStorageInstId());
                        return DynamicApplicationConfig.getLong(ConfigKeys.DAEMON_DN_HEALTH_CHECKER_SLA_LIMIT_SECOND)
                            + 1;
                    }
                } finally {
                    lastInjectTimestamp = now;
                }
            }
        }
        if (!ensure()) {
            return delay;
        }
        try {
            return doCheck();
        } finally {
            release();
        }
    }

    private boolean randomInject() {
        return RandomUtils.nextBoolean();
    }

    public abstract long doCheck() throws Exception;

    private void release() throws IOException {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    private boolean ensure() {
        DnHost host = DnHost.getLocalDnHost(storageInfo.getStorageMasterInstId());
        AuthenticationInfo authenticationInfo =
            new AuthenticationInfo(new InetSocketAddress(host.getIp(), host.getPort()), host.getUserName(),
                host.getPassword());
        connection = new MysqlConnection(authenticationInfo);
        long timeout = DynamicApplicationConfig.getLong(ConfigKeys.DAEMON_DN_HEALTH_CHECKER_CONNECTION_TIMEOUT_SECOND);
        connection.setConnTimeout((int) TimeUnit.SECONDS.toMillis(timeout));
        int retryNum = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_DN_HEALTH_CHECKER_RETRY_NUM);
        if (retryNum > 0) {
            Retryer<Object> retryer = RetryerBuilder.newBuilder().retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(retryNum)).build();
            try {
                retryer.call(() -> {
                    connection.connect();
                    return null;
                });
            } catch (Exception e) {
                logger.error("retry connect to " + storageInfo.getInstId() + " failed!", e);
                delay = Long.MAX_VALUE;
                return false;
            }
        } else {
            try {
                connection.connect();
            } catch (Exception e) {
                logger.error("connect to " + storageInfo.getInstId() + " failed!", e);
                delay = Long.MAX_VALUE;
                return false;
            }
        }
        return true;
    }
}
