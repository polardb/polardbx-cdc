/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.cleaner;

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.cleaner.barrier.RemoteCleanBarrier;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.io.OSSFile;
import com.aliyun.polardbx.binlog.io.OSSFileSystem;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.monitor.MonitorValue;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_EXPIRE_DAYS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CHECK_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CLEAN_ENABLE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.BINLOG_BACKUP_DELETE_ERROR;

public class LocalBinlogCleaner {

    private static final Logger logger = LoggerFactory.getLogger(LocalBinlogCleaner.class);

    private static final long MAX_BINLOG_SIZE = 500 * 1024 * 1024 * 1024L;
    private final long maxBinlogSize;
    private final List<ICleanerBarrier> cleanerBarrierList;
    private final OSSFileSystem fileSystem;
    private ScheduledExecutorService executorService;
    private String taskName;

    public LocalBinlogCleaner(String binlogFileDir) {
        cleanerBarrierList = new CopyOnWriteArrayList<>();
        fileSystem = new OSSFileSystem(binlogFileDir);
        maxBinlogSize = calcMaxBinlogSize();
        logger.info("local max binlog size : " + maxBinlogSize);
    }

    private long calcMaxBinlogSize() {
        long diskSize = DynamicApplicationConfig.getLong(ConfigKeys.DISK_SIZE);
        double threshold = DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_CLEANER_CLEAN_THRESHOLD);
        return Math.min((long) (diskSize * threshold) * 1024 * 1024, MAX_BINLOG_SIZE);
    }

    public void start() {
        logger.info("start binlog cleaner ... with max binlog size " + maxBinlogSize);
        if (RuntimeLeaderElector.isDumperLeader(taskName)) {
            if (RemoteBinlogProxy.getInstance().isBackupOn()) {
                addBarrier(new RemoteCleanBarrier());
                logger.info("add clean barrier ...");
            }
        }
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binlog-cleaner");
            t.setDaemon(true);
            return t;
        });
        int interval = getInt(BINLOG_CLEANER_CHECK_INTERVAL);
        executorService.scheduleAtFixedRate(this::scan, interval, interval, TimeUnit.MINUTES);
    }

    public void stop() {
        logger.info("shutdown cleaner ...");
        executorService.shutdown();
        executorService = null;
    }

    public void addBarrier(ICleanerBarrier barrier) {
        cleanerBarrierList.add(barrier);
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    private void purgeLocal() {
        List<OSSFile> fileList = fileSystem.getAllLogFileNamesOrdered();
        long totalSize = 0;
        for (OSSFile file : fileList) {
            totalSize += file.size();
        }
        logger.info(
            "detected local binlog size : " + totalSize + " max binlog size : " + maxBinlogSize + " file size : "
                + fileList.size());
        while (totalSize > maxBinlogSize) {
            // 只有一个文件，不能删除
            if (fileList.size() == 1) {
                break;
            }
            OSSFile file = fileList.remove(0);
            if (!canClean(file.getBinlogFile())) {
                logger.info("local file {} can`t be cleaned, because it has not uploaded to backup system.",
                    file.getBinlogFile());
                break;
            }
            totalSize -= file.size();
            file.delete();
        }

        // 重新获取一下本地磁盘文件 & print log
        Set<String> localFileSet =
            fileSystem.getAllLogFileNamesOrdered().stream().map(OSSFile::getBinlogFile).collect(
                Collectors.toSet());
        logger.info("after clean local binlog , local size : " + totalSize + " max binlog size : " + maxBinlogSize
            + " file size : " + localFileSet.size());

        if (totalSize > maxBinlogSize) {
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.BINLOG_NUM_LARGE_THEN_WARRNING, new MonitorValue(totalSize), totalSize,
                    maxBinlogSize);
        }
    }

    private void purgeRemote() {
        long end = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(getInt(BINLOG_BACKUP_EXPIRE_DAYS));
        Date endDate = new Date(end);
        logger.info("try to clean remote binlog files! end " + DateFormatUtils.format(endDate, "yyyy-MM-dd HH:mm:ss"));

        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper
            .select(s -> s.where(BinlogOssRecordDynamicSqlSupport.gmtModified, SqlBuilder.isLessThan(endDate))
                .and(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                    SqlBuilder.isEqualTo(BinlogUploadStatusEnum.SUCCESS.getValue()))
                .and(purgeStatus, SqlBuilder.isNotEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue())));

        for (BinlogOssRecord record : recordList) {
            try {
                RemoteBinlogProxy.getInstance().delete(record.getBinlogFile());
            } catch (Exception e) {
                logger.error("delete from backup failed!", e);
                MonitorManager.getInstance().triggerAlarm(BINLOG_BACKUP_DELETE_ERROR, record.getBinlogFile());
                break;
            }
            mapper.update(u -> u.set(purgeStatus)
                .equalTo(BinlogPurgeStatusEnum.COMPLETE.getValue())
                .where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
            logger.info("delete remote file : " + record.getBinlogFile());
        }
        logger.info("success to clean remote binlog files! size : " + recordList.size());
        tryDeleteBackupRecords();
    }

    private boolean canClean(String binlogFile) {
        for (ICleanerBarrier barrier : cleanerBarrierList) {
            if (!barrier.canClean(binlogFile)) {
                return false;
            }
        }
        return true;
    }

    private void tryDeleteBackupRecords() {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecord maxPurgedRecord = getObject(BinlogOssRecordService.class).getMaxPurgedRecord();
        if (maxPurgedRecord != null) {
            //已经purge的记录，尽量保留一段时间，再进行物理删除
            long end = maxPurgedRecord.getGmtCreated().getTime() - TimeUnit.DAYS.toMillis(7);
            Date endDate = new Date(end);
            mapper.delete(s -> s.where(binlogFile, SqlBuilder.isLessThan(maxPurgedRecord.getBinlogFile()))
                .and(purgeStatus, SqlBuilder.isEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue()))
                .and(gmtCreated, SqlBuilder.isLessThan(endDate)));
        }
    }

    // 本地磁盘上的binlog文件的清理策略是按磁盘使用量
    // backup系统中binlog文件的清理策略是按配置的保存天数
    // 可能会出现backup中的文件已经被清理掉了，但本地磁盘还未被清理的情况
    public void scan() {
        try {
            purgeRemote();
        } catch (Exception e) {
            logger.error("purge oss binlog occur exception!", e);
        }
        try {
            boolean enable = DynamicApplicationConfig.getBoolean(BINLOG_CLEANER_CLEAN_ENABLE);
            if (enable) {
                purgeLocal();
            }
        } catch (Exception e) {
            logger.error("purge binlog occur exception!", e);
        }
    }
}
