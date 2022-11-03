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

import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.io.IFileCursorProvider;
import com.aliyun.polardbx.binlog.io.OSSFile;
import com.aliyun.polardbx.binlog.io.OSSFileSystem;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.springframework.util.CollectionUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_DOWNLOAD_BEST_EFFORT_WITH_GAP;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_DOWNLOAD_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_DOWNLOAD_SIZE_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_DOWNLOAD_WAIT_UPLOAD_TIMEOUT;
import static com.aliyun.polardbx.binlog.ConfigKeys.DISK_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getDouble;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

public class BinlogBackupManager {

    private static final Logger logger = LoggerFactory.getLogger(BinlogBackupManager.class);
    private static final long MAX_BINLOG_SIZE = getLong(ConfigKeys.BINLOG_FILE_SIZE) / 1024 / 1024;
    private ThreadPoolExecutor executorService;
    private ActionManager actionManager;

    public void start(String binlogFileDir, IFileCursorProvider provider, String taskName) {
        executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(
            DynamicApplicationConfig.getInt(BINLOG_BACKUP_DOWNLOAD_PARALLELISM),
            getThreadFactory("binlog-download" + "-%d", false));
        executorService.setKeepAliveTime(60, TimeUnit.SECONDS);
        executorService.allowCoreThreadTimeOut(true);

        // 需要执行完prepare，然后再启动ActionManager
        prepareLocalLastBinlog(binlogFileDir);

        actionManager = new ActionManager(binlogFileDir, provider, taskName);
        actionManager.start();
    }

    private List<BinlogOssRecord> prepareDownloadList() {
        double maxUseRatio = getDouble(BINLOG_BACKUP_DOWNLOAD_SIZE_RATIO);
        long maxBinlogSize = Math.max((long) (getLong(DISK_SIZE) * maxUseRatio), MAX_BINLOG_SIZE);
        int fileNum = (int) (maxBinlogSize / MAX_BINLOG_SIZE);
        logger.info("max file num for download is : " + fileNum);

        // build a snapshot，防止流量大的时候，一直有未上传成功的文件，导致wait upload timeout
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper.select(s -> s.where(BinlogOssRecordDynamicSqlSupport.purgeStatus,
            SqlBuilder.isNotEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue()))
            .and(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                SqlBuilder.isNotEqualTo(BinlogUploadStatusEnum.IGNORE.getValue()))
            .orderBy(BinlogOssRecordDynamicSqlSupport.binlogFile.descending()));

        int timeoutSecond = DynamicApplicationConfig.getInt(BINLOG_BACKUP_DOWNLOAD_WAIT_UPLOAD_TIMEOUT);
        boolean bestEffortWithGap = DynamicApplicationConfig.getBoolean(BINLOG_BACKUP_DOWNLOAD_BEST_EFFORT_WITH_GAP);
        long start = System.currentTimeMillis();
        while (true) {
            Set<String> notUploadFiles = new HashSet<>();
            for (int i = 0; i < recordList.size(); i++) {
                final int id = recordList.get(i).getId();
                BinlogOssRecord record =
                    mapper.select(s -> s.where(BinlogOssRecordDynamicSqlSupport.id, SqlBuilder.isEqualTo(id))).get(0);
                if (i != 0 && record.getUploadStatus() != BinlogUploadStatusEnum.SUCCESS.getValue()) {
                    notUploadFiles.add(record.getBinlogFile());
                }
            }
            if (!notUploadFiles.isEmpty()) {
                if (System.currentTimeMillis() - start < timeoutSecond * 1000) {
                    LockSupport.parkNanos(1000 * 1000 * 1000);
                    logger.info("there exist not uploaded binlog files {} , will try later.", notUploadFiles);
                } else {
                    if (bestEffortWithGap) {
                        logger.warn("wait binlog files upload timeout, will go to best effort mode,"
                            + " not uploaded files is " + " :" + notUploadFiles);
                        break;
                    } else {
                        throw new PolardbxException("wait binlog files upload timeout, "
                            + "not uploaded files is : " + notUploadFiles);
                    }
                }
            } else {
                break;
            }
        }

        logger.info("all available db record list size : " + recordList.size());
        List<BinlogOssRecord> needDownloadRecordList = new ArrayList<>();
        for (BinlogOssRecord record : recordList) {
            needDownloadRecordList.add(record);
            if (needDownloadRecordList.size() > fileNum) {
                break;
            }
        }
        logger.info("db record list for download is " + needDownloadRecordList);
        return needDownloadRecordList;
    }

    /**
     * 1、 本地有binlog文件，直接return
     * 2、 本地没有binlog文件
     * - 查询下载列表，生成download.log文件
     */
    public void prepareLocalLastBinlog(String dir) {
        // 本地文件可用列表
        Map<String, OSSFile> allLocalFileMap = listAllLocalBinlogFileMap(dir);
        DownloadLogManager downloadLogManager = DownloadLogManager.getInstance();

        // build download log list
        if (allLocalFileMap.isEmpty()) {
            logger.info("local binlog file is empty, will build download log");
            buildDownloadLogList();
        }

        List<String> downloadLogList = downloadLogManager.getDownloadList();
        // 为空，直接返回
        if (CollectionUtils.isEmpty(downloadLogList)) {
            return;
        }
        cleanLocalBadFileBefore(downloadLogList, allLocalFileMap);
        downloadList(downloadLogList, downloadLogManager.hasSuccessFile());
    }

    private void downloadList(List<String> downloadLogList, boolean localHasBinlog) {
        int fileIdx = 0;
        if (!localHasBinlog) {
            while (fileIdx < downloadLogList.size()) {
                // 先下载最后一个
                String lastFileName = downloadLogList.get(fileIdx++);
                try {
                    downloadWithRetry(lastFileName);
                    break;
                } catch (Throwable t) {
                    processDownLoadError(lastFileName, t, true);
                }
            }
        }

        // 遍历列表，异步下载文件
        for (int i = fileIdx; i < downloadLogList.size(); i++) {
            String fileName = downloadLogList.get(i);
            executorService.execute(() -> {
                try {
                    downloadWithRetry(fileName);
                } catch (Throwable e) {
                    processDownLoadError(fileName, e, false);
                }
            });
        }
    }

    private void downloadWithRetry(String fileName) throws ExecutionException, RetryException {
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(500,
                TimeUnit.MILLISECONDS)).withStopStrategy(StopStrategies.stopAfterAttempt(5))
            .build();
        retryer.call(() -> {
            doDownloadFile(fileName);
            return true;
        });
    }

    private void processDownLoadError(String fileName, Throwable t, boolean throwError) {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper.select(
            s -> s.where(BinlogOssRecordDynamicSqlSupport.binlogFile, SqlBuilder.isEqualTo(fileName)));
        if (!recordList.isEmpty()) {
            int status = recordList.get(0).getUploadStatus();
            if (status == BinlogUploadStatusEnum.CREATE.getValue() ||
                status == BinlogUploadStatusEnum.UPLOADING.getValue()) {
                logger.warn("download " + fileName + " failed, record status is " + status
                    + ", will skipped the error !", t);
                DownloadLogManager.getInstance().successDownload(fileName);
                return;
            }
        }

        logger.error("download " + fileName + " failed!", t);
        MonitorManager.getInstance().triggerAlarm(MonitorType.BINLOG_DOWNLOAD_FAILED_WARNING, fileName);
        if (throwError) {
            throw new PolardbxException("download file " + fileName + " failed!", t);
        }
    }

    private void cleanLocalBadFileBefore(List<String> downloadLogList, Map<String, OSSFile> localFileMap) {
        downloadLogList.stream().forEach(e -> {
            OSSFile ossFile = localFileMap.get(e);
            if (ossFile != null) {
                ossFile.delete();
            }
        });
    }

    private void doDownloadFile(String fileName) {
        final BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        final DownloadLogManager downloadLogManager = DownloadLogManager.getInstance();
        BinlogOssRecord record = mapper.select(
            s -> s.where(BinlogOssRecordDynamicSqlSupport.binlogFile, SqlBuilder.isEqualTo(fileName))).get(0);
        if (record.getPurgeStatus() == BinlogPurgeStatusEnum.COMPLETE.getValue()) {
            logger.info("binlog file {} has been purged from remote, mark success directly", fileName);
            downloadLogManager.successDownload(fileName);
        } else {
            logger.info("start download oss binlog file " + fileName + "!");
            RemoteBinlogProxy.getInstance()
                .download(fileName, DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH));
            downloadLogManager.successDownload(fileName);
            logger.info("success download oss binlog file " + fileName + "!");
        }
    }

    private Map<String, OSSFile> listAllLocalBinlogFileMap(String dir) {
        OSSFileSystem ossFileSystem = new OSSFileSystem(dir);
        Map<String, OSSFile> localFileMap = ossFileSystem.getAllLogFileNamesOrdered().stream()
            .collect(Collectors.toMap(OSSFile::getBinlogFile, o -> o));
        logger.info("find local binlog size : " + localFileMap.size());
        return localFileMap;
    }

    private void buildDownloadLogList() {
        final DownloadLogManager downloadLogManager = DownloadLogManager.getInstance();
        downloadLogManager.clean();

        List<BinlogOssRecord> needDownloadRecordList = prepareDownloadList();
        needDownloadRecordList.stream().forEach(binlogOssRecord -> {
            downloadLogManager.addDownloadList(binlogOssRecord.getBinlogFile());
        });
        downloadLogManager.buildBaseLog();
    }

    public void shutdown() {
        if (actionManager != null) {
            actionManager.shutdown();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
