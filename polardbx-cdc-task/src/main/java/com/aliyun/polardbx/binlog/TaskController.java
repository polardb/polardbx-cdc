/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoExt;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.extractor.DnHealthCheckerManager;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class TaskController {

    private final String cluster;
    private final TaskRuntimeConfigProvider taskRuntimeConfigProvider;
    private final MetricsManager metricsManager;

    private TaskRuntimeConfig taskRuntimeConfig;
    private TaskEngine taskEngine;
    private TxnStreamRpcServer rpcServer;
    private volatile boolean running;
    private DnHealthCheckerManager checker;
    @Setter
    private Consumer<Void> processExitCallback;

    public TaskController(String cluster,
                          TaskRuntimeConfig originTaskRuntimeConfig,
                          TaskRuntimeConfigProvider taskRuntimeConfigProvider) {

        this(cluster, originTaskRuntimeConfig, taskRuntimeConfigProvider, i -> Runtime.getRuntime().halt(1));
    }

    public TaskController(String cluster,
                          TaskRuntimeConfig originTaskRuntimeConfig,
                          TaskRuntimeConfigProvider taskRuntimeConfigProvider,
                          Consumer<Void> processExitCallback) {

        this.cluster = cluster;
        this.taskRuntimeConfig = originTaskRuntimeConfig;
        this.taskRuntimeConfigProvider = taskRuntimeConfigProvider;
        this.metricsManager = new MetricsManager();
        this.processExitCallback = processExitCallback;
        this.build();
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        doStart();
        running = true;
    }

    private void doStart() throws IOException {
        log.info("## starting the task controller ......");

        // 系统启动时不需要知道startTSO，但为了测试方便，此处允许从TaskInfo获取；如果startTSO为空，则不启动TaskEngine
        taskEngine = new TaskEngine(taskRuntimeConfig);
        taskEngine.start();

        rpcServer = new TxnStreamRpcServer(taskRuntimeConfig.getServerPort(), taskEngine, taskRuntimeConfig.getType());
        rpcServer.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        rpcServer.setSubVersion(taskRuntimeConfig.getBinlogTaskConfig().getSubVersion());
        rpcServer.start();
        this.checker = getObject(DnHealthCheckerManager.class);

        metricsManager.start();
        MonitorManager.getInstance().startup();
        checker.start();

        log.info("## the task controller is running now ......");
    }

    public void stop() {
        if (!running) {
            return;
        }
        doStop();
        running = false;
    }

    private void doStop() {
        log.info("## stopping the task controller ......");
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
        checker.stop();
        log.info("## the task controller is stopped.");
    }

    public void reload(TaskRuntimeConfig taskRuntimeConfig) {
        log.info("## reloading the task controller ......");
        this.taskRuntimeConfig = taskRuntimeConfig;
        this.taskEngine.setTaskRuntimeConfig(taskRuntimeConfig);
        this.rpcServer.setSubVersion(taskRuntimeConfig.getBinlogTaskConfig().getSubVersion());
        log.info("## the task controller is reloaded");
    }

    private void build() {
        BinlogTaskInfoMapper taskInfoMapper = getObject(BinlogTaskInfoMapper.class);
        BinlogTaskInfo binlogTaskInfo = new BinlogTaskInfo();
        BinlogTaskInfoExt binlogTaskInfoExt = new BinlogTaskInfoExt();
        binlogTaskInfo.setClusterId(cluster);
        binlogTaskInfo.setTaskName(taskRuntimeConfig.getName());
        binlogTaskInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        binlogTaskInfo.setPort(taskRuntimeConfig.getServerPort());
        binlogTaskInfo.setRole(taskRuntimeConfig.getType().name());
        binlogTaskInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        binlogTaskInfo.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        binlogTaskInfo.setSubVersion(taskRuntimeConfig.getBinlogTaskConfig().getSubVersion());
        binlogTaskInfo.setStatus(0);
        binlogTaskInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        binlogTaskInfo.setEnableLightRebalance(true);
        binlogTaskInfo.setExt(JSONObject.toJSONString(binlogTaskInfoExt));

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
                log.info("Duplicate Task info in database : {}", JSONObject.toJSONString(info));
                processExitCallback.accept(null);
            }
        } else {
            try {
                taskInfoMapper.insert(binlogTaskInfo);
            } catch (DuplicateKeyException e) {
                log.info("Duplicate task info in database, insert failed.");
                processExitCallback.accept(null);
            }
        }
    }
}
