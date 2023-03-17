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

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.clean.barrier.ICleanerBarrier;
import com.aliyun.polardbx.binlog.clean.barrier.RemoteCleanBarrier;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FILE_EXPIRE_DAYS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_CLEANER_CLEAN_ENABLE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.BINLOG_BACKUP_DELETE_ERROR;

public class BinlogCleaner {
    private static final Logger logger = LoggerFactory.getLogger(BinlogCleaner.class);
    private final String stream;
    private final String group;

    private final List<ICleanerBarrier> cleanerBarrierList;
    private final CdcFileSystem fileSystem;
    private final long maxBinlogSize;

    public BinlogCleaner(String binlogFileFullPath, String group, String stream, long maxBinlogSize) {
        this.cleanerBarrierList = new CopyOnWriteArrayList<>();
        this.fileSystem = new CdcFileSystem(binlogFileFullPath, group, stream);
        this.group = group;
        this.stream = stream;
        this.maxBinlogSize = maxBinlogSize;
    }

    public void initDefaultBarrier() {
        this.addBarrier(new RemoteCleanBarrier(group, stream));
    }

    public void addBarrier(ICleanerBarrier barrier) {
        cleanerBarrierList.add(barrier);
    }

    public void purgeLocal() {
        boolean enable = DynamicApplicationConfig.getBoolean(BINLOG_CLEANER_CLEAN_ENABLE);
        if (!enable) {
            return;
        }

        List<CdcFile> fileList = fileSystem.listLocalFiles();
        long totalSize = 0;
        for (CdcFile file : fileList) {
            totalSize += file.size();
        }
        logger.info("detected local binlog size : " + totalSize
            + " max binlog size : " + maxBinlogSize
            + " file size : " + fileList.size());

        while (totalSize > maxBinlogSize) {
            // 只有一个文件，不能删除
            if (fileList.size() == 1) {
                break;
            }
            CdcFile file = fileList.remove(0);
            if (!canClean(file.getName())) {
                logger.info("local file {} can`t be cleaned, because it has not uploaded to backup system.",
                    file.getName());
                break;
            }
            totalSize -= file.size();
            file.delete();
        }

        // 重新获取一下本地磁盘文件 & print log
        Set<String> localFileSet =
            fileSystem.listLocalFiles().stream().map(CdcFile::getName).collect(Collectors.toSet());
        logger.info("after clean local binlog , local size : " + totalSize + " max binlog size : " + maxBinlogSize
            + " file size : " + localFileSet.size());

        if (totalSize > maxBinlogSize) {
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.BINLOG_NUM_LARGE_THEN_WARRNING, new MonitorValue(totalSize), totalSize,
                    maxBinlogSize);
        }
    }

    /**
     * 删除远端存储上过期的binlog文件
     * 并更新binlog_oss_record表中purgeStatus字段
     */
    public void purgeRemote() {
        long end = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(getInt(BINLOG_BACKUP_FILE_EXPIRE_DAYS));
        Date expireTime = new Date(end);
        logger.info("try to clean remote binlog files! expire time: " + DateFormatUtils
            .format(expireTime, "yyyy-MM-dd HH:mm:ss"));

        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecordService recordService = getObject(BinlogOssRecordService.class);
        List<BinlogOssRecord> recordsForPurge = recordService.getRecordsForPurge(group, stream, expireTime);
        for (BinlogOssRecord record : recordsForPurge) {
            try {
                fileSystem.deleteRemoteFile(record.getBinlogFile());
            } catch (Exception e) {
                logger.error("delete from remote failed!", e);
                MonitorManager.getInstance().triggerAlarm(BINLOG_BACKUP_DELETE_ERROR, record.getBinlogFile());
                break;
            }
            mapper.update(u -> u.set(purgeStatus)
                .equalTo(BinlogPurgeStatusEnum.COMPLETE.getValue())
                .where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
            logger.info("delete remote file : " + record.getBinlogFile());
        }
        logger.info("success to clean remote binlog files! size : " + recordsForPurge.size());
        tryDeletePurgedRecords();
    }

    private boolean canClean(String binlogFile) {
        for (ICleanerBarrier barrier : cleanerBarrierList) {
            if (!barrier.canClean(binlogFile)) {
                return false;
            }
        }
        return true;
    }

    private void tryDeletePurgedRecords() {
        getObject(BinlogOssRecordService.class).deletePurgedRecords(group, stream);
    }
}
