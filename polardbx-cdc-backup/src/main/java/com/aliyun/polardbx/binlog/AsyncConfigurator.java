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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.lindorm.LindormConfig;
import com.aliyun.polardbx.binlog.lindorm.LindormProvider;
import com.aliyun.polardbx.binlog.oss.OssClientProvider;
import com.aliyun.polardbx.binlog.oss.OssConfig;
import com.aliyun.polardbx.binlog.platform.dbstack.NameServiceApi;
import com.aliyun.polardbx.binlog.platform.dbstack.NsServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class AsyncConfigurator implements IConfigurator, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfigurator.class);
    private final OssConfig ossConfig;
    private final LindormConfig lindormConfig;
    private IRemoteManager remoteManager;
    private volatile boolean configSuccess;

    public AsyncConfigurator(OssConfig ossConfig, LindormConfig lindormConfig) {
        this.ossConfig = ossConfig;
        this.lindormConfig = lindormConfig;
    }

    @Override
    public void doConfig() {
        Thread t = new Thread(this, "backup-config");
        t.start();
    }

    private void configOss() {
        if (!ossConfig.isAvaliable()) {
            return;
        }
        if (remoteManager != null) {
            logger.warn("already config oss backup message, will ignore lindorm");
        }
        logger.info("config oss manager");
        OssClientProvider ocp = new OssClientProvider(ossConfig);
        remoteManager = ocp;
        configSuccess = true;
    }

    private void configLindorm() {
        if (!lindormConfig.isAvaliable()) {
            NsServiceType nsServiceType = NameServiceApi.queryLindormFileService();
            String getSubDown = nsServiceType.getGwSubDomain();
            String ipAndPort[] = getSubDown.split(":");
            lindormConfig.setIp(ipAndPort[0]);
            lindormConfig.setPort(Integer.parseInt(ipAndPort[1]));
        }
        if (!lindormConfig.isAvaliable()) {
            return;
        }
        if (remoteManager != null) {
            logger.warn("already config oss backup message, will ignore lindorm");
            return;
        }
        logger.info("config lindorm manager");
        LindormProvider lpr = new LindormProvider(lindormConfig);
        remoteManager = lpr;
        configSuccess = true;
    }

    @Override
    public IRemoteManager prepare() {
        while (!configSuccess) {
            try {
                Thread.sleep(1000L);
            } catch (Exception e) {
            }
            logger.info("wait for remote backup config success");
        }
        return remoteManager;
    }

    @Override
    public void run() {
        while (!configSuccess) {
            try {
                if (ossConfig != null) {
                    configOss();
                } else if (lindormConfig != null) {
                    configLindorm();
                }
                break;
            } catch (Exception e) {
                logger.error("config backup error!", e);
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                } catch (InterruptedException interruptedException) {
                }
            }

        }
    }
}
