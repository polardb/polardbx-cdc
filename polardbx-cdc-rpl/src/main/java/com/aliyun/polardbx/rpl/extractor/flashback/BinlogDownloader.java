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
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * @author ziyang.lb
 */
@Slf4j
public class BinlogDownloader {

    private final LinkedBlockingQueue<String> downloadFileQueue = new LinkedBlockingQueue<>();

    private ExecutorService executorService;

    private final int maxLocalFileNumber;

    private final AtomicInteger nLocalFile = new AtomicInteger(0);

    private boolean running = false;

    private final AtomicInteger nDownloadedFile = new AtomicInteger(0);

    private final int nDownloadThread;

    private final String localDirectory;

    public BinlogDownloader() {
        maxLocalFileNumber = DynamicApplicationConfig.getInt(ConfigKeys.FLASHBACK_BINLOG_MAX_DOWNLOAD_FILE_COUNT);
        nDownloadThread = DynamicApplicationConfig.getInt(ConfigKeys.FLASHBACK_BINLOG_DOWNLOAD_THREAD_NUM);
        localDirectory = DynamicApplicationConfig.getString(ConfigKeys.FLASHBACK_BINLOG_DOWNLOAD_DIR) + System.getProperty(TASK_NAME) + File.separator;

        log.info("maxLocalFileNumber: " + maxLocalFileNumber +
            ", nDownloadThread: " + nDownloadThread +
            ", localDirectory: " + localDirectory);
    }

    public void init() {
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                log.info("wait for all downloader complete!");
                executorService.awaitTermination(60L, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("stop old executor failed!", e);
            }
        }

        File dir = new File(localDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        } else {
            cleanLocalDirectory();
        }

        log.info("initialize new executor service");
        executorService = Executors.newFixedThreadPool(nDownloadThread, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("binlog-downloader");
            return t;
        });
    }

    public void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread t = new Thread(this::dispatchTask);

        t.setName("binlog-download-dispatcher");
        t.setDaemon(true);
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanLocalDirectory));
    }

    /**
     * 减少nLocalFile的值
     * 注意：具体删除文件的操作需要调用者自己来做
     */
    public void releaseOne() {
        nLocalFile.decrementAndGet();
    }

    public void download(String binlogFile) {
        downloadFileQueue.offer(binlogFile);
    }

    public void batchDownload(List<String> binlogFileList) {
        downloadFileQueue.addAll(binlogFileList);
    }

    public int getNumberOfDownloadedFile() {
        return nDownloadedFile.get();
    }

    private void dispatchTask() {
        while (running) {
            try {
                if (maxLocalFileNumber == -1 || maxLocalFileNumber >= nLocalFile.get()) {
                    String binlogFile = downloadFileQueue.poll(4, TimeUnit.SECONDS);
                    if (binlogFile == null) {
                        continue;
                    }
                    nLocalFile.incrementAndGet();
                    executorService.execute(new Downloader(binlogFile));
                }
                Thread.sleep(1000L);
            } catch (Exception e) {
                log.error("dispatcher download binlog failed!", e);
                System.exit(1);
            }
        }
    }

    private void cleanLocalDirectory() {
        File file = new File(localDirectory);
        if (!file.exists()) {
            log.error("local directory not exists");
        } else {
            try {
                FileUtils.cleanDirectory(new File(localDirectory));
            } catch (IOException e) {
                log.error("clean directory error!");
                System.exit(1);
            }
        }
    }

    public boolean isFinish() {
        return nLocalFile.get() == 0 && downloadFileQueue.isEmpty() && nDownloadedFile.get() > 0;
    }

    public class Downloader implements Runnable {

        private final String binlogFile;

        public Downloader(String binlogFile) {
            this.binlogFile = binlogFile;
        }

        @Override
        public void run() {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (binlogFile == null) {
                    return;
                }
                try {
                    log.info("start download binlog file " + binlogFile);
                    downloadBinlogFile(binlogFile);
                    log.info(binlogFile + " download finished");
                } catch (Exception e) {
                    log.error("download binlog file failed ! ", e);
                    System.exit(1);
                }
            } catch (Exception e) {
                log.error("download binlog file failed ! ", e);
                System.exit(1);
            }
        }

        private void downloadBinlogFile(String binlogFile) {
            RemoteBinlogProxy.getInstance().download(binlogFile, localDirectory);
            nDownloadedFile.incrementAndGet();
        }
    }

}
