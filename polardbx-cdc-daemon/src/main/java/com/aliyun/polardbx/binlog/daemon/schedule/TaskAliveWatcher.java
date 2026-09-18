/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.enums.BinlogTaskStatus;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_ROCKSDB_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_EXECUTION_INSTRUCTION;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.RUNTIME_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.daemon.constant.ClusterExecutionInstruction.START_EXECUTION_INSTRUCTION;
import static com.aliyun.polardbx.binlog.daemon.constant.ClusterExecutionInstruction.STOP_EXECUTION_INSTRUCTION;
import static com.aliyun.polardbx.binlog.util.SystemDbConfig.getSystemDbConfig;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class TaskAliveWatcher extends AbstractBinlogTimerTask {

    private final CommandPipeline commandPipeline;
    private final String instId;

    private final BinlogTaskConfigMapper taskConfigMapper =
        SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
    private final DumperInfoMapper dumperInfoMapper =
        SpringContextHolder.getObject(DumperInfoMapper.class);
    private final BinlogTaskInfoMapper taskInfoMapper =
        SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
    private final AtomicBoolean sameRegionFlag;

    public TaskAliveWatcher(String cluster, String clusterType, String taskName, int interval) {
        this(cluster, clusterType, taskName, interval, new CommandPipeline());
    }

    public TaskAliveWatcher(String cluster, String clusterType, String taskName, int interval,
                            CommandPipeline commandPipeline) {
        super(cluster, clusterType, taskName, interval);
        this.instId = getString(ConfigKeys.INST_ID);
        boolean sameRegion = getBoolean(TASK_DUMP_SAME_REGION_STORAGE_BINLOG);
        this.sameRegionFlag = new AtomicBoolean(sameRegion);
        this.commandPipeline = commandPipeline;
    }

    @Override
    public synchronized void exec() {
        try {
            if (RuntimeMode.isLocalMode(RuntimeMode.valueOf(getString(RUNTIME_MODE)))) {
                return;
            }

            // 检查是否切换了region
            final boolean newSameRegion = getBoolean(TASK_DUMP_SAME_REGION_STORAGE_BINLOG);
            final AtomicBoolean regionChanged = new AtomicBoolean(false);
            if (CommonUtils.isGlobalBinlogSlave()) {
                regionChanged.set(sameRegionFlag.get() != newSameRegion);
            }

            // 查询本机需要运行的任务列表
            List<BinlogTaskConfig> localTaskConfigs = taskConfigMapper.select(
                s -> s.where(BinlogTaskConfigDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(instId)));
            Set<String> localTasks = localTaskConfigs.stream()
                .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());

            // 停止没有分配在本机上的正在运行的任务
            stopTasksNotBelongsToThisNode(localTasks);

            // 对已经不在本机运行的Task或Dumper遗留的资源进行GC
            tryCleanResource(localTasks);

            // 停止或者启动任务
            String executionInstruction = StringUtils.defaultIfEmpty(
                getSystemDbConfig(CLUSTER_EXECUTION_INSTRUCTION), START_EXECUTION_INSTRUCTION);

            if (executionInstruction.equals(STOP_EXECUTION_INSTRUCTION)) {
                processStop(localTaskConfigs);
            } else {
                Set<String> forceRestartTaskSet = localTaskConfigs.stream()
                    .filter(b -> regionChanged.get() && StringUtils.equalsAnyIgnoreCase(
                        b.getRole(), TaskType.Relay.name(), TaskType.Final.name(), TaskType.Dispatcher.name()))
                    .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
                processStart(localTaskConfigs, forceRestartTaskSet);
            }

            if (regionChanged.get()) {
                sameRegionFlag.set(newSameRegion);
            }
        } catch (Throwable e) {
            log.error("TaskKeepAlive Fail {}", name, e);
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.DAEMON_TASK_ALIVE_WATCHER_ERROR, ExceptionUtils.getStackTrace(e));
        }
    }

    void processStart(List<BinlogTaskConfig> taskConfigs, Set<String> forceRestartTaskSet) {
        taskConfigs.forEach(config -> {
            try {
                tryStartTask(config, forceRestartTaskSet);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    void processStop(List<BinlogTaskConfig> taskConfigs) throws Exception {
        Set<String> runningTaskSet = getAllTaskProcess();
        Set<String> whiteList = stopTaskWhitList();

        if (!runningTaskSet.isEmpty()) {
            for (BinlogTaskConfig config : taskConfigs) {
                if (runningTaskSet.contains(config.getTaskName()) && !whiteList.contains(config.getTaskName())) {
                    stopTask(config.getTaskName());
                }
                updateTaskStatus(config.getClusterId(), config.getTaskName(), config.getRole(),
                    BinlogTaskStatus.STOPPED);
            }
        }
    }

    void tryStartTask(BinlogTaskConfig config, Set<String> forceRestartTaskSet) throws Exception {
        if (forceRestartTaskSet.contains(config.getTaskName())) {
            log.info("prepare to force restart task {}", config.getTaskName());
            restartTask(config, config.getTaskName(), config.getMem());
            return;
        }

        Optional<CommonInfo> infoOptional;
        if (TaskType.isTask(config.getRole())) {
            infoOptional = taskInfoMapper.selectOne(
                    s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                        .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())))
                .map(s -> new CommonInfo(s.getTaskName(), s.getGmtHeartbeat(), s.getGmtCreated(),
                    s.getVersion(), s.getContainerId()));
        } else {
            infoOptional = dumperInfoMapper.selectOne(
                    s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                        .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(config.getTaskName())))
                .map(s -> new CommonInfo(s.getTaskName(), s.getGmtHeartbeat(), s.getGmtCreated(),
                    s.getVersion(), s.getContainerId()));
        }

        if (infoOptional.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("task info is " + infoOptional.get() + ", now is " + System.currentTimeMillis());
            }

            CommonInfo info = infoOptional.get();
            if (shouldRestartTask(config, infoOptional.get())) {
                MonitorManager.getInstance().triggerAlarm(MonitorType.PROCESS_HEARTBEAT_TIMEOUT_WARNING, info.name);
                restartTask(config, config.getTaskName(), config.getMem());
            }

            if (info.version < config.getVersion()) {
                log.info("task {} version {} < {}, will restart.", config.getTaskName(),
                    info.version, config.getVersion());
                restartTask(config, config.getTaskName(), config.getMem());
            }
        } else {
            tryStartTask(config.getTaskName(), config.getMem(), false);
        }
    }

    boolean shouldRestartTask(BinlogTaskConfig config, CommonInfo commonInfo) throws Exception {
        int heartbeatTimeout = getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS);
        int forceKillTimeout = getInt(DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS);
        long heartbeatInterval = GmsTimeUtil.getHeartbeatInterval(
            config.getRole(), config.getClusterId(), config.getTaskName());

        if (!StringUtils.equals(config.getContainerId(), commonInfo.containerId)) {
            log.info("detect task {} container changed, should restart, {}:{}.", config.getTaskName(),
                config.getContainerId(), commonInfo.containerId);
            return true;
        }

        if (heartbeatInterval > heartbeatTimeout) {
            //心跳超时，但进程还在，一个典型的场景：大数据量场景下GC很频繁，导致cpu使用率很高，Task进程的心跳会出现超时
            if (!isTaskProcessAlive(config.getTaskName())) {
                log.info("detect heartbeat timeout {} ms, task {} is already down, should restart.",
                    heartbeatTimeout, config.getTaskName());
                return true;
            } else {
                if (heartbeatInterval > forceKillTimeout) {
                    log.info("detect heartbeat timeout {} ms, task {} is still alive but exceed the force"
                        + " kill threshold, should force restart.", heartbeatInterval, config.getTaskName());
                    return true;
                } else {
                    log.info("detect heartbeat timeout {} ms, task {} is still alive, should not restart.",
                        heartbeatInterval, config.getTaskName());
                }
            }
        }
        return false;
    }

    void updateTaskStatus(String clusterId, String taskName, String taskType, BinlogTaskStatus status) {
        if (TaskType.isDumper(taskType)) {
            dumperInfoMapper.update(s -> s.set(DumperInfoDynamicSqlSupport.status).equalTo(status.ordinal())
                .where(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskName))
                .and(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
        } else {
            taskInfoMapper.update(s -> s.set(BinlogTaskInfoDynamicSqlSupport.status).equalTo(status.ordinal())
                .where(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskName))
                .and(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
        }
    }

    void stopTasksNotBelongsToThisNode(Set<String> localTasks) throws Exception {
        Set<String> runningTasks = getAllTaskProcess();
        Set<String> whiteList = stopTaskWhitList();

        if (!runningTasks.isEmpty()) {
            for (String runningTask : runningTasks) {
                if (!localTasks.contains(runningTask) && !whiteList.contains(runningTask)) {
                    log.info("prepare to stop task not belongs to this node, task name -> {}.", runningTask);
                    stopTask(runningTask);
                    log.info("stop task not belongs to this node finished, task name -> {}.", runningTask);
                }
            }
        }
    }

    boolean isTaskProcessAlive(String takName) throws Exception {
        Set<String> runningTasks = getAllTaskProcess();
        if (!runningTasks.isEmpty()) {
            for (String runningTask : runningTasks) {
                if (StringUtils.equals(runningTask, takName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获得当前容器内运行的Task以及Dumper的名字
     */
    Set<String> getAllTaskProcess() throws Exception {
        CommandResult result = commandPipeline.execCommand(
            new String[] {
                "bash", "-c",
                "ps -u `whoami` -f | grep 'com.aliyun.polardbx.binlog' | grep -v 'DaemonBootStrap' | grep -v 'grep' |"
                    + " sed 's/.*DtaskName=\\([A-Za-z]*[-]*[0-9]*\\).*/\\1/g'"},
            3000);

        if (result.getCode() == 0) {
            Set<String> runningTaskSet = new HashSet<>(
                Arrays.asList(StringUtils.split(result.getMsg(), System.getProperty("line.separator"))));

            if (log.isDebugEnabled()) {
                log.debug("local running tasks {}", runningTaskSet);
            }
            return runningTaskSet;
        } else {
            log.warn("check local running task fail, {}:{}.", result.getCode(), result.getMsg());
            return new HashSet<>();
        }
    }

    void tryStartTask(String taskName, int mem, boolean restart) throws Exception {
        //improve 这里可以用flock控制
        log.info("prepare to start task {}.", taskName);
        CommandResult result = commandPipeline.execCommand(
            new String[] {"bash", "-c", "ps -ef | grep taskName=" + taskName + " | grep -v grep | wc -l"}, 1000);

        if (log.isDebugEnabled()) {
            log.debug("{} {}: ps check result code={}, count={}", restart ? "Restart" : "Start",
                taskName, result.getCode(), StringUtils.chomp(result.getMsg()));
        }

        if (result.getCode() == 0) {
            int count = Integer.parseInt(StringUtils.getDigits(result.getMsg()));
            switch (count) {
            case 0:
                commandPipeline.startTask(taskName, mem);
                log.warn("task {} is started.", taskName);
                break;
            case 1:
                log.warn("task {} is started or starting, will not start again!", taskName);
                break;
            default:
                log.warn("task {} is repeat started, will force stop!", taskName);
                commandPipeline.stopTask(taskName);
                break;
            }
        }
    }

    void restartTask(BinlogTaskConfig config, String taskName, int mem) throws Exception {
        log.info("prepare to restart task {}.", taskName);
        stopTask(taskName);
        cleanInfo(config);
        tryStartTask(taskName, mem, true);
        log.info("task {} is restarted.", taskName);
    }

    void stopTask(String taskName) throws Exception {
        log.info("prepare to stop task {}.", taskName);
        commandPipeline.stopTask(taskName);
        log.info("task {} is stopped.", taskName);
    }

    void cleanInfo(BinlogTaskConfig config) {
        log.info("prepare to clean task info {}.", config.getTaskName());
        if (TaskType.isTask(config.getRole())) {
            deleteTaskInfo(config.getTaskName());
        } else {
            deleteDumperInfo(config.getTaskName());
        }
        log.info("task info {} is cleaned.", config.getTaskName());
    }

    void deleteDumperInfo(String name) {
        dumperInfoMapper.delete(s ->
            s.where(DumperInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(getString(ConfigKeys.CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
    }

    void deleteTaskInfo(String name) {
        taskInfoMapper.delete(s ->
            s.where(BinlogTaskInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(getString(ConfigKeys.CLUSTER_ID)))
                .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
    }

    void tryCleanResource(Set<String> localTasks) {
        tryCleanRocksDb(getString(STORAGE_PERSIST_BASE_PATH), localTasks);
        tryCleanRocksDb(getString(BINLOGX_ROCKSDB_BASE_PATH), localTasks);
        tryCleanRocksDb2();
        tryCleanRdsBinlog(localTasks);
    }

    void tryCleanRocksDb(String basePath, Set<String> localTasks) {
        try {
            File baseDir = new File(basePath);
            if (baseDir.exists()) {
                File[] files = baseDir.listFiles((dir, name) -> !localTasks.contains(name));
                assert files != null;
                Arrays.stream(files).forEach(f -> {
                    try {
                        FileUtils.forceDelete(f);
                        log.info("rocks db directory {} is cleaned.", f.getAbsolutePath());
                    } catch (IOException e) {
                        throw new PolardbxException("delete failed.", e);
                    }
                });
            }
        } catch (Throwable t) {
            log.error("something goes wrong when clean rocksdb data.", t);
        }
    }

    void tryCleanRocksDb2() {
        // 历史上出现过一个bug，StorageFactory中拼接persistPath的时候，使用了File.pathSeparator，实际上应该使用File.separator，此处做一下兼容性处理
        // String persistPath = getString(STORAGE_PERSIST_BASE_PATH) + File.pathSeparator + getString(ConfigKeys.TASK_NAME) + File.pathSeparator + identifier;
        String path = getString(STORAGE_PERSIST_BASE_PATH);
        String parentPath = StringUtils.substringBeforeLast(path, File.separator);
        String suffix = StringUtils.substringAfterLast(path, File.separator);

        File parentDir = new File(parentPath);
        if (parentDir.exists()) {
            File[] files = parentDir.listFiles((dir, name) -> name.startsWith(suffix + File.pathSeparator));
            if (files != null) {
                Arrays.stream(files).forEach(f -> {
                    try {
                        FileUtils.forceDelete(f);
                        log.info("rocks db directory {} is cleaned.", f.getAbsolutePath());
                    } catch (IOException e) {
                        throw new PolardbxException("delete failed.", e);
                    }
                });
            }
        }
    }

    void tryCleanRdsBinlog(Set<String> localTasks) {
        try {
            String basePath = getString(TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR);
            File baseDir = new File(basePath);
            if (baseDir.exists()) {
                File[] files = baseDir
                    .listFiles((dir, name) -> !localTasks.contains(name) && !StringUtils.equals("__test__", name));
                assert files != null;
                Arrays.stream(files).forEach(f -> {
                    try {
                        FileUtils.forceDelete(f);
                        log.info("rds binlog directory {} is cleaned.", f.getAbsolutePath());
                    } catch (IOException e) {
                        throw new PolardbxException("delete failed.", e);
                    }
                });
            }
        } catch (Throwable t) {
            log.error("something goes wrong when clean rds binlog data.", t);
        }
    }

    Set<String> stopTaskWhitList() {
        String whitListStr = getString(DAEMON_WATCH_WORK_PROCESS_BLACKLIST);
        if (StringUtils.isNotBlank(whitListStr)) {
            return Sets.newHashSet(StringUtils.split(whitListStr, ","));
        }
        return Sets.newHashSet();
    }

    static class CommonInfo {
        String name;
        Date heartbeatTime;
        Date startTime;
        long version;
        String containerId;

        public CommonInfo(String name, Date heartbeatTime, Date startTime, Long version, String containerId) {
            this.name = name;
            this.heartbeatTime = heartbeatTime;
            this.startTime = startTime;
            this.version = version;
            this.containerId = containerId;
        }

        @Override
        public String toString() {
            return "CommonInfo{" +
                "name='" + name + '\'' +
                ", heartbeatTime=" + heartbeatTime +
                ", startTime=" + startTime +
                ", version=" + version +
                ", containerId='" + containerId + '\'' +
                '}';
        }
    }
}
