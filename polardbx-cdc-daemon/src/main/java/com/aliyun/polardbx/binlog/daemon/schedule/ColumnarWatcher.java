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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.exception.DuplicateKeyException;
import com.aliyun.polardbx.binlog.columnar.ColumnarMonitor;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.ColumnarTopologyBuilder;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.ColumnarInfoMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTask;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ColumnarResourceManager;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;

/**
 * @author wenki
 */
@Slf4j
@Getter
public class ColumnarWatcher extends AbstractBinlogTimerTask {
    private final CommandPipeline commander = new CommandPipeline();
    private final String instId;

    private final String startScript = "/home/admin/polardbx-columnar/bin/startup.sh";
    private final String stopScript = "/home/admin/polardbx-columnar/bin/shutdown.sh";

    private final ColumnarTaskConfigMapper columnarTaskConfigMapper =
        SpringContextHolder.getObject(ColumnarTaskConfigMapper.class);
    private final ColumnarTaskMapper columnarTaskMapper =
        SpringContextHolder.getObject(ColumnarTaskMapper.class);
    private final ColumnarInfoMapper columnarInfoMapper =
        SpringContextHolder.getObject(ColumnarInfoMapper.class);

    public ColumnarWatcher(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
        instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
    }

    @Override
    public synchronized void exec() {
        // 查询本机需要运行的任务列表
        List<ColumnarTaskConfig> localTaskConfigs = columnarTaskConfigMapper.select(
            s -> s.where(ColumnarTaskConfigDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(instId)));

        processStart(localTaskConfigs);
        watchMemory();
    }

    private void processStart(List<ColumnarTaskConfig> taskConfigs) {
        taskConfigs.forEach(config -> {
            try {
                startTask(config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void startTask(ColumnarTaskConfig config) throws Exception {
        Optional<ColumnarInfo> infoOptional;

        infoOptional = columnarTaskMapper.selectOne(
                s -> s.where(ColumnarTaskDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(ColumnarTaskDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())))
            .map(s -> new ColumnarInfo(s.getTaskName(), s.getGmtHeartbeat(), s.getGmtCreated(),
                s.getVersion(), s.getRole()));

        updateTimeAlarm();

        if (infoOptional.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("task info is " + infoOptional.get() + ", now is " + System.currentTimeMillis());
            }
            ColumnarInfo info = infoOptional.get();
            int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS);
            long heartbeatInterval =
                columnarInfoMapper.getHeartbeatInterval(config.getClusterId(), config.getTaskName());
            if (heartbeatInterval > heartbeatTimeout) {

                // JVM心跳超时，但进程还在，一个典型的场景：大数据量场景下GC很频繁，导致cpu使用率很高，Task进程的心跳会出现超时
                if (!isColumnarLauncherAlive()) {
                    if (columnarInfoMapper.getColumnarIndexExist()) {
                        MonitorManager.getInstance()
                            .triggerAlarm(MonitorType.COLUMNAR_JVM_HEARTBEAT_TIMEOUT_ERROR);
                    }
                    log.info("detected heartbeat timeout, and task is already down, prepare to restart, task name {}.",
                        config.getTaskName());
                    restartColumnar(config, config.getTaskName(), config.getMem());
                } else {
                    if ("Leader".equals(info.role)) {
                        log.info("detected heartbeat timeout, but task is still alive, will not restart, task name {}.",
                            config.getTaskName());
                    }
                }

            }
            if (info.version < config.getVersion()) {
                restartColumnar(config, config.getTaskName(), config.getMem());
            }
        } else {
            log.info("columnar task {} not present, will start", config.getTaskName());
            startColumnar(config.getTaskName(), config.getMem(), false);
        }
    }

    public void updateTimeAlarm() {
        long updateTimeInterval = getColumnarInfoMapper().getUpdateTimeInterval();
        boolean columnarIndexExist = getColumnarInfoMapper().getColumnarIndexExist();
        if (columnarIndexExist && updateTimeInterval > DynamicApplicationConfig.getInt(
            COLUMNAR_PROCESS_HEARTBEAT_TIMEOUT_MS)) {
            // 列存进程超时，报警
            log.warn("columnar update_time not update for {} seconds", updateTimeInterval / 1000);
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.COLUMNAR_PROCESS_HEARTBEAT_TIMEOUT_WARNING, updateTimeInterval / 1000);
        }
    }

    static class ColumnarInfo {
        String name;
        Date heartbeatTime;
        Date startTime;
        long version;
        String role;

        public ColumnarInfo(String name, Date heartbeatTime, Date startTime, Long version, String role) {
            this.name = name;
            this.heartbeatTime = heartbeatTime;
            this.startTime = startTime;
            this.version = version;
            this.role = role;
        }

        @Override
        public String toString() {
            return "CommonInfo{" +
                "name='" + name + '\'' +
                ", heartbeatTime=" + heartbeatTime +
                ", startTime=" + startTime +
                ", role=" + role +
                '}';
        }
    }

    private boolean isColumnarLauncherAlive() throws Exception {
        CommandResult result = commander.execCommand(
            new String[] {"bash", "-c", "ps -ef | grep 'ColumnarLauncher' | grep -v grep | wc -l"}, 1000);
        if (result.getCode() != 0) {
            return false;
        }

        int count = Integer.parseInt(StringUtils.getDigits(result.getMsg()));
        return count != 0;
    }

    private void startColumnar(String taskName, int mem, boolean restart) throws Exception {
        build(taskName);

        //improve 这里可以用flock控制
        log.warn("prepare to start task {}.", taskName);
        CommandResult launcherResult = commander.execCommand(
            new String[] {"bash", "-c", "ps -ef | grep 'ColumnarLauncher' | grep -v grep | wc -l"}, 1000);
        log.debug("{} {}: ps check result launcher code={}, launcher count={}", restart ? "Restart" : "Start", taskName,
            launcherResult.getCode(), StringUtils.chomp(launcherResult.getMsg()));
        if (launcherResult.getCode() == 0) {
            int launcherCount = Integer.parseInt(StringUtils.getDigits(launcherResult.getMsg()));
            switch (launcherCount) {
            case 0:
                startColumnar(taskName, mem);
                log.warn("task {} is started.", taskName);
                break;
            case 1:
                log.warn("task {} is started or starting, will not start again!", taskName);
                break;
            default:
                log.warn("task {} is repeat started, will force stop!", taskName);
                stopColumnar();
                break;
            }
        }
    }

    private void restartColumnar(ColumnarTaskConfig config, String taskName, int mem) throws Exception {
        //检查最近启动时间，小于2分钟，则不重启
        CommandResult result = commander.execCommand(
            new String[] {
                "bash", "-c",
                "ps -eo etimes,cmd | grep 'ColumnarLauncher' | grep -v grep | awk '{print $1}'"}, 1000);
        if (result.getCode() == 0) {
            String digits = StringUtils.getDigits(result.getMsg());
            if (StringUtils.isNotBlank(digits)) {
                int seconds = Integer.parseInt(digits);
                if (seconds < 120) {
                    log.info("start in 120 seconds, will not restart this time!");
                    return;
                }
            }
        } else {
            log.warn("{} check start time fail, code={}, msg={}", taskName,
                result.getCode(), StringUtils.chomp(result.getMsg()));
        }

        stopColumnar();

        columnarTaskMapper.delete(s ->
            s.where(DumperInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())));

        log.info("task {} is restarted.", taskName);
        startColumnar(taskName, mem, true);
    }

    public void startColumnar(String taskName, int mem) throws Exception {
        commander.execCommand(new String[] {"bash", "-c", "sh " + startScript + " -m " + mem}, 3000);
    }

    public void stopColumnar() throws Exception {
        commander.execCommand(new String[] {"bash", "-c", "sh " + stopScript}, 3000);
    }

    private void build(String taskName) throws Exception {
        ColumnarTaskConfigMapper mapper = SpringContextHolder.getObject(ColumnarTaskConfigMapper.class);
        Optional<ColumnarTaskConfig> opTask = mapper
            .selectOne(s -> s.where(ColumnarTaskConfigDynamicSqlSupport.clusterId,
                    IsEqualTo.of(() -> DynamicApplicationConfig.getString(CLUSTER_ID)))
                .and(ColumnarTaskConfigDynamicSqlSupport.taskName, IsEqualTo.of(() -> taskName)));
        if (!opTask.isPresent()) {
            throw new Exception("columnar task config is null");
        }

        ColumnarTaskConfig taskConfig = opTask.get();

        ColumnarTaskMapper columnarTaskMapper = SpringContextHolder.getObject(
            ColumnarTaskMapper.class);
        ColumnarTask columnarTask = new ColumnarTask();
        columnarTask.setClusterId(taskConfig.getClusterId());
        columnarTask.setTaskName(taskConfig.getTaskName());
        columnarTask.setIp(taskConfig.getIp());
        columnarTask.setPort(taskConfig.getPort());
        columnarTask.setRole(taskConfig.getRole());
        columnarTask.setContainerId(taskConfig.getContainerId());
        columnarTask.setVersion(taskConfig.getVersion());
        columnarTask.setStatus(0);

        Optional<ColumnarTask> info = columnarTaskMapper.selectOne(
            s -> s.where(ColumnarTaskDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(ColumnarTaskDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskConfig.getTaskName())));
        if (info.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (info.get().getVersion() == 0 || RuntimeMode.isLocalMode(runtimeMode)) {
                columnarTask.setId(info.get().getId());
                columnarTaskMapper.updateByPrimaryKey(columnarTask);
            } else {
                log.info("Duplicate Task info in database : {}", JSONObject.toJSONString(info));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                columnarTaskMapper.insert(columnarTask);
            } catch (DuplicateKeyException e) {
                log.info("Duplicate columnar task in database, insert failed.");
                Runtime.getRuntime().halt(1);
            }
        }
    }

    public void watchMemory() {
        try {
            int memoryUsage = 0;

            CommandResult processResult = getCommander().execCommand(
                new String[] {"bash", "-c", "ps -ef | grep 'ColumnarLauncher' | grep -v 'grep' | awk '{print $2}'"},
                1000);
            if (processResult.getCode() == 0) {
                int processId = Integer.parseInt(StringUtils.getDigits(processResult.getMsg()));
                String memoryUsageCommand = "pmap -x " + processId + " | tail -n 1 | awk '{print $4}'";
                CommandResult memResult = getCommander().execCommand(
                    new String[] {"bash", "-c", memoryUsageCommand}, 10000);
                if (memResult.getCode() == 0) {
                    memoryUsage = Integer.parseInt(StringUtils.getDigits(memResult.getMsg()));
                }
            }

            int totalMemory =
                ColumnarTopologyBuilder.calculateHeapMemory(getColumnarInfoMapper().getContainerMemory(getInstId()));
            double memoryThreshold = 0.95 * totalMemory * 1024;
            if (memoryUsage > memoryThreshold) {
                log.warn("columnar process memory usage is too high, memoryUsage={}, totalMem={}", memoryUsage,
                    memoryThreshold);
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.COLUMNAR_PROCESS_OOM_WARNING);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
