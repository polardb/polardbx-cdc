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
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Binlog下载工具，负责将远端存储的binlog文件下载到本地
 *
 * @author yudong
 * @since 2022/12/8 17:19
 **/
@Slf4j
public class BinlogDownloader {
    private final String group;
    private final String stream;
    private final String binlogFullPath;
    private final List<String> downloadFiles;
    /**
     * 标志文件，开始下载binlog文件之前创建文件，文件全部下载完成之后删除文件
     * 如果BinlogDownloader启动后发现binlog目录下有该文件，说明上次下载的过程中出现了异常，所以需要清空目录，重新下载
     */
    private final String lockFileName;

    public BinlogDownloader(String group, String stream, String binlogFullPath, List<String> downloadFiles) {
        this.group = group;
        this.stream = stream;
        this.downloadFiles = downloadFiles;
        this.binlogFullPath = binlogFullPath;
        this.lockFileName = binlogFullPath + File.separator + "LOCK";
    }

    public void start() {
        log.info("binlog downloader start to run");
        File dir = new File(binlogFullPath);
        try {
            FileUtils.forceMkdir(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 如果文件锁存在，说明之前下载过程出现中断退出的情况，需要重新下载
        // 如果文件锁不存在，可能是首次启动没有创建文件锁，也有可能是上次下载过程顺利完成，删除了文件锁
        if (hasLock()) {
            log.info("detect download lock, will clean previous downloaded binlog files");
            deleteBadFiles();
        }

        if (needDownload()) {
            log.info("there is no binlog file in local dir, start to download");
            startDownload();
        } else {
            log.info("there are some binlog files in local dir, will not download again");
        }
    }

    private boolean needDownload() {
        List<File> localFiles = BinlogFileUtil.listLocalBinlogFiles(binlogFullPath, group, stream);
        return localFiles.isEmpty() && !downloadFiles.isEmpty();
    }

    private void deleteBadFiles() {
        File dir = new File(binlogFullPath);
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.stream(files).forEach(f -> {
                try {
                    FileUtils.forceDelete(f);
                    log.warn("binlog file {} is deleted", f.getName());
                } catch (IOException e) {
                    throw new PolardbxException("delete failed.", e);
                }
            });
        }
    }

    private void startDownload() {
        try {
            lock();
            for (String fileName : downloadFiles) {
                doDownload(fileName);
            }
            unlock();
        } catch (Throwable e) {
            log.error("download files error", e);
        }
    }

    private void doDownload(String fileName) throws Throwable {
        String binlogRootPath = BinlogFileUtil.extractRootPathFromFullPath(binlogFullPath, group, stream);
        String remoteFileName = BinlogFileUtil.buildRemoteFilePartName(fileName, group, stream);
        if (RemoteBinlogProxy.getInstance().isObjectsExistForPrefix(remoteFileName)) {
            log.info("start download remote binlog file {}", remoteFileName);
            RemoteBinlogProxy.getInstance().download(remoteFileName, binlogRootPath);
            log.info("success download remote binlog file {}", remoteFileName);
        } else {
            log.warn("binlog file {} does not exist on remote", remoteFileName);
        }
    }

    private void lock() {
        try {
            File file = new File(lockFileName);
            file.createNewFile();
            log.info("create lock file {} succeeded.", file.getName());
        } catch (IOException e) {
            throw new PolardbxException("lock file failed", e);
        }
    }

    private void unlock() {
        File file = new File(lockFileName);
        file.delete();
        log.info("delete lock file {} succeeded.", file.getName());
    }

    private boolean hasLock() {
        File file = new File(lockFileName);
        return file.exists();
    }
}
