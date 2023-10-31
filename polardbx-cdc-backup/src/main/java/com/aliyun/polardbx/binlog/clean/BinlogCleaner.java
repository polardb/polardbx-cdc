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
import com.aliyun.polardbx.binlog.clean.barrier.ICleanBarrier;
import com.aliyun.polardbx.binlog.clean.barrier.UploadFinishedBarrier;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.monitor.MonitorValue;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FILE_PRESERVE_DAYS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DISK_SPACE_MAX_SIZE_MB;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PURGE_DISK_USE_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PURGE_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.DISK_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getDouble;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.clusterId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.groupId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.streamId;
import static com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus.COMPLETE;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.BINLOG_BACKUP_DELETE_ERROR;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThan;

/**
 * @author chengjin, yudong
 */
public class BinlogCleaner {
    private static final Logger logger = LoggerFactory.getLogger(BinlogCleaner.class);
    private final String stream;
    private final String group;
    private final CdcFileSystem fileSystem;
    private final long maxTotalBytes;
    private List<ICleanBarrier> cleanBarriers;

    public BinlogCleaner(String stream, StreamContext context) {
        this.group = context.getGroup();
        this.stream = stream;
        this.maxTotalBytes = calculateMaxTotalBytes(context.getStreamList().size());
        this.fileSystem =
            new CdcFileSystem(BinlogFileUtil.getRootPath(context.getTaskType(), context.getVersion()), group, stream);
        initCleanBarrier(context.getTaskName(), context.getTaskType());
    }

    public void cleanLocalFiles() {
        if (!DynamicApplicationConfig.getBoolean(BINLOG_PURGE_ENABLE)) {
            return;
        }

        List<CdcFile> localFiles = fileSystem.listLocalFiles();
        if (logger.isDebugEnabled()) {
            logger.info(
                "before clean, local files:" + localFiles.stream().map(CdcFile::getName).collect(Collectors.toList()));
        }

        long totalBytes = 0;
        for (CdcFile file : localFiles) {
            totalBytes += file.size();
        }
        logger.info("before clean local files, local binlog size : " + totalBytes
            + " max binlog size : " + maxTotalBytes
            + " file size : " + localFiles.size());

        while (totalBytes > maxTotalBytes) {
            // 只有一个文件，不能删除
            if (localFiles.size() == 1) {
                logger.info("local file:{} can't be cleaned, because it is the only one", localFiles.get(0).getName());
                break;
            }

            CdcFile file = localFiles.remove(0);
            if (!canClean(file.getName())) {
                logger.info("local file {} can`t be cleaned, because it has not uploaded to backup system.",
                    file.getName());
                continue;
            }
            totalBytes -= file.size();
            file.delete();
            logger.info("local file:{} has been cleaned!", file.getName());
        }

        // 重新获取一下本地磁盘文件 & print log
        localFiles = fileSystem.listLocalFiles();
        if (logger.isDebugEnabled()) {
            logger.info(
                "after clean, local files:" + localFiles.stream().map(CdcFile::getName).collect(Collectors.toList()));
        }

        logger.info("after clean local files, local size : " + totalBytes + " max binlog size : " + maxTotalBytes
            + " file size : " + localFiles.size());

        if (totalBytes > maxTotalBytes) {
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.BINLOG_NUM_LARGE_THEN_WARRNING, new MonitorValue(totalBytes), totalBytes,
                    maxTotalBytes);
        }
    }

    /**
     * 删除远端存储上过期的binlog文件
     * 并更新binlog_oss_record表中purgeStatus字段
     */
    public void purgeRemote() {
        cleanRemoteFiles();
        cleanOssRecords();
    }

    private void cleanRemoteFiles() {
        Date expireTime =
            new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(getInt(BINLOG_BACKUP_FILE_PRESERVE_DAYS)));
        logger.info("try to clean remote binlog files! expire time: " + DateFormatUtils
            .format(expireTime, "yyyy-MM-dd HH:mm:ss"));

        String cid = getString(ConfigKeys.CLUSTER_ID);
        List<BinlogOssRecord> filesToClean =
            getObject(BinlogOssRecordService.class).getRecordsForPurge(group, stream, cid, expireTime);
        for (BinlogOssRecord record : filesToClean) {
            try {
                fileSystem.deleteRemoteFile(record.getBinlogFile());
                logger.info("remote file:{} has been cleaned!", record.getBinlogFile());
            } catch (Exception e) {
                logger.error("delete from remote failed!", e);
                MonitorManager.getInstance().triggerAlarm(BINLOG_BACKUP_DELETE_ERROR, record.getBinlogFile());
                break;
            }
            getObject(BinlogOssRecordMapper.class).update(u -> u.set(purgeStatus)
                .equalTo(BinlogPurgeStatus.COMPLETE.getValue())
                .where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
            logger.info("update binlog:{} upload_status to complete", record.getBinlogFile());
        }
    }

    private void cleanOssRecords() {
        String cid = getString(ConfigKeys.CLUSTER_ID);
        Optional<BinlogOssRecord> maxPurgedRecord =
            getObject(BinlogOssRecordService.class).getMaxPurgedRecord(group, stream, cid);
        maxPurgedRecord.ifPresent(record -> {
            int recordExpireDays =
                DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_PURGED_RECORD_PRESERVE_DAYS);
            Date endDate = new Date(record.getGmtCreated().getTime() - TimeUnit.DAYS.toMillis(recordExpireDays));
            logger.info("try to delete purged records, max purged record:{}, expire time:{}", record.getBinlogFile(),
                endDate);
            getObject(BinlogOssRecordMapper.class).delete(
                s -> s.where(groupId, isEqualTo(group)).and(streamId, isEqualTo(stream)).and(clusterId, isEqualTo(cid))
                    .and(binlogFile, isLessThan(record.getBinlogFile()))
                    .and(purgeStatus, isEqualTo(COMPLETE.getValue())).and(gmtCreated, isLessThan(endDate)));
        });
    }

    private boolean canClean(String binlogFile) {
        if (CollectionUtils.isEmpty(cleanBarriers)) {
            return true;
        }

        for (ICleanBarrier barrier : cleanBarriers) {
            if (!barrier.canClean(binlogFile)) {
                return false;
            }
        }
        return true;
    }

    private void initCleanBarrier(String taskName, TaskType taskType) {
        if (taskType == TaskType.DumperX || RuntimeLeaderElector.isDumperLeader(taskName)) {
            if (RemoteBinlogProxy.getInstance().isBackupOn()) {
                cleanBarriers = Collections.singletonList(new UploadFinishedBarrier(group, stream));
            }
        }
    }

    private long calculateMaxTotalBytes(int streamCount) {
        long physicalMaxBytes = (long) (getLong(DISK_SIZE) * getDouble(BINLOG_PURGE_DISK_USE_RATIO)) << 20;
        long configMaxBytes = getLong(BINLOG_DISK_SPACE_MAX_SIZE_MB) << 20;
        return Math.min(physicalMaxBytes, configMaxBytes) / streamCount;
    }
}
