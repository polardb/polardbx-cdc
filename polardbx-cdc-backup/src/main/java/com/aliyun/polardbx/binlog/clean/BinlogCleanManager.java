/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.lock.LogFileLockManagerCollection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PURGE_CHECK_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_KEY;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_CLEAN;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author chengjin, yudong
 */
@Slf4j
public class BinlogCleanManager {
    private ScheduledExecutorService executor;
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, BinlogCleaner> cleaners;
    private OldVersionBinlogCleaner oldVersionBinlogCleaner;

    public BinlogCleanManager(StreamContext context, LogFileLockManagerCollection lockManagerCollection) {
        this.cleaners = new ConcurrentHashMap<>();
        for (String stream : context.getStreamSet()) {
            this.cleaners.put(stream, createBinlogCleaner(stream, context, lockManagerCollection));
        }

        if (context.getTaskType() == TaskType.DumperX) {
            this.oldVersionBinlogCleaner = new OldVersionBinlogCleaner(context.getVersion());
        }
    }

    public void start() {
        log.info("## starting binlog clean manager ...");
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "binlog-cleaner-thread");
            t.setDaemon(true);
            return t;
        });
        int interval = DynamicApplicationConfig.getInt(BINLOG_PURGE_CHECK_INTERVAL_MINUTE);
        executor.scheduleAtFixedRate(this::doClean, interval, interval, TimeUnit.MINUTES);
        cleanBinlogDumpDir();
        log.info("## the binlog clean manager is running now ...");
    }

    public void start(Set<String> streams, StreamContext context, LogFileLockManagerCollection lockManagerCollection) {
        log.info("## adding new streams to binlog clean manager, streams: {} ...", streams);
        synchronized (this) {
            streams.forEach(s -> cleaners.put(s, createBinlogCleaner(s, context, lockManagerCollection)));
        }
        log.info("## the streams is successfully added to binlog clean manager ...");
    }

    public void stop() {
        log.info("## stopping binlog clean manager ...");
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        log.info("## the binlog clean manager is stopped now ...");
    }

    public void stop(String stream) {
        synchronized (this) {
            cleaners.remove(stream);
        }
    }

    BinlogCleaner createBinlogCleaner(String stream,
                                      StreamContext context,
                                      LogFileLockManagerCollection lockManagerCollection) {
        return new BinlogCleaner(stream, context, lockManagerCollection.get(stream));
    }

    void doClean() {
        synchronized (this) {
            try {
                MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_CLEAN);
                tryCleanRemoteBinlog();
                tryCleanLocalBinlog();
                tryCleanOldVersionBinlog();
            } finally {
                MDC.remove(MDC_THREAD_LOGGER_KEY);
            }
        }
    }

    void tryCleanLocalBinlog() {
        try {
            for (BinlogCleaner cleaner : cleaners.values()) {
                cleaner.cleanLocalFiles();
            }
        } catch (Throwable e) {
            log.error("purge local binlog error!", e);
        }
    }

    void tryCleanRemoteBinlog() {
        try {
            for (BinlogCleaner cleaner : cleaners.values()) {
                cleaner.purgeRemote();
            }
        } catch (Throwable e) {
            log.error("purge remote binlog error!", e);
        }
    }

    void tryCleanOldVersionBinlog() {
        try {
            if (oldVersionBinlogCleaner != null) {
                oldVersionBinlogCleaner.purge();
            }
        } catch (Throwable e) {
            log.error("purge old version binlog error!", e);
        }
    }

    void cleanBinlogDumpDir() {
        String path = getString(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH);
        log.info("cleaning up binlog dump download path:{}", path);
        try {
            File f = new File(path);
            if (f.exists()) {
                FileUtils.forceDelete(f);
            }
        } catch (IOException e) {
            log.error("delete download path:{} failed!", path, e);
        }
    }

}
