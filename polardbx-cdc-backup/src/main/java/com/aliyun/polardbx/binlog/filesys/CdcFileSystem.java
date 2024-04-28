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
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * CDC file system架构：
 * ------------------------------------------------------
 * |                 CDC File System                    |
 * ------------------------------------------------------
 * |         Remote File System   |                     |
 * --------------------------------                     |
 * |         Remote Binlog Proxy  |                     |
 * --------------------------------   Local File System |
 * |    OSS      |    Lindorm     |                     |
 * --------------------------------                     |
 * | OSSManager  | LindormManager |                     |
 * ------------------------------------------------------
 *
 * @author yudong
 * @since 2022/8/19
 **/
@Slf4j
public class CdcFileSystem {
    private final LocalFileSystem localFileSystem;
    private RemoteFileSystem remoteFileSystem;

    public CdcFileSystem(String rootPath, String group, String stream) {
        localFileSystem = new LocalFileSystem(rootPath, group, stream);
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            remoteFileSystem = new RemoteFileSystem(group, stream);
        }
    }

    public File newLocalFile(String fileName) {
        return localFileSystem.newFile(fileName);
    }

    public void deleteRemoteFile(String fileName) {
        if (remoteFileSystem != null) {
            remoteFileSystem.delete(fileName);
        }
    }

    public List<CdcFile> listLocalFiles() {
        return localFileSystem.listFiles();
    }

    public List<CdcFile> listAllFiles() {
        Map<String, CdcFile> fileMap = getLocalFileMap();
        if (remoteFileSystem != null) {
            List<CdcFile> remoteFiles = listRemoteFiles();
            for (CdcFile f : remoteFiles) {
                if (fileMap.containsKey(f.getName())) {
                    fileMap.get(f.getName()).setRecord(f.getRecord());
                } else {
                    fileMap.putIfAbsent(f.getName(), f);
                }
            }
        }
        ArrayList<CdcFile> res = new ArrayList<>(fileMap.values());
        res.sort(CdcFile::compareTo);
        return res;
    }

    public CdcFile getBinlogFile(String fileName) {
        CdcFile localFile;
        CdcFile remoteFile;

        // used for local develop test
        Boolean forceConsumeRemoteFile =
            DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_FORCE_STREAMING_CONSUME_ENABLED);
        if (forceConsumeRemoteFile) {
            remoteFile = waitRemoteFile(fileName, Long.MAX_VALUE);
            log.info("get file {} from remote", fileName);
            return remoteFile;
        }

        // used for lab test
        Boolean randomTest =
            DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_TEST_STREAMING_CONSUME_ENABLED);
        if (randomTest && new Random().nextBoolean()) {
            remoteFile = waitRemoteFile(fileName, 15 * 1000);
            if (remoteFile != null) {
                log.info("get file {} from remote", remoteFile.getName());
                return remoteFile;
            } else {
                log.info("remote file {} is absent", fileName);
            }
        }

        // used for production
        localFile = localFileSystem.get(fileName);
        if (localFile != null) {
            log.info("get file {} from local", localFile.getName());
            return localFile;
        }
        remoteFile = remoteFileSystem == null ? null : remoteFileSystem.get(fileName);
        if (remoteFile != null) {
            log.info("get file {} from remote", remoteFile.getName());
            return remoteFile;
        } else {
            throw new RuntimeException("can't get file " + fileName + " from local or remote");
        }
    }

    /**
     * 需要增加超时机制，否则实验室链路预检阶段一直卡在最后一个文件，可能导致wait token超时
     */
    private CdcFile waitRemoteFile(String fileName, long millisToWait) {
        CdcFile remoteFile = remoteFileSystem.get(fileName);
        long start = System.currentTimeMillis();
        while (remoteFile == null) {
            try {
                if (System.currentTimeMillis() - start > millisToWait) {
                    log.warn("wait for remote file {} upload timeout", fileName);
                    break;
                }
                log.info("waiting for remote file {} upload success", fileName);
                Thread.sleep(5000);
            } catch (Exception e) {
                log.warn("error while waiting for remote file {}", fileName, e);
                throw new RuntimeException(e);
            }
            remoteFile = remoteFileSystem.get(fileName);
        }
        return remoteFile;
    }

    private Map<String, CdcFile> getLocalFileMap() {
        Map<String, CdcFile> fileMap = new HashMap<>();
        List<CdcFile> localFiles = listLocalFiles();
        for (CdcFile f : localFiles) {
            fileMap.put(f.getName(), f);
        }
        return fileMap;
    }

    private List<CdcFile> listRemoteFiles() {
        if (remoteFileSystem == null) {
            return new ArrayList<>();
        }
        return remoteFileSystem.listFiles();
    }

    public CdcFile getMinFile() {
        List<CdcFile> files = listAllFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(0);
    }

    public CdcFile getLocalMaxFile() {
        List<CdcFile> files = listLocalFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(files.size() - 1);
    }

    public CdcFile getMaxFile() {
        List<CdcFile> files = listAllFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(files.size() - 1);
    }

    public String getLocalFullName(String fileName) {
        return localFileSystem.getFullName(fileName);
    }

    /**
     * SQL闪回专用，列举指定时间范围内的binlog
     * 用TreeSet保证binlog文件名的有序性
     */
    public static List<String> listFilesInTimeRange(long startTime, long endTime) {
        TreeSet<String> set = new TreeSet<>();
        Date startDate = new Date(startTime);
        Date endDate = new Date(endTime);
        BinlogOssRecordService service = SpringContextHolder.getObject(BinlogOssRecordService.class);
        List<BinlogOssRecord> recordsInTimeRange =
            service.getRecordsInTimeRange(CommonConstants.GROUP_NAME_GLOBAL, CommonConstants.STREAM_NAME_GLOBAL,
                startDate,
                endDate);
        recordsInTimeRange.forEach(r -> set.add(r.getBinlogFile()));
        return new ArrayList<>(set);
    }
}
