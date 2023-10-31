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
/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.TaskContext;
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
public class BinlogUrlDownloader {

    private final LinkedBlockingQueue<BinlogFile> downloadFileQueue = new LinkedBlockingQueue<>();
    private final int maxLocalFileNumber;
    private final AtomicInteger nLocalFile = new AtomicInteger(0);
    private final AtomicInteger nDownloadedFile = new AtomicInteger(0);
    private final int nDownloadThread;
    private final String localDirectory;
    private ExecutorService executorService;
    private volatile boolean running = false;

    public BinlogUrlDownloader() {
        maxLocalFileNumber = 3;
        nDownloadThread = 3;
        localDirectory =
            DynamicApplicationConfig.getString(ConfigKeys.FLASHBACK_BINLOG_DOWNLOAD_DIR) + System.getProperty(TASK_NAME)
                + File.separator;

        log.info("maxLocalFileNumber: " + maxLocalFileNumber +
            ", nDownloadThread: " + nDownloadThread +
            ", localDirectory: " + localDirectory);
    }

    public void init() throws IOException {
        downloadFileQueue.clear();
        nLocalFile.set(0);
        nDownloadedFile.set(0);

        if (executorService != null) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(60L, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("stop old executor failed!", e);
            }
        }

        log.info("all downloader complete!");

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

        // Runtime.getRuntime().addShutdownHook(new Thread(this::cleanLocalDirectory));
    }

    /**
     * 减少nLocalFile的值
     * 注意：具体删除文件的操作需要调用者自己来做
     */
    public void releaseOne() {
        nLocalFile.decrementAndGet();
    }

    public void download(BinlogFile binlogFile) {
        downloadFileQueue.offer(binlogFile);
    }

    public void batchDownload(List<BinlogFile> binlogFileList) {
        downloadFileQueue.addAll(binlogFileList);
    }

    public int getNumberOfDownloadedFile() {
        return nDownloadedFile.get();
    }

    private void dispatchTask() {
        while (running) {
            try {
                if (maxLocalFileNumber == -1 || maxLocalFileNumber >= nLocalFile.get()) {
                    BinlogFile binlogFile = downloadFileQueue.poll(4, TimeUnit.SECONDS);
                    if (binlogFile == null) {
                        continue;
                    }
                    log.info("try submit download task: {}", binlogFile.getLogname());
                    nLocalFile.incrementAndGet();
                    executorService.execute(new Downloader(binlogFile));
                }
                Thread.sleep(1000L);
            } catch (Exception e) {
                log.error("dispatcher download binlog failed!", e);
                TaskContext.getInstance().getPipeline().stop();
            }
        }
    }

    private void cleanLocalDirectory() throws IOException {
        log.warn("clean local directory");
        File file = new File(localDirectory);
        if (!file.exists()) {
            log.error("local directory not exists");
        } else {
            try {
                FileUtils.cleanDirectory(new File(localDirectory));
            } catch (IOException e) {
                log.error("clean directory error!", e);
                throw e;
            }
        }
    }

    public boolean isFinish() {
        log.warn("nLocalFile: {}, downloadFileQueue: {}, nDownloadedFile: {}", nLocalFile,
            downloadFileQueue, nDownloadedFile);
        return nLocalFile.get() == 0 && downloadFileQueue.isEmpty() && nDownloadedFile.get() > 0;
    }

    public class Downloader implements Runnable {

        private final BinlogFile binlogFile;

        public Downloader(BinlogFile binlogFile) {
            this.binlogFile = binlogFile;
        }

        @Override
        public void run() {
            try {
                if (Thread.currentThread().isInterrupted() || !running) {
                    return;
                }
                if (binlogFile == null) {
                    return;
                }
                String binlogFileName = binlogFile.getLogname();
                String path = localDirectory + File.separator + binlogFileName;
                File f = new File(path);
                if (!f.exists()) {
                    HttpHelper
                        .download(binlogFile.getIntranetDownloadLink(), path);
                    nDownloadedFile.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("download binlog file failed ! ", e);
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                    TaskContext.getInstance().getTaskId(), "download binlog error");
                StatisticalProxy.getInstance().recordLastError(e.toString());
                TaskContext.getInstance().getPipeline().stop();
            }
        }
    }

}

