/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.backup.BinlogBackupManager;
import com.aliyun.polardbx.binlog.backup.MetricsObserver;
import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.clean.BinlogCleanManager;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.DumperType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.FlushPolicy;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManager;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManagerCollection;
import com.aliyun.polardbx.binlog.dumper.dump.util.VersionMeta;
import com.aliyun.polardbx.binlog.dumper.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.lock.LogFileLockManager;
import com.aliyun.polardbx.binlog.lock.LogFileLockManagerCollection;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.rpc.EndPoint;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_DRY_RUN_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_FLUSH_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_FLUSH_POLICY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static com.aliyun.polardbx.binlog.ConfigKeys.RUNTIME_MODE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.listFilesOfStreamGroup;

/**
 * @author ziyang.lb, yudong
 **/
@Slf4j
public class DumperController {
    @Getter
    private TaskRuntimeConfig taskRuntimeConfig;
    private ExecutionConfig executionConfig;
    @Getter
    private LogFileManagerCollection logFileManagerCollection;
    private LogFileLockManagerCollection logFileLockManagerCollection;
    private CdcServer cdcServer;
    private MetricsManager metricsManager;
    private String role;
    private String groupName;
    private Set<String> streamSet;
    private BinlogBackupManager backupManager;
    private BinlogCleanManager cleanManager;
    @Getter
    private volatile boolean running;

    public DumperController(TaskRuntimeConfig taskRuntimeConfig) {
        this.taskRuntimeConfig = taskRuntimeConfig;
        this.executionConfig = taskRuntimeConfig.getExecutionConfig();
        this.init();
    }

    public void start() {
        if (running) {
            return;
        }
        doStart();
        running = true;
    }

    public void stop() {
        if (!running) {
            return;
        }
        doStop();
        running = false;
    }

    private void doStart() {
        log.info("## dumper controller start begin {}:{} ...",
            executionConfig.getRuntimeVersion(), executionConfig.getSubRuntimeVersion());

        MonitorManager.getInstance().startup();

        // 暂时这个start()将会几乎啥都不干
        this.logFileLockManagerCollection.start();
        this.cleanManager.start();
        this.logFileManagerCollection.start();
        // 需要保证logFileManager启动之后再启动backupManager
        this.backupManager.start();
        this.cdcServer.start();
        this.metricsManager.start();

        log.info("## dumper controller start end ...");
    }

    private void doStop() {
        log.info("## dumper controller stop begin.");
        this.logFileManagerCollection.stop();
        this.cdcServer.stop();
        this.metricsManager.stop();
        this.cleanManager.stop();
        this.backupManager.stop();
        log.info("## dumper controller stop end.");
    }

    public void reloadForMultiStream(TaskRuntimeConfig taskRuntimeConfig) {
        log.info("## dumper controller reload begin ...");
        reloadCommon(taskRuntimeConfig);

        Set<String> currentStreams = executionConfig.getStreamNameSet();
        Set<String> previousStreams = logFileManagerCollection.streamSet();
        Set<String> addedStreams = currentStreams.stream()
            .filter(s -> !previousStreams.contains(s)).collect(Collectors.toSet());
        Set<String> removedStreams = previousStreams.stream()
            .filter(s -> !currentStreams.contains(s)).collect(Collectors.toSet());
        Set<String> remainingStreams = currentStreams.stream()
            .filter(previousStreams::contains).collect(Collectors.toSet());

        // stop
        for (String streamName : removedStreams) {
            this.logFileManagerCollection.stop(streamName);
            this.logFileLockManagerCollection.stop(streamName);
            this.backupManager.stop(streamName);
            this.cleanManager.stop(streamName);
            this.cdcServer.stop(streamName);
            this.logFileManagerCollection.clean(streamName);
            StreamMetrics.remove(streamName);
        }

        // prepare before start
        StreamContext streamContext = buildStreamContext();
        HashMap<String, LogFileManager> toStartLogFileManager = new HashMap<>();
        HashMap<String, LogFileLockManager> toStartLogFileLockManager = new HashMap<>();
        HashMap<String, MetricsObserver> toStartMetricsObserver = new HashMap<>();

        for (String streamName : addedStreams) {
            toStartLogFileLockManager.put(streamName,
                new LogFileLockManager(streamName, taskRuntimeConfig.getType(),
                    executionConfig.getRuntimeVersion(), groupName));
            toStartLogFileManager.put(streamName,
                buildLogFileManager(streamName, toStartLogFileLockManager.get(streamName)));
            toStartMetricsObserver.put(streamName, StreamMetrics.getStreamMetrics(streamName));
        }

        // start
        logFileLockManagerCollection.start(toStartLogFileLockManager);
        cleanManager.start(addedStreams, streamContext, logFileLockManagerCollection);
        logFileManagerCollection.start(toStartLogFileManager);
        backupManager.start(addedStreams, toStartMetricsObserver);
        updateStreamEndpoint(getDumperInfo().get());

        // refresh/restart remaining
        if (HashLevel.getCurrentHashLevel() != HashLevel.DATANODE) {
            for (String streamName : remainingStreams) {
                logFileManagerCollection.refreshAndRestart(streamName, executionConfig);
            }
        } else {
            for (String streamName : remainingStreams) {
                logFileManagerCollection.refresh(streamName, executionConfig);
            }
        }

        updateVersionMeta();
        log.info("## dumper controller reload end ...");
    }

    public void reloadForSingleStream(TaskRuntimeConfig taskRuntimeConfig) {
        log.info("## dumper controller reload begin ...");
        reloadCommon(taskRuntimeConfig);
        logFileManagerCollection.refresh(STREAM_NAME_GLOBAL, executionConfig);
        log.info("## dumper controller reload end ...");
    }

    private void reloadCommon(TaskRuntimeConfig taskRuntimeConfig) {
        this.taskRuntimeConfig = taskRuntimeConfig;
        this.executionConfig = taskRuntimeConfig.getExecutionConfig();
        this.setGroupAndStream();
    }

    /**
     * 单流的group name和stream name不再设置为null，方便下游代码统一
     */
    private void setGroupAndStream() {
        TaskType taskType = taskRuntimeConfig.getType();
        switch (taskType) {
        case Dumper:
            groupName = GROUP_NAME_GLOBAL;
            streamSet = Sets.newTreeSet(Sets.newHashSet(STREAM_NAME_GLOBAL));
            break;
        case DumperX:
            groupName = getString(BINLOGX_STREAM_GROUP_NAME);
            streamSet = new TreeSet<>(executionConfig.getStreamNameSet());
            break;
        default:
            throw new PolardbxException("invalid task type " + taskType);
        }
    }

    private void init() {
        log.info("## dumper controller init begin with version {}:{} ...",
            executionConfig.getRuntimeVersion(), executionConfig.getSubRuntimeVersion());

        setGroupAndStream();
        tryRenameBinlogRootPath();
        tryCleanStreamFilesNotBelongsToMe();
        buildLogFileLockManagerCollection();
        buildLogFileManagerCollection();

        this.backupManager = buildBinlogBackupManager();
        this.metricsManager = buildMetricsManager();
        this.cleanManager = new BinlogCleanManager(buildStreamContext(), logFileLockManagerCollection);
        this.cdcServer = buildCdcServer();
        this.updateDumperInfo(taskRuntimeConfig);

        log.info("## dumper controller init end ...");
    }

    private BinlogBackupManager buildBinlogBackupManager() {
        Map<String, MetricsObserver> metrics = new HashMap<>();
        this.streamSet.forEach(streamId -> metrics.put(streamId, StreamMetrics.getStreamMetrics(streamId)));
        return new BinlogBackupManager(buildStreamContext(), metrics);
    }

    private MetricsManager buildMetricsManager() {
        return new MetricsManager(
            executionConfig.getRuntimeVersion(),
            taskRuntimeConfig.getName(),
            taskRuntimeConfig.getType());
    }

    private CdcServer buildCdcServer() {
        return new CdcServer(
            executionConfig.getRuntimeVersion(),
            taskRuntimeConfig.getType(),
            taskRuntimeConfig.getName(),
            logFileManagerCollection,
            taskRuntimeConfig.getServerPort(),
            taskRuntimeConfig.getBinlogTaskConfig(),
            metricsManager);
    }

    /**
     * 多流场景下，每个Dumper可能负责多个流，每个流需要一个logFileManager管理
     * LogFileManagerCollection保存当前Dumper的所有LogFileManager
     */
    private void buildLogFileManagerCollection() {
        this.logFileManagerCollection = new LogFileManagerCollection();
        this.streamSet.forEach(streamName -> {
            logFileManagerCollection.add(streamName,
                buildLogFileManager(streamName, logFileLockManagerCollection.get(streamName)));
        });
    }

    private LogFileManager buildLogFileManager(String streamName, LogFileLockManager logFileLockManager) {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setTaskName(taskRuntimeConfig.getName());
        logFileManager.setTaskType(taskRuntimeConfig.getType());
        logFileManager.setGroupName(groupName);
        logFileManager.setExecutionConfig(executionConfig);
        logFileManager.setBinlogRootPath(BinlogFileUtil.getRootPath(taskRuntimeConfig.getType(),
            taskRuntimeConfig.getBinlogTaskConfig().getVersion()));
        logFileManager.setBinlogFileSize(DynamicApplicationConfig.getInt(BINLOG_FILE_SIZE));
        logFileManager.setDryRun(DynamicApplicationConfig.getBoolean(BINLOG_WRITE_DRY_RUN_ENABLE));
        logFileManager.setFlushPolicy(
            FlushPolicy.parseFrom(DynamicApplicationConfig.getInt(BINLOG_WRITE_FLUSH_POLICY)));
        logFileManager.setFlushInterval(DynamicApplicationConfig.getInt(BINLOG_WRITE_FLUSH_INTERVAL));
        logFileManager.setWriteBufferSize(DynamicApplicationConfig.getInt(BINLOG_WRITE_BUFFER_SIZE));
        logFileManager.setStreamName(streamName);
        logFileManager.setLogFileLockManager(logFileLockManager);
        return logFileManager;
    }

    private void buildLogFileLockManagerCollection() {
        this.logFileLockManagerCollection = new LogFileLockManagerCollection();
        this.streamSet.forEach(streamName ->
            logFileLockManagerCollection.add(streamName,
                new LogFileLockManager(streamName, taskRuntimeConfig.getType(),
                    executionConfig.getRuntimeVersion(), groupName))
        );
    }

    private void buildRole() {
        if (taskRuntimeConfig.getType() == TaskType.Dumper) {
            boolean dumperLeader =
                RuntimeLeaderElector.isDumperMaster(executionConfig.getRuntimeVersion(), taskRuntimeConfig.getName());
            role = dumperLeader ? DumperType.MASTER.getName() : DumperType.SLAVE.getName();
        } else if (taskRuntimeConfig.getType() == TaskType.DumperX) {
            role = DumperType.XSTREAM.getName();
        } else {
            throw new PolardbxException("invalid task type " + taskRuntimeConfig.getType());
        }
    }

    private void updateDumperInfo(TaskRuntimeConfig taskRuntimeConfig) {
        this.buildRole();

        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);

        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setClusterId(getString(CLUSTER_ID));
        dumperInfo.setTaskName(taskRuntimeConfig.getName());
        dumperInfo.setIp(getString(INST_IP));
        dumperInfo.setContainerId(getString(INST_ID));
        dumperInfo.setPort(taskRuntimeConfig.getServerPort());
        dumperInfo.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        dumperInfo.setSubVersion(taskRuntimeConfig.getBinlogTaskConfig().getSubVersion());
        dumperInfo.setRole(role);
        dumperInfo.setStatus(0);
        dumperInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        dumperInfo.setEnableLightRebalance(true);

        Optional<DumperInfo> dumperInfoInDb = getDumperInfo();
        if (dumperInfoInDb.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(getString(RUNTIME_MODE));
            if (dumperInfoInDb.get().getVersion() == 0 || RuntimeMode.isLocalMode(runtimeMode)) {
                dumperInfo.setId(dumperInfoInDb.get().getId());
                dumperInfoMapper.updateByPrimaryKeySelective(dumperInfo);
            } else {
                log.error("duplicate dumper info in database : {}", JSONObject.toJSONString(dumperInfoInDb));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                transactionTemplate.execute(t -> {
                    dumperInfoMapper.insert(dumperInfo);
                    updateStreamEndpoint(dumperInfo);
                    return null;
                });
            } catch (DuplicateKeyException e) {
                log.error("Duplicate dumper info in database, insert failed.", e);
                Runtime.getRuntime().halt(1);
            }
        }
    }

    private Optional<DumperInfo> getDumperInfo() {
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        return dumperInfoMapper.selectOne(
            s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(getString(CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskRuntimeConfig.getName())));
    }

    private void updateStreamEndpoint(DumperInfo dumperInfo) {
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        if (executionConfig.getStreamNameSet() != null) {
            executionConfig.getStreamNameSet().forEach(s -> {
                EndPoint endPoint = new EndPoint(dumperInfo.getIp(), dumperInfo.getPort());
                xStreamMapper.update(
                    u -> u.set(XStreamDynamicSqlSupport.endpoint)
                        .equalTo(JSONObject.toJSONString(endPoint))
                        .where(XStreamDynamicSqlSupport.streamName, SqlBuilder.isEqualTo(s)));
            });
        }
    }

    /**
     * 版本号发生改变，重命名binlog root path
     */
    @SneakyThrows
    private void tryRenameBinlogRootPath() {
        if (taskRuntimeConfig.getType() == TaskType.DumperX && !executionConfig.isNeedCleanBinlogOfPreVersion()) {

            long currentVersion = executionConfig.getRuntimeVersion();
            String preRootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, currentVersion - 1);
            String currentRootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, currentVersion);
            File preBinlogDir = new File(preRootPath);
            File currentBinlogDir = new File(currentRootPath);

            if (preBinlogDir.exists() && !currentBinlogDir.exists()) {
                FileUtils.moveDirectory(preBinlogDir, currentBinlogDir);
                updateVersionMeta();
                log.info("binlog files is moved from {} to {}.", preBinlogDir, currentBinlogDir);
            }
        }
    }

    private void tryCleanStreamFilesNotBelongsToMe() {
        if (taskRuntimeConfig.getType() == TaskType.DumperX) {
            final boolean cleanAll = isCleanAll();
            long version = executionConfig.getRuntimeVersion();
            String rootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, version);
            List<File> fileList = listFilesOfStreamGroup(rootPath, groupName);

            fileList.forEach(file -> {
                if (cleanAll || !streamSet.contains(file.getName())) {
                    try {
                        FileUtils.forceDelete(file);
                    } catch (IOException e) {
                        throw new RuntimeException("ERROR: failed to delete " + file.getName(), e);
                    }
                    log.info("file or directory is cleaned, because it is not in stream set or need clean all, "
                        + "clean all flag is {}, stream set is {}, file: {}", cleanAll, streamSet, file.getName());
                }
            });
            updateVersionMeta();
        }
    }

    private boolean isCleanAll() {
        VersionMeta versionMeta = VersionMeta.query(executionConfig.getRuntimeVersion());
        boolean cleanAll = false;

        if (versionMeta == null) {
            cleanAll = true;
        } else {
            if (versionMeta.getVersion() != executionConfig.getRuntimeVersion()) {
                cleanAll = true;
            } else {
                if ((executionConfig.getSubRuntimeVersion() != versionMeta.getSubVersion()) && (
                    executionConfig.getSubRuntimeVersion() - versionMeta.getSubVersion() != 1)) {
                    cleanAll = true;
                }
            }
        }
        return cleanAll;
    }

    @SneakyThrows
    private void updateVersionMeta() {
        VersionMeta.builder().version(executionConfig.getRuntimeVersion())
            .subVersion(executionConfig.getSubRuntimeVersion())
            .streamSet(executionConfig.getStreamNameSet()).build().update();

        log.info("update version meta, version: {}, subVersion: {}, streamSet: {}", executionConfig.getRuntimeVersion(),
            executionConfig.getSubRuntimeVersion(), executionConfig.getStreamNameSet());
    }

    private StreamContext buildStreamContext() {
        return new StreamContext(groupName, streamSet, getString(CLUSTER_ID), taskRuntimeConfig.getName(),
            taskRuntimeConfig.getType(), taskRuntimeConfig.getBinlogTaskConfig().getVersion());
    }

}
