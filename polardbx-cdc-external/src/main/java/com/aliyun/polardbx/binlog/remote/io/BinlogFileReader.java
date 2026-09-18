/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.io;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_WAIT_DATA_TIMEOUT_MS;

/**
 * @author chengjin, yudong
 * <p>
 * 读取一个binlog的数据到给定的Buffer中
 */
@Slf4j
public class BinlogFileReader implements IFileReader {
    private final String binlogFileName;
    private final String binlogFullPath;
    private final int fileSequence;
    private final BinlogFileStatusChecker checker;
    private final File localFile;
    private RandomAccessFile io;
    private int totalReadSize = 0;
    private Object fileKey;

    public BinlogFileReader(String binlogFileName, String binlogFullPath, BinlogFileStatusChecker checker) {
        this.binlogFileName = binlogFileName;
        this.binlogFullPath = binlogFullPath;
        this.checker = checker;
        this.localFile = new File(binlogFullPath, binlogFileName);
        this.fileSequence = BinlogFileUtil.getBinlogSequence(binlogFileName);
    }

    @Override
    public long length() {
        try {
            return localFile.length();
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    public String getName() {
        return binlogFileName;
    }

    @Override
    public int read(byte[] buffer) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        int currentReadLen = 0;
        boolean needWait = true;
        do {
            if (Thread.interrupted()) {
                throw new InterruptedException("read thread is interrupted");
            }

            if (io == null && localFile.exists()) {
                io = new RandomAccessFile(localFile, "r");
                BasicFileAttributes fileAttr =
                    Files.readAttributes(localFile.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                fileKey = fileAttr.fileKey();
            }
            if (io != null) {
                needWait = checker.needWait(totalReadSize, localFile.getName());
                if (log.isDebugEnabled()) {
                    log.debug("needWait value is " + needWait);
                }
            }
            if (needWait) {
                checkTimeOut(start);
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
                debug();
            } else {
                int tmpLen = io.read(buffer, currentReadLen, buffer.length - currentReadLen);
                // check if read size not equal last cursor
                if (tmpLen == -1) {
                    if (totalReadSize == 0) {
                        try {
                            io.close();
                        } catch (Exception e) {
                        }
                        io = null;
                        needWait = true;
                        continue;
                    } else {
                        BasicFileAttributes fileAttr =
                            Files.readAttributes(localFile.toPath(), BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS);
                        if (!fileKey.equals(fileAttr.fileKey())) {
                            // 此时文件被LogFileGenerator中的prepare方法重建了，需要重新init io，详见
                            // https://aliyuque.antfin.com/coronadb/knddog/ggecx612znopghbz
                            fileKey = fileAttr.fileKey();
                            io = new RandomAccessFile(localFile, "r");
                            io.seek(totalReadSize);
                            needWait = true;
                            tmpLen = 0;
                        }
                    }
                }
                totalReadSize += tmpLen;
                currentReadLen += tmpLen;
            }
        } while (needWait);
        return currentReadLen;
    }

    @Override
    public boolean isComplete() {
        return checker.isCompleteFile(fileSequence);
    }

    private void debug() {
        if (log.isDebugEnabled()) {
            log.debug(toString());
        }
    }

    //进行超时检测，避免死循环
    private void checkTimeOut(long start) {
        int timeout = DynamicApplicationConfig.getInt(BINLOG_BACKUP_UPLOAD_WAIT_DATA_TIMEOUT_MS);
        if (checker.isCompleteFile(fileSequence) && System.currentTimeMillis() - start > timeout) {
            throw new PolardbxException("wait file data [" + binlogFileName + "] timeout!");
        }
    }

    @Override
    public void close() {
        if (io != null) {
            try {
                io.close();
            } catch (IOException e) {
            }
        }
        io = null;
    }

    @Override
    public String toString() {
        return "BinlogFetcher{" +
            "binlogFile='" + binlogFileName + '\'' +
            ", path='" + binlogFullPath + '\'' +
            ", cursorProvider=" + checker +
            ", io=" + io +
            ", localFile=" + localFile +
            ", totalReadSize=" + totalReadSize +
            '}';
    }
}
