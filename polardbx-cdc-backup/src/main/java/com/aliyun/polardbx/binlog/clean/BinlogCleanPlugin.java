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
package com.aliyun.polardbx.binlog.clean;

import com.aliyun.polardbx.binlog.Constants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.clean.barrier.ICleanerBarrier;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.plugin.IDumperPlugin;
import com.aliyun.polardbx.binlog.plugin.PluginContext;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CHECK_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CLEAN_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CLEAN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.DISK_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getDouble;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;

@Component
public class BinlogCleanPlugin implements IDumperPlugin {

    private static final Logger logger = LoggerFactory.getLogger(BinlogCleanPlugin.class);
    private static final long MAX_BINLOG_SIZE = 500 * 1024 * 1024 * 1024L;

    private long maxBinlogSize;
    private ScheduledExecutorService executorService;
    private Map<String, BinlogCleaner> cleanerMap;
    private OldVersionBinlogCleaner oldVersionBinlogCleaner;
    private String taskName;

    @Override
    public void init(PluginContext context) {
        double cleanThreshold = getDouble(BINLOG_CLEANER_CLEAN_THRESHOLD);
        this.cleanerMap = Maps.newConcurrentMap();
        this.maxBinlogSize = Math.min((long) (getLong(DISK_SIZE) * cleanThreshold) * 1024 * 1024, MAX_BINLOG_SIZE);
        this.taskName = context.getTaskName();
        String group = context.getGroup();
        switch (context.getTaskType()) {
        case Dumper:
            this.cleanerMap.put(
                Constants.STREAM_NAME_GLOBAL,
                new BinlogCleaner(
                    BinlogFileUtil.getBinlogFileFullPath(context.getBinlogFileRootPath(), group,
                        Constants.STREAM_NAME_GLOBAL),
                    group,
                    Constants.STREAM_NAME_GLOBAL,
                    maxBinlogSize));
            break;
        case DumperX:
            long singleMaxSize = maxBinlogSize / context.getStreamList().size();
            for (String stream : context.getStreamList()) {
                this.cleanerMap.put(stream, new BinlogCleaner(
                    BinlogFileUtil.getBinlogFileFullPath(context.getBinlogFileRootPath(), group, stream), group,
                    stream, singleMaxSize));
            }
            this.oldVersionBinlogCleaner = new OldVersionBinlogCleaner(context.getRuntimeVersion());
            break;
        default:
            throw new PolardbxException("invalid task type " + context.getTaskType());
        }
        logger.info("local max binlog size : " + maxBinlogSize);
    }

    @Override
    public void start(PluginContext context) {
        logger.info("start binlog cleaner ... with max binlog size " + maxBinlogSize);
        if (context.getTaskType().equals(TaskType.DumperX) || RuntimeLeaderElector.isDumperLeader(taskName)) {
            if (RemoteBinlogProxy.getInstance().isBackupOn()) {
                for (BinlogCleaner entry : cleanerMap.values()) {
                    entry.initDefaultBarrier();
                }
                logger.info("add clean barrier ...");
            }
        }
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binlog-cleaner");
            t.setDaemon(true);
            return t;
        });
        int interval = DynamicApplicationConfig.getInt(BINLOG_CLEANER_CHECK_INTERVAL);
        executorService.scheduleAtFixedRate(this::scan, interval, interval, TimeUnit.MINUTES);
    }

    @Override
    public void stop(PluginContext context) {
        logger.info("shutdown cleaner ...");
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    public void addBarrier(String stream, ICleanerBarrier barrier) {
        cleanerMap.get(stream).addBarrier(barrier);
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    private void purgeLocal() {
        for (BinlogCleaner cleaner : cleanerMap.values()) {
            cleaner.purgeLocal();
        }
    }

    private void purgeRemote() {
        for (BinlogCleaner cleaner : cleanerMap.values()) {
            cleaner.purgeRemote();
        }
    }

    // 本地磁盘上的binlog文件的清理策略是按磁盘使用量
    // backup系统中binlog文件的清理策略是按配置的保存天数
    // 可能会出现backup中的文件已经被清理掉了，但本地磁盘还未被清理的情况
    public void scan() {
        try {
            purgeRemote();
        } catch (Throwable e) {
            logger.error("purge oss binlog occur exception!", e);
        }
        try {
            boolean enable = DynamicApplicationConfig.getBoolean(BINLOG_CLEANER_CLEAN_ENABLE);
            if (enable) {
                purgeLocal();
            }
        } catch (Throwable e) {
            logger.error("purge binlog occur exception!", e);
        }
        try {
            if (oldVersionBinlogCleaner != null) {
                oldVersionBinlogCleaner.purge();
            }
        } catch (Throwable e) {
            logger.error("purge old version binlog error!", e);
        }
    }
}
