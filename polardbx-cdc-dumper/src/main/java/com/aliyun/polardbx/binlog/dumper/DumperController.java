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
package com.aliyun.polardbx.binlog.dumper;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
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
import com.aliyun.polardbx.binlog.dumper.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.rpc.EndPoint;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

/**
 * @author ziyang.lb, yudong
 **/
public class DumperController {

    private static final Logger logger = LoggerFactory.getLogger(DumperController.class);

    private final TaskRuntimeConfig taskRuntimeConfig;
    private LogFileManagerCollection logFileManagerCollection;
    private CdcServer cdcServer;
    private MetricsManager metricsManager;
    private String role;
    private String groupName;
    private List<String> streamList;
    private BinlogBackupManager backupManager;
    private BinlogCleanManager cleanManager;
    private volatile boolean running;

    public DumperController(TaskConfigProvider taskConfigProvider) {
        this.taskRuntimeConfig = taskConfigProvider.getTaskRuntimeConfig();
        MonitorManager.getInstance().startup();
        this.build();
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        this.cleanManager.start();
        this.logFileManagerCollection.start();
        // 需要保证logFileManager启动之后再启动backupManager
        this.backupManager.start();
        this.cdcServer.start();
        this.metricsManager.start();
        logger.info("Dumper controller started({}).", role);
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        this.logFileManagerCollection.stop();
        this.cdcServer.stop();
        this.metricsManager.stop();
        this.cleanManager.stop();
        this.backupManager.stop();
        logger.info("Dumper controller stopped.");
    }

    /**
     * 单流的group name和stream name不再设置为null，方便下游代码统一
     */
    private void setGroupAndStream() {
        TaskType taskType = taskRuntimeConfig.getType();
        switch (taskType) {
        case Dumper:
            groupName = GROUP_NAME_GLOBAL;
            streamList = Collections.singletonList(STREAM_NAME_GLOBAL);
            break;
        case DumperX:
            groupName = getString(BINLOGX_STREAM_GROUP_NAME);
            ExecutionConfig executionConfig = JSONObject.parseObject(
                taskRuntimeConfig.getBinlogTaskConfig().getConfig(), ExecutionConfig.class);
            streamList = new ArrayList<>(executionConfig.getStreamNameSet());
            break;
        default:
            throw new PolardbxException("invalid task type " + taskType);
        }
    }

    private void build() {
        setGroupAndStream();
        tryRenameBinlogRootPath();
        buildLogFileManagerCollection();
        Map<String, MetricsObserver> metrics = new HashMap<>();
        streamList.forEach(streamId -> metrics.put(streamId, StreamMetrics.getStreamMetrics(streamId)));
        this.backupManager = new BinlogBackupManager(buildStreamContext(), metrics);
        this.cleanManager = new BinlogCleanManager(buildStreamContext());
        this.metricsManager = new MetricsManager(taskRuntimeConfig.getName(), taskRuntimeConfig.getType());
        this.cdcServer = new CdcServer(taskRuntimeConfig.getName(), logFileManagerCollection,
            taskRuntimeConfig.getServerPort(), taskRuntimeConfig.getBinlogTaskConfig(), metricsManager);
        this.updateDumperInfo(taskRuntimeConfig);
    }

    /**
     * 多流场景下，每个Dumper可能负责多个流，每个流需要一个logFileManager管理
     * LogFileManagerCollection保存当前Dumper的所有LogFileManager
     */
    private void buildLogFileManagerCollection() {
        ExecutionConfig config = JSONObject.parseObject(
            taskRuntimeConfig.getBinlogTaskConfig().getConfig(), ExecutionConfig.class);
        this.logFileManagerCollection = new LogFileManagerCollection();
        streamList.forEach(streamName -> {
            logFileManagerCollection.add(streamName, buildLogFileManager(config, streamName));
        });
    }

    private LogFileManager buildLogFileManager(ExecutionConfig executionConfig, String streamName) {
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
        return logFileManager;
    }

    private void buildRole() {
        if (taskRuntimeConfig.getType() == TaskType.Dumper) {
            boolean dumperLeader = RuntimeLeaderElector.isDumperLeader(taskRuntimeConfig.getName());
            role = dumperLeader ? DumperType.MASTER.getName() : DumperType.SLAVE.getName();
        } else if (taskRuntimeConfig.getType() == TaskType.DumperX) {
            role = DumperType.XSTREAM.getName();
        } else {
            throw new PolardbxException("invalid task type " + taskRuntimeConfig.getType());
        }
    }

    private void updateDumperInfo(TaskRuntimeConfig taskRuntimeConfig) {
        this.buildRole();
        ExecutionConfig executionConfig =
            JSONObject.parseObject(taskRuntimeConfig.getBinlogTaskConfig().getConfig(), ExecutionConfig.class);

        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);

        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setClusterId(getString(CLUSTER_ID));
        dumperInfo.setTaskName(taskRuntimeConfig.getName());
        dumperInfo.setIp(getString(INST_IP));
        dumperInfo.setContainerId(getString(INST_ID));
        dumperInfo.setPort(taskRuntimeConfig.getServerPort());
        dumperInfo.setVersion(taskRuntimeConfig.getBinlogTaskConfig().getVersion());
        dumperInfo.setRole(role);
        dumperInfo.setStatus(0);
        dumperInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));

        Optional<DumperInfo> dumperInfoInDb = dumperInfoMapper.selectOne(
            s -> s.where(DumperInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(getString(CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskRuntimeConfig.getName())));
        if (dumperInfoInDb.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(getString(RUNTIME_MODE));
            if (dumperInfoInDb.get().getVersion() == 0 || RuntimeMode.isLocalMode(runtimeMode)) {
                dumperInfo.setId(dumperInfoInDb.get().getId());
                dumperInfoMapper.updateByPrimaryKeySelective(dumperInfo);
            } else {
                logger.error("Duplicate dumper info in database : {}", JSONObject.toJSONString(dumperInfoInDb));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                transactionTemplate.execute(t -> {
                    dumperInfoMapper.insert(dumperInfo);
                    if (executionConfig.getStreamNameSet() != null) {
                        executionConfig.getStreamNameSet().forEach(s -> {
                            EndPoint endPoint = new EndPoint(dumperInfo.getIp(), dumperInfo.getPort());
                            xStreamMapper.update(
                                u -> u.set(XStreamDynamicSqlSupport.endpoint)
                                    .equalTo(JSONObject.toJSONString(endPoint))
                                    .where(XStreamDynamicSqlSupport.streamName, SqlBuilder.isEqualTo(s)));
                        });
                    }
                    return null;
                });
            } catch (DuplicateKeyException e) {
                logger.error("Duplicate dumper info in database, insert failed.", e);
                Runtime.getRuntime().halt(1);
            }
        }
    }

    /**
     * 版本号发生改变，重命名binlog root path
     */
    @SneakyThrows
    private void tryRenameBinlogRootPath() {
        if (taskRuntimeConfig.getType() == TaskType.DumperX) {
            ExecutionConfig executionConfig = JSONObject.parseObject(
                taskRuntimeConfig.getBinlogTaskConfig().getConfig(), ExecutionConfig.class);
            if (!executionConfig.isNeedCleanBinlogOfPreVersion()) {
                long currentVersion = executionConfig.getRuntimeVersion();
                String preRootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, currentVersion - 1);
                String currentRootPath = BinlogFileUtil.getRootPath(TaskType.DumperX, currentVersion);
                File preBinlogDir = new File(preRootPath);
                File currentBinlogDir = new File(currentRootPath);
                if (preBinlogDir.exists() && !currentBinlogDir.exists()) {
                    FileUtils.moveDirectory(preBinlogDir, currentBinlogDir);
                    logger.info("binlog files is moved from {} to {}.", preBinlogDir, currentBinlogDir);
                }
            }
        }
    }

    public LogFileManagerCollection getLogFileManagerCollection() {
        return logFileManagerCollection;
    }

    private StreamContext buildStreamContext() {
        return new StreamContext(groupName, streamList, getString(CLUSTER_ID), taskRuntimeConfig.getName(),
            taskRuntimeConfig.getType(), taskRuntimeConfig.getBinlogTaskConfig().getVersion());
    }
}
