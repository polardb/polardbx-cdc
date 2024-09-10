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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
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
import org.slf4j.MDC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
     * 开始下载的文件
     */
    private String startFile;

    /**
     * 下载文件列表
     */
    private final List<String> downloadList;

    /**
     * 下载线程池
     */
    private final ThreadPoolExecutor downloadThreadPool;

    /**
     * 滑动窗口左边缘
     */
    private int left = 0;

    /**
     * 滑动窗口右边缘
     */
    private int right = 0;

    private final Map<String, Throwable> fileDownLoadErrorMap;

    /**
     * 是否全部结束
     */
    @Getter
    private boolean finished = false;

    private final LocalFileSystem fileSystem;
    private final long masterHeartbeatPeriod;

    private final ServerCallStreamObserver<DumpStream> observer;

    // used to send heartbeat while waiting
    private final BinlogDumpReader dumpReader;

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
        this.downloadList = new ArrayList<>();
        this.downloadThreadPool =
            new ThreadPoolExecutor(windowSize, windowSize * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("binlog-dump-download-thread-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.fileSystem =
            new LocalFileSystem(downloadPath, logFileManager.getGroupName(), logFileManager.getStreamName());
        this.fileDownLoadErrorMap = new ConcurrentHashMap<>();

        log.info("start file:{}, window size:{}, download path:{}", startFile, windowSize, downloadPath);
    }

    public void start() {
        getDownloadFileList(startFile);
        if (downloadList.isEmpty()) {
            log.info("download file list is empty, will not start binlog dump downloader");
            close();
        }

        for (int i = 0; i < windowSize; i++) {
            moveRight();
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
     * 上一个binlog文件已经消费完成了，滑动窗口向右滑动一步，同时清理binlog文件
     */
    @Override
    public void onRotate() {
        if (isFinished()) {
            return;
        }

        moveRight();
        moveLeft();

        if (left == downloadList.size()) {
            // 透明消费追数据过程中可能有些本地文件被清理
            startFile = BinlogFileUtil.getNextBinlogFileName(downloadList.get(left - 1));
            getDownloadFileList(startFile);
            if (downloadList.isEmpty()) {
                close();
            } else {
                for (int i = 0; i < windowSize; i++) {
                    moveRight();
                }
            }
        }
    }

    public CdcFile getFile(String fileName) throws Exception {
        if (isFinished()) {
            log.warn("try to get file:{} from binlog dump downloader after finished!", fileName);
            return null;
        }

        wait(fileName);
        return fileSystem.get(fileName);
    }

    private void wait(String fileName) throws Exception {
        try {
            File f = fileSystem.newFile(fileName);
            long maxWaitSeconds =
                DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS);
            Timer waitTimeoutTimer = new Timer(maxWaitSeconds * 1000);
            Timer heartbeatTimer = new Timer(masterHeartbeatPeriod / 1000000);
            long sleepTime = Math.min(1000, masterHeartbeatPeriod / 1000000);
            long start = System.currentTimeMillis();
            while (!observer.isCancelled() && !f.exists()) {
                if (fileDownLoadErrorMap.containsKey(fileName)) {
                    throw new IOException("download file " + fileName + " from oss error!",
                        fileDownLoadErrorMap.get(fileName));
                }

                if (waitTimeoutTimer.isTimeout()) {
                    throw new IOException("download file " + fileName + " from oss timeout!");
                }

                // 等待文件下载过程中需要向slave发送心跳，防止下载时间过长导致slave等待超时
                if (heartbeatTimer.isTimeout()) {
                    observer.onNext(DumpStream.newBuilder().setPayload(dumpReader.heartbeatEventPacket()).build());
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

    private void moveRight() {
        if (right == downloadList.size()) {
            return;
        }

        String pureFileName = downloadList.get(right++);
        String remoteFileName = BinlogFileUtil.buildRemoteFilePartName(pureFileName, logFileManager.getGroupName(),
            logFileManager.getStreamName());
        downloadThreadPool.submit(() -> {
            try {
                MDC.put(MDC_THREAD_LOGGER_KEY, MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP);
                fileDownLoadErrorMap.remove(pureFileName);
                log.info("start to download file {}", pureFileName);
                Stopwatch stopwatch = Stopwatch.createStarted();
                RemoteBinlogProxy.getInstance().download(remoteFileName, downloadPath);
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
        });
    }

    private void moveLeft() {
        if (left == downloadList.size()) {
            return;
        }

        String fileName = downloadList.get(left++);
        File f = fileSystem.newFile(fileName);
        boolean b = f.delete();
        if (b) {
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
        BinlogOssRecordService recordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        List<BinlogOssRecord> records =
            recordService.getRecordsForBinlogDump(logFileManager.getGroupName(), logFileManager.getStreamName(),
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID), startFile);
        List<String> filesToDownload =
            records.stream().map(BinlogOssRecord::getBinlogFile).filter(f -> !localFiles.contains(f))
                .collect(Collectors.toList());

        downloadList.clear();
        left = 0;
        right = 0;
        log.info("download file list:{}", filesToDownload);
        downloadList.addAll(filesToDownload);
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

}
