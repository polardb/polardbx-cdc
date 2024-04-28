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
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_ENGINE_AUTO_START;

/**
 * Created by ziyang.lb
 **/
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final String cluster;
    private final TaskConfigProvider taskConfigProvider;
    private final TaskRuntimeConfig taskRuntimeConfig;
    private final MetricsManager metricsManager;

    private TaskEngine taskEngine;
    private TxnStreamRpcServer rpcServer;
    private volatile boolean running;

    public TaskController(String cluster, TaskConfigProvider taskConfigProvider) {
        this.cluster = cluster;
        this.taskConfigProvider = taskConfigProvider;
        this.taskRuntimeConfig = taskConfigProvider.getTaskRuntimeConfig();
        this.metricsManager = new MetricsManager();
        this.build();
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        running = true;

        logger.info("starting task controller with {}...", taskRuntimeConfig);

        // 系统启动时不需要知道startTSO，但为了测试方便，此处允许从TaskInfo获取；如果startTSO为空，则不启动TaskEngine
        taskEngine = new TaskEngine(taskConfigProvider, taskRuntimeConfig);
        if (StringUtils.isNotBlank(taskRuntimeConfig.getStartTSO())
            || DynamicApplicationConfig.getBoolean(TASK_ENGINE_AUTO_START)
            || taskRuntimeConfig.getType() == TaskType.Dispatcher) {
            taskEngine.start(taskRuntimeConfig.getStartTSO());
        }

        rpcServer = new TxnStreamRpcServer(taskRuntimeConfig.getServerPort(), taskEngine, taskRuntimeConfig.getType());
        rpcServer.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        rpcServer.start();

        metricsManager.start();
        MonitorManager.getInstance().startup();

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

    private void build() {
        BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(
            BinlogTaskInfoMapper.class);
        BinlogTaskInfo binlogTaskInfo = new BinlogTaskInfo();
        binlogTaskInfo.setClusterId(cluster);
        binlogTaskInfo.setTaskName(taskRuntimeConfig.getName());
        binlogTaskInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        binlogTaskInfo.setPort(taskRuntimeConfig.getServerPort());
        binlogTaskInfo.setRole(taskRuntimeConfig.getType().name());
        binlogTaskInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        binlogTaskInfo.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        binlogTaskInfo.setStatus(0);
        binlogTaskInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));

        Optional<BinlogTaskInfo> info = taskInfoMapper.selectOne(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(cluster))
                .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskRuntimeConfig.getName())));
        if (info.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (info.get().getVersion() == 0 || RuntimeMode.isLocalMode(runtimeMode)) {
                binlogTaskInfo.setId(info.get().getId());
                taskInfoMapper.updateByPrimaryKeySelective(binlogTaskInfo);
            } else {
                logger.info("Duplicate Task info in database : {}", JSONObject.toJSONString(info));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                taskInfoMapper.insert(binlogTaskInfo);
            } catch (DuplicateKeyException e) {
                logger.info("Duplicate task info in database, insert failed.");
                Runtime.getRuntime().halt(1);
            }
        }
    }
}
