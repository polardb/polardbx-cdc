/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.lock;

import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author zm
 * 负责管理本地文件读写锁，当文件正在被dump时，不应被删除。
 */
@Slf4j
public class LogFileLockManager {
    private final Map<String, ReentrantReadWriteLock> fileLockMap;
    @Setter
    private LocalFileSystem localFileSystem;
    private final String streamName;

    public LogFileLockManager(String streamName, StreamContext context) {
        fileLockMap = new ConcurrentHashMap<>();
        this.streamName = streamName;
        String rootPath = BinlogFileUtil.getRootPath(context.getTaskType(), context.getVersion());
        this.localFileSystem = new LocalFileSystem(rootPath, context.getGroup(), streamName);
    }

    public void init() {
        clear();
        // 不再在初始化的时候就尝试加文件锁，转而使用了懒加载的方法
        log.info("{} log file lock manager is running...", streamName);
    }

    public void putFileLock(String fileName) {
        log.info("put lock file: {}", fileName);
        fileLockMap.putIfAbsent(fileName, new ReentrantReadWriteLock());
    }

    public ReentrantReadWriteLock getFileLock(String fileName) {
        return fileLockMap.get(fileName);
    }

    public void removeFileLock(String fileName) {
        unLockWrite(fileName);
        log.info("remove lock file: {}", fileName);
        fileLockMap.remove(fileName);
    }

    public void readLock(String fileName) {
        ReentrantReadWriteLock lock = getFileLock(fileName);
        // 防止实验室中文件因为不是被cleaner删除没有被追踪到的情况（如forced recover）
        if (lock == null && localFileSystem.exist(fileName)) {
            putFileLock(fileName);
            lock = getFileLock(fileName);
        }
        if (lock != null) {
            log.info("try read lock file: {}", fileName);
            lock.readLock().lock();
            log.info("read locked file: {}", fileName);
        }
    }

    public boolean tryWriteLock(String fileName) {
        ReentrantReadWriteLock lock = getFileLock(fileName);
        if (lock == null && localFileSystem.exist(fileName)) {
            putFileLock(fileName);
            lock = getFileLock(fileName);
        }
        log.info("try write lock file: {}", fileName);
        return lock.writeLock().tryLock();
    }

    public void unLockRead(String fileName) {
        ReentrantReadWriteLock lock = getFileLock(fileName);
        if (lock != null) {
            log.info("read unlock file: {}", fileName);
            try {
                lock.readLock().unlock();
            } catch (Exception e) {
                log.warn("unlock file {} failed!", fileName, e);
            }
        }
    }

    public void unLockWrite(String fileName) {
        log.info("write unlock file: {}", fileName);
        ReentrantReadWriteLock lock = getFileLock(fileName);
        try {
            lock.writeLock().unlock();
        } catch (Exception e) {
            log.warn("unlock file {} failed!", fileName, e);
        }
    }

    public void clear() {
        fileLockMap.clear();
    }
}
