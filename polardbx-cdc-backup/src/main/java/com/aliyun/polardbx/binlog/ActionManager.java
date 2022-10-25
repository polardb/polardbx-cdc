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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.action.Uploader;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.io.BinlogFetcher;
import com.aliyun.polardbx.binlog.io.IDataFetcher;
import com.aliyun.polardbx.binlog.io.IFileCursorProvider;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MAX_THREAD_NUM;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * 同一时间，只能有一个 ActionManager为leader， upload 最终以leader为准
 * 不同ActionManager在上传统一文件到oss上时，会报错退出，此后只有真正的leader才会继续上传
 */
public class ActionManager implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ActionManager.class);

    private final BinlogOssRecordMapper ossRecordMapper;
    private final IFileCursorProvider provider;
    private final String binlogFileDir;
    private final String taskName;
    private final ExecutorService scanExecutor;
    private final ThreadPoolExecutor uploadExecutor;
    private final Set<String> uploadingFiles;
    private volatile boolean runnable = true;

    public ActionManager(String binlogFileDir, IFileCursorProvider provider, String taskName) {
        this.ossRecordMapper = getObject(BinlogOssRecordMapper.class);
        this.provider = provider;
        this.binlogFileDir = binlogFileDir;
        this.taskName = taskName;
        this.uploadingFiles = new HashSet<>();

        this.scanExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "binlog-backup-action");
            t.setDaemon(true);
            return t;
        });

        int maxThreadNum = DynamicApplicationConfig.getInt(BINLOG_BACKUP_UPLOAD_MAX_THREAD_NUM);
        this.uploadExecutor = new ThreadPoolExecutor(maxThreadNum, maxThreadNum, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("binlog-backup-upload-thread-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy());
        this.uploadExecutor.allowCoreThreadTimeOut(true);
    }

    @Override
    public void run() {
        logger.info("backup binlog loop begin!");
        while (runnable) {
            try {
                Thread.sleep(2000L);
                if (!RuntimeLeaderElector.isDumperLeader(taskName)) {
                    continue;
                }
                // 扫描上传失败的任务，从新抢锁启动
                scanUpload();
            } catch (Throwable e) {
                logger.error("dispatch agent action failed", e);
                alert("Scan Thread");
            }
        }
    }

    private void alert(String fileName) {
        MonitorManager.getInstance().triggerAlarm(MonitorType.BINLOG_BACKUP_UPLOAD_ERROR, fileName);
    }

    /**
     * 扫其未完成的Upload binlog
     */
    private void scanUpload() {
        List<BinlogOssRecord> notUploadBinlogList =
            ossRecordMapper.select(s -> s.where(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                SqlBuilder.isEqualTo(BinlogUploadStatusEnum.CREATE.getValue()))
                .or(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                    SqlBuilder.isEqualTo(BinlogUploadStatusEnum.UPLOADING.getValue())));

        //filter
        BinlogOssRecord maxPurgedRecord = getObject(BinlogOssRecordService.class).getMaxPurgedRecord();
        if (maxPurgedRecord != null) {
            notUploadBinlogList = notUploadBinlogList.stream()
                .filter(f -> f.getBinlogFile().compareTo(maxPurgedRecord.getBinlogFile()) > 0)
                .collect(Collectors.toList());
        }

        for (final BinlogOssRecord record : notUploadBinlogList) {
            //如果文件正在上传中，则忽略
            if (uploadingFiles.contains(record.getBinlogFile())) {
                continue;
            }
            //double check，如果文件已经上传成功，则忽略
            Optional<BinlogOssRecord> latestRecord = ossRecordMapper
                .selectOne(s -> s.where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
            if (latestRecord.isPresent() &&
                latestRecord.get().getUploadStatus() == BinlogUploadStatusEnum.SUCCESS.getValue()) {
                continue;
            }

            uploadExecutor.submit(() -> {
                try {
                    logger.info("begin to backup binlog file " + record.getBinlogFile());
                    processUpload(record);
                    logger.info("binlog file " + record.getBinlogFile() + " is successfully uploaded.");
                } catch (Throwable e) {
                    logger.error("upload file failed!", e);
                    alert(record.getBinlogFile());
                } finally {
                    uploadingFiles.remove(record.getBinlogFile());
                }
            });
            uploadingFiles.add(record.getBinlogFile());
        }
    }

    /**
     * 标记为上传中
     */
    private void processUpload(BinlogOssRecord record) throws IOException {
        IDataFetcher fetcher = new BinlogFetcher(record.getBinlogFile(), binlogFileDir, provider);
        setUploading(record);
        doUpload(fetcher);
        uploadSuccess(record);
    }

    private void doUpload(IDataFetcher fetcher) throws IOException {
        Uploader uploader = new Uploader(fetcher);
        uploader.doAction();
    }

    private void uploadSuccess(BinlogOssRecord record) {
        // 更新状态为完成
        ossRecordMapper.update(u -> u.set(BinlogOssRecordDynamicSqlSupport.uploadStatus)
            .equalTo(BinlogUploadStatusEnum.SUCCESS.getValue()).set(BinlogOssRecordDynamicSqlSupport.uploadHost)
            .equalTo(DynamicApplicationConfig.getString(ConfigKeys.INST_IP))
            .where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
    }

    private void setUploading(BinlogOssRecord record) {
        // 更新状态为上传中
        ossRecordMapper.update(u -> u.set(BinlogOssRecordDynamicSqlSupport.uploadStatus)
            .equalTo(BinlogUploadStatusEnum.UPLOADING.getValue())
            .where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(record.getId())));
    }

    public void start() {
        scanExecutor.execute(this);
    }

    public void shutdown() {
        this.runnable = false;
        this.scanExecutor.shutdownNow();
        this.uploadingFiles.clear();
    }
}
