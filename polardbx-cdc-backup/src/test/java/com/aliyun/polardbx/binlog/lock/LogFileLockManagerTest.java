/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.lock;

import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogFileLockManagerTest extends BaseTest {
    private LogFileLockManager logFileLockManager;
    private LogFileLockManagerCollection logFileLockManagerCollection;
    private final String groupName = "group_global";
    private final String streamName = "stream_global";
    private final String clusterId = "lock_test_cluster";
    private final String taskName = "lock_test_task";
    private final String rootPath = "lock_test";
    private static final String binlogFilePrefix = "binlog.";
    private LocalFileSystem localFileSystem;
    private final int totalFileNum = 200;

    @Before
    @SneakyThrows
    public void setUp() {
        logFileLockManagerCollection = new LogFileLockManagerCollection();
        StreamContext context = new StreamContext(groupName, null, clusterId, taskName, TaskType.Dumper, 1);
        logFileLockManager = new LogFileLockManager(streamName, context);
        localFileSystem = new LocalFileSystem(rootPath, groupName, streamName);
        logFileLockManager.setLocalFileSystem(localFileSystem);
        logFileLockManagerCollection.add(streamName, logFileLockManager);
        prepareFiles();
        logFileLockManagerCollection.start();
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(new File(rootPath));
    }

    private void prepareFiles() throws IOException {
        String content = "hello, world";
        // generate some local files
        File dir = new File(rootPath);
        dir.createNewFile();
        for (int i = 1; i <= totalFileNum; i++) {
            String localName = localFileSystem.getFullName(binlogFilePrefix + String.format("%06d", i));
            File f = new File(localName);
            f.createNewFile();
            PrintWriter writer = new PrintWriter(f);
            writer.print(content);
            writer.close();
        }
    }

    @Test
    public void testParallelReadWriteLock() throws InterruptedException {
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal = new CountDownLatch(120);
        final AtomicBoolean writeSuccess = new AtomicBoolean(false);
        final AtomicBoolean readSuccess = new AtomicBoolean(false);
        final List<CdcFile> files = localFileSystem.listFiles();

        ExecutorService executor = Executors.newFixedThreadPool(120);

        // Read threads
        for (int i = 0; i < 60; i++) { // Increase the number of read threads
            final int idx = i;
            executor.execute(() -> {
                try {
                    startSignal.await();
                    String fileName = files.get(idx).getName();
                    logFileLockManager.readLock(fileName);
                    if (localFileSystem.exist(fileName)) {
                        // 在持有本地文件锁期间不应被删除
                        Thread.sleep(2000);
                        Assert.assertTrue(localFileSystem.exist(fileName));
                    }
                    logFileLockManager.unLockRead(fileName);
                    readSuccess.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Write threads
        for (int i = 0; i < 60; i++) {
            // Increase the number of write threads
            final int idx = i;
            executor.execute(() -> {
                try {
                    startSignal.await();
                    String fileName = files.get(idx).getName();
                    boolean lockAcquired = logFileLockManager.tryWriteLock(fileName);
                    if (lockAcquired) {
                        localFileSystem.delete(fileName);
                        logFileLockManager.removeFileLock(fileName);
                        writeSuccess.set(true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // let all threads proceed
        startSignal.countDown();
        // wait for all to finish
        doneSignal.await();

        executor.shutdown();

        // Verify that both read and write operations were successful
        assert writeSuccess.get() : "Write operation should have succeeded";
        assert readSuccess.get() : "Read operation should have succeeded";
    }
}
