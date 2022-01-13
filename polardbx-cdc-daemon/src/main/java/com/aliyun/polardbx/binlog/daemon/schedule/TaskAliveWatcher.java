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

package com.aliyun.polardbx.binlog.daemon.schedule;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogTaskConfigStatus;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class TaskAliveWatcher extends AbstractBinlogTimerTask {
    private final CommandPipeline commander = new CommandPipeline();
    private final String instId;

    private final BinlogTaskConfigMapper taskConfigMapper =
        SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
    private final DumperInfoMapper dumperInfoMapper =
        SpringContextHolder.getObject(DumperInfoMapper.class);
    private final RelayFinalTaskInfoMapper taskInfoMapper =
        SpringContextHolder.getObject(RelayFinalTaskInfoMapper.class);

    public TaskAliveWatcher(String cluster, String clusterType, String taskName, int interval) {
        super(cluster, clusterType, taskName, interval);
        instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
    }

    @Override
    public synchronized void exec() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Task Alive Watcher execute.");
            }

            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (runtimeMode == RuntimeMode.LOCAL) {
                return;
            }

            // 查询本机需要运行的任务列表
            List<BinlogTaskConfig> localTaskConfigs = taskConfigMapper.select(
                s -> s.where(BinlogTaskConfigDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(instId)));
            Set<String> localTasks = localTaskConfigs.stream()
                .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());

            // 停止没有分配在本机上的正在运行的任务
            stopNoLocalTasks(localTasks);

            if (log.isDebugEnabled()) {
                log.debug("local binlog task config is " + JSONObject.toJSONString(localTaskConfigs));
            }

            // 尝试启动或重启任务
            for (BinlogTaskConfig config : localTaskConfigs) {
                //跳过不自动调度的任务
                if (config.getStatus() == BinlogTaskConfigStatus.DISABLE_AUTO_SCHEDULE) {
                    continue;
                }
                process(config);
            }
        } catch (Exception e) {
            log.error("TaskKeepAlive Fail {}", name, e);
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.DAEMON_TASK_ALIVE_WATCHER_ERROR, ExceptionUtils.getStackTrace(e));
        }
    }

    private void process(BinlogTaskConfig config) throws Exception {
        Optional<CommonInfo> infoOptional;
        if (TaskType.Relay.name().equals(config.getRole()) || TaskType.Final.name().equals(config.getRole())) {
            infoOptional = taskInfoMapper.selectOne(
                s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())))
                .map(s -> new CommonInfo(s.getTaskName(), s.getGmtHeartbeat(), s.getGmtCreated()));
        } else {
            infoOptional = dumperInfoMapper.selectOne(
                s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())))
                .map(s -> new CommonInfo(s.getTaskName(), s.getGmtHeartbeat(), s.getGmtCreated()));
        }

        if (infoOptional.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("task info is " + infoOptional.get() + ", now is " + System.currentTimeMillis());
            }

            CommonInfo info = infoOptional.get();
            long now = System.currentTimeMillis();
            int heartbeatTimeout = DynamicApplicationConfig.getInt(DAEMON_TASK_WATCH_HEARTBEAT_TIMEOUT_MS);
            if (now - info.startTime.getTime() > heartbeatTimeout * 2
                && now - info.heartbeatTime.getTime() > heartbeatTimeout) {
                log.info("prepare to restart task {}", config.getTaskName());
                cleanInfo(config);
                restartTask(config.getTaskName(), config.getMem());
                MonitorManager.getInstance().triggerAlarm(MonitorType.PROCESS_HEARTBEAT_TIMEOUT_WARNING, info.name);
            }
        } else {
            startTask(config.getTaskName(), config.getMem(), false);
        }
    }

    private void stopNoLocalTasks(Set<String> localTasks) throws Exception {
        CommandResult result = commander.execCommand(
            new String[] {
                "bash", "-c",
                "ps -u `whoami` -f | grep 'com.aliyun.polardbx.binlog' | grep -v 'DaemonBootStrap' | grep -v 'grep' |"
                    + " sed 's/.*DtaskName=\\([A-Za-z]*[-]*[0-9]*\\).*/\\1/g'"},
            3000);

        if (result.getCode() == 0) {
            String[] runningTasks = StringUtils.split(result.getMsg(), System.getProperty("line.separator"));
            log.debug("local running tasks {}", Arrays.toString(runningTasks));
            for (String runningTask : runningTasks) {
                if (!localTasks.contains(runningTask)) {
                    commander.stopTask(runningTask);
                    log.warn("stop local running task {} not in {}", runningTask, localTasks);
                }
            }
        } else {
            log.warn("check local running task fail!");
        }
    }

    private void startTask(String taskName, int mem, boolean restart) throws Exception {
        //improve 这里可以用flock控制
        log.warn("prepare to start task {}.", taskName);
        CommandResult result = commander.execCommand(
            new String[] {"bash", "-c", "ps -ef | grep taskName=" + taskName + " | grep -v grep | wc -l"}, 1000);
        log.debug("{} {}: ps check result code={}, count={}", restart ? "Restart" : "Start", taskName, result.getCode(),
            StringUtils.chomp(result.getMsg()));
        if (result.getCode() == 0) {
            int count = Integer.parseInt(StringUtils.getDigits(result.getMsg()));
            switch (count) {
            case 0:
                commander.startTask(taskName, mem);
                log.warn("task {} is started.", taskName);
                break;
            case 1:
                log.warn("task {} is started or starting, will not start again!", taskName);
                break;
            default:
                log.warn("task {} is repeat started, will force stop!", taskName);
                commander.stopTask(taskName);
                break;
            }
        }
    }

    private void restartTask(String taskName, int mem) throws Exception {
        //检查最近启动时间，小于2分钟，则不重启
        CommandResult result = commander.execCommand(
            new String[] {
                "bash", "-c",
                "ps -eo etimes,cmd | grep taskName=" + taskName + " | grep -v grep | awk '{print $1}'"}, 1000);
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

        commander.stopTask(taskName);
        log.info("task {} is stopped.", taskName);
        startTask(taskName, mem, true);
    }

    private void cleanInfo(BinlogTaskConfig config) {
        if (TaskType.Relay.name().equals(config.getRole()) || TaskType.Final.name().equals(config.getRole())) {
            deleteTaskInfo(config.getTaskName());
        } else {
            deleteDumperInfo(config.getTaskName());
        }
        log.info("Task(Dumper) info {} is cleaned.", config.getTaskName());
    }

    private void deleteDumperInfo(String name) {
        dumperInfoMapper.delete(s ->
            s.where(DumperInfoDynamicSqlSupport.clusterId,
                SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
    }

    private void deleteTaskInfo(String name) {
        taskInfoMapper.delete(s ->
            s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId,
                SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
    }

    static class CommonInfo {
        String name;
        Date heartbeatTime;
        Date startTime;

        public CommonInfo(String name, Date heartbeatTime, Date startTime) {
            this.name = name;
            this.heartbeatTime = heartbeatTime;
            this.startTime = startTime;
        }

        @Override
        public String toString() {
            return "CommonInfo{" +
                "name='" + name + '\'' +
                ", heartbeatTime=" + heartbeatTime +
                ", startTime=" + startTime +
                '}';
        }
    }
}
