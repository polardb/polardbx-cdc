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

package com.aliyun.polardbx.binlog.dumper;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TaskInfoProvider;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.DumperType;
import com.aliyun.polardbx.binlog.domain.TaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.FlushPolicy;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManager;
import com.aliyun.polardbx.binlog.dumper.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.util.Date;
import java.util.Optional;

/**
 * Created by ziyang.lb
 **/
public class DumperController {

    private final static Logger logger = LoggerFactory.getLogger(DumperController.class);

    private final TaskInfoProvider taskInfoProvider;
    private LogFileManager logFileManager;
    private CdcServer cdcServer;
    private MetricsManager metricsManager;
    private volatile boolean running;
    private volatile boolean leader;

    public DumperController(TaskInfoProvider taskInfoProvider) {
        this.taskInfoProvider = taskInfoProvider;
        MonitorManager.getInstance().startup();
        this.build();
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        TaskInfo taskInfo = taskInfoProvider.get();
        this.logFileManager.start();
        this.cdcServer.start();
        this.metricsManager.start();
        logger.info("Dumper controller started({}).", leader ? "M" : "S");
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        this.logFileManager.stop();
        this.cdcServer.stop();
        this.metricsManager.stop();
        logger.info("Dumper controller stopped.");
    }

    private void build() {
        this.logFileManager = new LogFileManager();
        this.logFileManager.setTaskName(taskInfoProvider.get().getName());
        this.logFileManager.setBinlogFileDirPath(DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH));
        this.logFileManager.setBinlogFileSize(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_FILE_SIZE));
        this.logFileManager.setDryRun(DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_WRITE_DRYRUN));
        this.logFileManager
            .setFlushPolicy(
                FlushPolicy.parseFrom(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_WRITE_FLUSH_POLICY)));
        this.logFileManager.setFlushInterval(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_WRITE_FLUSH_INTERVAL));
        this.logFileManager.setWriteBufferSize(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_WRITE_BUFFER_SIZE));
        this.logFileManager.setSeekBufferSize(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE));

        this.cdcServer = new CdcServer(taskInfoProvider.get().getName(), logFileManager,
            taskInfoProvider.get().getServerPort());

        this.metricsManager = new MetricsManager(RuntimeLeaderElector.isDumperLeader(taskInfoProvider.get().getName()));
        updateDumperInfo(taskInfoProvider.get());
    }

    private DumperInfo updateDumperInfo(TaskInfo taskInfo) {
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setClusterId(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
        dumperInfo.setTaskName(taskInfo.getName());
        dumperInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        dumperInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        dumperInfo.setPort(taskInfo.getServerPort());
        dumperInfo.setVersion(taskInfo.getBinlogTaskConfig().getVersion());
        boolean dumperLeader = RuntimeLeaderElector.isDumperLeader(taskInfo.getName());
        dumperInfo.setRole(dumperLeader ? DumperType.MASTER.getName() : DumperType.SLAVE.getName());
        leader = dumperLeader;
        dumperInfo.setStatus(0);
        Date now = new Date();
        dumperInfo.setGmtHeartbeat(now);
        dumperInfo.setGmtCreated(now);
        dumperInfo.setGmtModified(now);

        Optional<DumperInfo> dumperInfoInDB = dumperInfoMapper.selectOne(
            s -> s.where(DumperInfoDynamicSqlSupport.clusterId,
                SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskInfo.getName())));
        if (dumperInfoInDB.isPresent()) {
            // 兼容一下老版调度引擎的逻辑，如果version为0，进行更新
            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (dumperInfoInDB.get().getVersion() == 0 || runtimeMode == RuntimeMode.LOCAL) {
                dumperInfo.setId(dumperInfoInDB.get().getId());
                dumperInfoMapper.updateByPrimaryKey(dumperInfo);
            } else {
                logger.error("Duplicate dumper info in database : {}", JSONObject.toJSONString(dumperInfoInDB));
                Runtime.getRuntime().halt(1);
            }
        } else {
            try {
                dumperInfoMapper.insert(dumperInfo);
            } catch (DuplicateKeyException e) {
                logger.error("Duplicate dumper info in database, insert failed.", e);
                Runtime.getRuntime().halt(1);
            }
        }
        return dumperInfo;
    }

    public LogFileManager getLogFileManager() {
        return logFileManager;
    }
}
