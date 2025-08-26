/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.remote.DownloadModeEnum;
import com.aliyun.polardbx.binlog.remote.DownloadParameter;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.binlog.util.Timer;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_PART_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_KEY;
import static com.aliyun.polardbx.binlog.Constants.MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP;

/**
 * 滑动窗口文件下载器
 *
 * @author yudong
 * @since 2023/7/19 16:07
 **/
@Slf4j
public class BinlogDumpDownloader implements BinlogDumpRotateObserver {

    /**
     * 用于获得本地文件列表，确定下载结束时刻
     */
    private final LogFileManager logFileManager;

    /**
     * 滑动窗口大小
     */
    private final int windowSize;

    /**
     * 下载文件在本地保存的位置
     */
    private final String downloadPath;
    /**
     * 待下载文件列表
     */
    // private final List<String> downloadList;
    private final PriorityQueue<Integer> downloadQueue;
    /**
     * 已下载或下载中的文件列表
     */
    private final Set<String> downloadedSet;
    /**
     * 下载线程池
     */
    private ThreadPoolExecutor downloadThreadPool;
    private final Map<String, Throwable> fileDownLoadErrorMap;
    private final LocalFileSystem fileSystem;
    private final long masterHeartbeatPeriod;
    private final ServerCallStreamObserver<DumpStream> observer;
    /**
     * used to send heartbeat while waiting
     */
    private final BinlogDumpReader dumpReader;
    private final AtomicBoolean downloadStartFlag;
    /**
     * 开始下载的文件
     */
    private String startFile;
    /**
     * 文件前缀
     */
    private String filePrefix;
    /**
     * 是否全部结束
     */
    @Getter
    private boolean finished = false;

    public BinlogDumpDownloader(LogFileManager logFileManager, String downloadPath, int windowSize, String startFile,
                                long masterHeartbeatPeriod, ServerCallStreamObserver<DumpStream> observer,
                                BinlogDumpReader dumpReader) {
        this.logFileManager = logFileManager;
        this.windowSize = windowSize;
        this.startFile = startFile;
        this.downloadPath = downloadPath;
        this.masterHeartbeatPeriod = masterHeartbeatPeriod;
        this.observer = observer;
        this.dumpReader = dumpReader;
        // this.downloadList = new ArrayList<>();
        this.downloadQueue = new PriorityQueue<>();
        this.downloadedSet = new HashSet<>();
        this.downloadThreadPool =
            new ThreadPoolExecutor(windowSize, windowSize * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("binlog-dump-download-thread-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.fileSystem =
            new LocalFileSystem(downloadPath, logFileManager.getGroupName(), logFileManager.getStreamName());
        this.fileDownLoadErrorMap = new ConcurrentHashMap<>();
        this.downloadStartFlag = new AtomicBoolean(false);
        if (!StringUtils.isEmpty(startFile)) {
            this.filePrefix = startFile.split("\\.")[0];
        }

        log.info("start file:{}, window size:{}, download path:{}", startFile, windowSize, downloadPath);
    }

    public static BinlogDumpDownloader buildBinlogDumpDownloader(BinlogDumpDownloader binlogDumpDownloader,
                                                                 String startFile) {
        return new BinlogDumpDownloader(binlogDumpDownloader.logFileManager, binlogDumpDownloader.downloadPath,
            binlogDumpDownloader.windowSize, startFile, binlogDumpDownloader.masterHeartbeatPeriod,
            binlogDumpDownloader.observer, binlogDumpDownloader.dumpReader);
    }

    public void init() {
        finished = false;
        downloadStartFlag.set(false);
        if (downloadThreadPool.isShutdown()) {
            downloadThreadPool =
                new ThreadPoolExecutor(windowSize, windowSize * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                    new ThreadFactoryBuilder().setNameFormat("binlog-dump-download-thread-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
        fileSystem.init();
        getDownloadFileList(startFile);
        if (downloadQueue.isEmpty()) {
            log.info("download file list is empty, will not start binlog dump downloader");
            close();
        }
    }

    public void close() {
        if (isFinished()) {
            return;
        }

        log.info("shutting down binlog dump downloader...");
        downloadThreadPool.shutdownNow();

        // 等待所有下载线程退出之后再清理临时目录，否则清理之后线程再下载，会导致临时文件残留
        try {
            boolean res = downloadThreadPool.awaitTermination(60, TimeUnit.SECONDS);
            if (!res) {
                log.warn("failed to wait download thread pool close!");
            }
        } catch (InterruptedException e) {
            log.warn("failed to wait download thread pool close!");
        }

        cleanUp();
        finished = true;
    }

    /**
     * 上一个binlog文件已经消费完成了，清理消费完的binlog，且下载下一个文件
     */
    @Override
    public void onRotate(String fileName) {
        if (!downloadStartFlag.get()) {
            return;
        }
        if (isFinished()) {
            return;
        }
        // 检查该项是否是已下载或正在下载的，否则是从本地文件rotate的，跟下载无关
        if (!downloadedSet.contains(fileName)) {
            return;
        }

        downloadNext();
        cleanFile(fileName);

        if (downloadQueue.isEmpty() && downloadedSet.isEmpty()) {
            // 透明消费追数据过程中可能有些本地文件被清理
            startFile = BinlogFileUtil.getNextBinlogFileName(fileName);
            getDownloadFileList(startFile);
            if (downloadQueue.isEmpty()) {
                close();
            } else {
                for (int i = 0; i < windowSize; i++) {
                    downloadNext();
                }
            }
        }
    }

    public CdcFile getFile(String fileName) throws Exception {
        if (isFinished()) {
            log.warn("try to get file:{} from binlog dump downloader after finished!", fileName);
            return null;
        }

        tryStartDownload();

        // 被其他dump线程锁住的文件后来被清掉了，补下载
        if (!downloadedSet.contains(fileName)) {
            downloadedSet.add(fileName);
            downloadThreadPool.submit(() -> downloadFile(fileName));
        }

        wait(fileName);
        return fileSystem.get(fileName);
    }

    private void tryStartDownload() {
        if (downloadStartFlag.compareAndSet(false, true)) {
            for (int i = 0; i < windowSize; i++) {
                downloadNext();
            }
        }
    }

    private void wait(String fileName) throws Exception {
        try {
            File f = fileSystem.newFile(fileName);
            long fileSize = getFileSize(fileName);
            long maxWaitSeconds = DynamicApplicationConfig.getLong(BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS);
            Timer waitTimeoutTimer = new Timer(maxWaitSeconds * 1000);
            Timer heartbeatTimer = new Timer(masterHeartbeatPeriod / 1000000);
            long sleepTime = Math.min(1000, masterHeartbeatPeriod / 1000000);
            while (!observer.isCancelled() && (!f.exists() || f.length() < fileSize)) {
                if (fileDownLoadErrorMap.containsKey(fileName)) {
                    throw new IOException("download file " + fileName + " from oss error!",
                        fileDownLoadErrorMap.get(fileName));
                }

                if (waitTimeoutTimer.isTimeout()) {
                    throw new IOException("download file " + fileName + " from oss timeout!");
                }

                // 等待文件下载过程中需要向slave发送心跳，防止下载时间过长导致slave等待超时
                if (heartbeatTimer.isTimeout() || DynamicApplicationConfig.getBoolean(IS_LAB_ENV)) {
                    observer.onNext(DumpStream.newBuilder()
                        .setPayload(dumpReader.heartbeatEventPacket(fileName, 4)).setIsHeartBeat(true).build());
                    LabEventManager.logEvent(LabEventType.TRANSPARENT_CONSUMING);
                }

                log.info("waiting for file {} download finished", fileName);
                Thread.sleep(sleepTime);
            }

            if (observer.isCancelled()) {
                throw new InterruptedException("remote close");
            }

        } catch (InterruptedException e) {
            log.info("download file {} has been interrupted.", fileName);
            throw new InterruptedException();
        } catch (Exception e) {
            log.error("download file from oss error!", e);
            throw new Exception(e);
        }
    }

    /**
     * 下载right指向的文件，并挪动指针
     */
    private void downloadNext() {
        if (downloadQueue.isEmpty()) {
            return;
        }
        int fileSequence = downloadQueue.poll();
        String pureFileName = BinlogFileUtil.getBinlogFileNameBySequence(filePrefix, fileSequence);
        downloadThreadPool.submit(() -> downloadFile(pureFileName));
        downloadedSet.add(pureFileName);
    }

    private void downloadFile(String pureFileName) {
        String remoteFileName = BinlogFileUtil.buildRemoteFilePartName(pureFileName, logFileManager.getGroupName(),
            logFileManager.getStreamName());
        try {
            MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
            fileDownLoadErrorMap.remove(pureFileName);
            log.info("start to download file {}", pureFileName);
            Stopwatch stopwatch = Stopwatch.createStarted();
            RemoteBinlogProxy.getInstance().download(remoteFileName, downloadPath, new DownloadParameter(
                DownloadModeEnum.valueOf(DynamicApplicationConfig.getString(BINLOG_DUMP_DOWNLOAD_MODE)),
                DynamicApplicationConfig.getInt(BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE),
                DynamicApplicationConfig.getLong(BINLOG_DUMP_DOWNLOAD_PART_SIZE)
            ));
            stopwatch.stop();
            long elapsedSeconds = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("download file {} cost {} s", pureFileName, elapsedSeconds);
        } catch (InterruptedException interruptedException) {
            fileDownLoadErrorMap.put(pureFileName, interruptedException);
            log.info("download file {} has been interrupted.", pureFileName);
        } catch (FileNotFoundException fileNotFoundException) {
            fileDownLoadErrorMap.put(pureFileName, fileNotFoundException);
            log.info("download file {} failed because dump download dir has been deleted.", pureFileName);
        } catch (Throwable e) {
            fileDownLoadErrorMap.put(pureFileName, e);
            log.warn("binlog file {} download failed!", pureFileName, e);
        } finally {
            MDC.remove(MDC_THREAD_LOGGER_KEY);
        }
    }

    /**
     * 删除cleanIdx指向的文件，并挪动指针
     */
    private void cleanFile(String fileName) {
        if (downloadedSet.isEmpty()) {
            return;
        }

        File f = fileSystem.newFile(fileName);
        boolean b = f.delete();
        if (b) {
            downloadedSet.remove(fileName);
            log.info("file {} is successfully deleted", fileName);
        } else {
            log.error("file {} is not correctly deleted", fileName);
        }
    }

    /**
     * 查询binlog_oss_record，对比本地文件列表，确定下载文件列表
     * 此方法可能需要调用多次，因为在滑动消费的过程中binlog文件可能还在不断地产生和清理
     */
    private void getDownloadFileList(String startFile) {
        List<String> localFiles = logFileManager.getAllLocalBinlogFileNamesOrdered();
        BinlogOssRecordMapperExtend mapperExtend = SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class);
        int startFileSequence = BinlogFileUtil.getBinlogSequence(startFile);
        List<BinlogOssRecord> records =
            mapperExtend.getRecordsForBinlogDump(logFileManager.getGroupName(), logFileManager.getStreamName(),
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID), startFileSequence);
        List<Integer> filesToDownload =
            records.stream().map(BinlogOssRecord::getBinlogFile).filter(f -> !localFiles.contains(f))
                .map(BinlogFileUtil::getBinlogSequence)
                .collect(Collectors.toList());

        downloadQueue.clear();
        downloadedSet.clear();
        log.info("download file list:{}", filesToDownload);
        downloadQueue.addAll(filesToDownload);
    }

    private void cleanUp() {
        log.info("cleaning up binlog dump download path:{}", downloadPath);
        try {
            RetryerBuilder.newBuilder().withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .retryIfException().build().call(() -> {
                    FileUtils.forceDelete(new File(downloadPath));
                    return null;
                });
        } catch (Exception e) {
            log.error("clean binlog dump path {} error", downloadPath, e);
        }
    }

    private long getFileSize(String fileName) {
        BinlogOssRecordService binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        return binlogOssRecordService.getRecordByName(logFileManager.getGroupName(), logFileManager.getStreamName(),
            DynamicApplicationConfig.getString(CLUSTER_ID), fileName).get().getLogSize();
    }

    public void setStartFile(String startFile) {
        this.startFile = startFile;
        if (!StringUtils.isEmpty(startFile)) {
            this.filePrefix = startFile.split("\\.")[0];
        }
    }

}
