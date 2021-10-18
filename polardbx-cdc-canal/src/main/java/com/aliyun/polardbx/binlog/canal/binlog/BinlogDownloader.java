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

package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadExceptionListener;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BinlogDownloader implements DownloadExceptionListener {

    private static final Logger logger = LoggerFactory.getLogger(BinlogDownloader.class);
    private static final BinlogDownloader instance = new BinlogDownloader();

    private final Map<String, LinkedBlockingQueue<DownloadTask>> taskQueueMap = Maps.newConcurrentMap();
    private final Map<String, AtomicInteger> downloadTaskCounterMap = Maps.newConcurrentMap();
    private String path;
    private boolean run;
    private volatile Throwable taskException;
    private ExecutorService executorService;
    private Thread mainDownloadThread;
    private boolean init = false;

    public static BinlogDownloader getInstance() {
        return instance;
    }

    public void init(String path, final int storageCount) {
        logger.warn("init downloader with : " + storageCount);

        this.path = path;
        int threadSize = storageCount + 2;
        this.executorService = Executors.newFixedThreadPool(threadSize, r -> {
            Thread t = new Thread(r, "binlog-download-thread");
            t.setDaemon(true);
            return t;
        });
        this.init = true;
    }

    public void addDownloadTask(String storageInstanceId, DownloadTask task) {
        task.registerListener(this);
        LinkedBlockingQueue<DownloadTask> blockingQueue = taskQueueMap.get(storageInstanceId);
        if (blockingQueue == null) {
            blockingQueue = new LinkedBlockingQueue<>();
            taskQueueMap.put(storageInstanceId, blockingQueue);
        }
        blockingQueue.add(task);
    }

    private void calculateBinlogFile(Map<String, Integer> indexCounterMap) {
        indexCounterMap.clear();
        for (String storeageInstanceId : taskQueueMap.keySet()) {
            File localDir = new File(path + File.separator + storeageInstanceId);
            if (localDir.exists()) {
                indexCounterMap.put(storeageInstanceId, localDir.listFiles(
                    (dir, name) -> {
                        int idx = name.indexOf(".");
                        if (idx < 0) {
                            return false;
                        }
                        return name.substring(idx + 1).matches("\\d+");
                    }).length);
            }
        }
    }

    public void start() {
        if (!init) {
            logger.warn("not init binlog downloader ,will not start!");
            return;
        }

        prepare();
        run = true;
        mainDownloadThread = new Thread(() -> {
            Map<String, Integer> indexCounterMap = Maps.newHashMap();
            while (run) {
                try {
                    if (taskException != null) {
                        logger.error("downloader occur exception, will exist!");
                        Runtime.getRuntime().halt(1);
                    }

                    calculateBinlogFile(indexCounterMap);
                    if (CollectionUtils.isEmpty(taskQueueMap)) {
                        try {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                        } catch (Exception e) {

                        }
                        continue;
                    }
                    for (Map.Entry<String, LinkedBlockingQueue<DownloadTask>> entry : taskQueueMap.entrySet()) {
                        Integer counter = indexCounterMap.get(entry.getKey());
                        AtomicInteger willRunTask = downloadTaskCounterMap.get(entry.getKey());
                        int totalCounter;
                        if (counter == null) {
                            counter = 0;
                        }
                        if (willRunTask == null) {
                            willRunTask = new AtomicInteger(0);
                            downloadTaskCounterMap.put(entry.getKey(), willRunTask);
                        }
                        totalCounter = counter + willRunTask.get();
                        if (totalCounter >= 3) {
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                            } catch (Exception e) {
                            }
                            continue;
                        }
                        DownloadTask task =
                            entry.getValue().poll(1, TimeUnit.SECONDS);
                        if (task == null) {
                            continue;
                        }
                        willRunTask.incrementAndGet();
                        executorService.execute(task);
                    }

                } catch (Exception e) {
                    logger.error("main download thread error", e);
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException interruptedException) {

                    }
                }

            }
        }, "main-download-thread");
        mainDownloadThread.setDaemon(true);
        mainDownloadThread.start();
    }

    private void prepare() {
        try {
            FileUtils.forceMkdir(new File(path));
            FileUtils.cleanDirectory(new File(path));
        } catch (IOException e) {
            throw new PolardbxException("Clean local binlog directory failed.", e);
        }
    }

    public void stop() {
        run = false;
        mainDownloadThread.interrupt();
    }

    @Override
    public void beginDownload(String storageInstanceId) {
        logger.info("begin download for storage " + storageInstanceId);
    }

    @Override
    public void catchException(Throwable t) {
        this.taskException = t;
    }

    @Override
    public void endDownload(String storageInstanceId) {
        downloadTaskCounterMap.get(storageInstanceId).decrementAndGet();
    }
}
