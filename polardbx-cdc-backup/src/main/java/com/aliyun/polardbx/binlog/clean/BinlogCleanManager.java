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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.domain.TaskType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PURGE_CHECK_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_KEY;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_CLEAN;

/**
 * @author chengjin, yudong
 */
public class BinlogCleanManager {
    private static final Logger logger = LoggerFactory.getLogger(BinlogCleanManager.class);
    private ScheduledExecutorService executor;
    private final List<BinlogCleaner> cleaners;
    private OldVersionBinlogCleaner oldVersionBinlogCleaner;

    public BinlogCleanManager(StreamContext context) {
        this.cleaners = new ArrayList<>();
        for (String stream : context.getStreamList()) {
            this.cleaners.add(new BinlogCleaner(stream, context));
        }

        if (context.getTaskType() == TaskType.DumperX) {
            this.oldVersionBinlogCleaner = new OldVersionBinlogCleaner(context.getVersion());
        }
    }

    public void start() {
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "binlog-cleaner");
            t.setDaemon(true);
            return t;
        });
        int interval = DynamicApplicationConfig.getInt(BINLOG_PURGE_CHECK_INTERVAL_MINUTE);
        executor.scheduleAtFixedRate(this::doClean, interval, interval, TimeUnit.MINUTES);
        cleanBinlogDumpDir();
    }

    public void stop() {
        logger.info("shutdown cleaner ...");
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void doClean() {
        try {
            MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_CLEAN);
            tryCleanRemoteBinlog();
            tryCleanLocalBinlog();
            tryCleanOldVersionBinlog();
        } finally {
            MDC.remove(MDC_THREAD_LOGGER_KEY);
        }
    }

    private void tryCleanLocalBinlog() {
        try {
            for (BinlogCleaner cleaner : cleaners) {
                cleaner.cleanLocalFiles();
            }
        } catch (Throwable e) {
            logger.error("purge local binlog error!", e);
        }
    }

    private void tryCleanRemoteBinlog() {
        try {
            for (BinlogCleaner cleaner : cleaners) {
                cleaner.purgeRemote();
            }
        } catch (Throwable e) {
            logger.error("purge remote binlog error!", e);
        }
    }

    private void tryCleanOldVersionBinlog() {
        try {
            if (oldVersionBinlogCleaner != null) {
                oldVersionBinlogCleaner.purge();
            }
        } catch (Throwable e) {
            logger.error("purge old version binlog error!", e);
        }
    }

    private void cleanBinlogDumpDir() {
        String path = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH);
        logger.info("cleaning up binlog dump download path:{}", path);
        try {
            File f = new File(path);
            if (f.exists()) {
                FileUtils.forceDelete(f);
            }
        } catch (IOException e) {
            logger.error("delete download path:{} failed!", path, e);
        }
    }

}
