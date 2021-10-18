/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskInfo;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

/**
 * Created by ziyang.lb
 **/
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final String cluster;
    private final TaskInfoProvider taskInfoProvider;
    private final MetricsManager metricsManager;

    private TaskEngine taskEngine;
    private TxnStreamRpcServer rpcServer;
    private volatile boolean running;

    public TaskController(String cluster, TaskInfoProvider taskInfoProvider) {
        this.cluster = cluster;
        this.taskInfoProvider = taskInfoProvider;
        this.metricsManager = new MetricsManager();
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        running = true;

        TaskInfo taskInfo = taskInfoProvider.get();
        logger.info("starting task controller with {}...", taskInfo);

        // 系统启动时不需要知道startTSO，但为了测试方便，此处允许从TaskInfo获取；如果startTSO为空，则不启动TaskEngine
        taskEngine = new TaskEngine(taskInfoProvider, taskInfo);
        if (StringUtils.isNotBlank(taskInfo.getStartTSO())
            || DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_ENGINE_AUTO_START)) {
            taskEngine.start(taskInfo.getStartTSO());
        }

        rpcServer = new TxnStreamRpcServer(taskInfo.getServerPort(), taskEngine);
        rpcServer.start();

        metricsManager.start();
        MonitorManager.getInstance().startup();

        RelayFinalTaskInfoMapper relayFinalTaskInfoMapper = SpringContextHolder.getObject(
            RelayFinalTaskInfoMapper.class);
        RelayFinalTaskInfo relayFinalTaskInfo = new RelayFinalTaskInfo();
        relayFinalTaskInfo.setClusterId(cluster);
        relayFinalTaskInfo.setTaskName(taskInfo.getName());
        relayFinalTaskInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        relayFinalTaskInfo.setPort(taskInfo.getServerPort());
        relayFinalTaskInfo.setRole(taskInfo.getType().name());
        relayFinalTaskInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        relayFinalTaskInfo.setVersion(taskInfo.getBinlogTaskConfig().getVersion());
        relayFinalTaskInfo.setStatus(0);
        Date now = new Date();
        relayFinalTaskInfo.setGmtHeartbeat(now);
        relayFinalTaskInfo.setGmtCreated(now);
        relayFinalTaskInfo.setGmtModified(now);

        Optional<RelayFinalTaskInfo> info = relayFinalTaskInfoMapper.selectOne(
            s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(cluster))
                .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskInfo.getName())));
        if (info.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (info.get().getVersion() == 0 || runtimeMode == RuntimeMode.LOCAL) {
                relayFinalTaskInfo.setId(info.get().getId());
                relayFinalTaskInfoMapper.updateByPrimaryKey(relayFinalTaskInfo);
            } else {
                logger.info("Duplicate Task info in database : {}", JSONObject.toJSONString(info));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                relayFinalTaskInfoMapper.insert(relayFinalTaskInfo);
            } catch (DuplicateKeyException e) {
                logger.info("Duplicate dumper info in database, insert failed.");
                Runtime.getRuntime().halt(1);
            }
        }

        logger.info("task controller started.");
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (rpcServer != null) {
            try {
                rpcServer.stop();
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        if (taskEngine != null) {
            taskEngine.stop();
        }

        metricsManager.stop();
        MonitorManager.getInstance().shutdown();

        logger.info("task controller stopped.");
    }

}
