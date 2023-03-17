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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.Constants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.io.IOException;
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
    private final ICdcFileSystem localFileSystem;
    private ICdcFileSystem remoteFileSystem;

    public CdcFileSystem(String binlogFileFullPath, String group, String stream) {
        localFileSystem = new LocalFileSystem(binlogFileFullPath, group, stream);
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            remoteFileSystem = new RemoteFileSystem(group, stream);
        }
    }

    public CdcFile createLocalFile(String fileName) {
        return localFileSystem.create(fileName);
    }

    public boolean deleteLocalFile(String fileName) {
        return localFileSystem.delete(fileName);
    }

    public boolean deleteRemoteFile(String fileName) {
        if (remoteFileSystem != null) {
            remoteFileSystem.delete(fileName);
        }
        return true;
    }

    public boolean delete(String fileName) {
        boolean res = deleteLocalFile(fileName);
        if (remoteFileSystem != null) {
            res &= deleteRemoteFile(fileName);
        }
        return res;
    }

    public boolean existInLocal(String fileName) {
        return localFileSystem.exist(fileName);
    }

    public boolean existOnRemote(String fileName) {
        return remoteFileSystem != null && remoteFileSystem.exist(fileName);
    }

    public boolean exist(String fileName) {
        boolean res = existInLocal(fileName);
        if (!res && remoteFileSystem != null) {
            res = existOnRemote(fileName);
        }
        return res;
    }

    public CdcFile getLocalFile(String fileName) {
        return localFileSystem.get(fileName);
    }

    public CdcFile getRemoteFile(String fileName) {
        if (remoteFileSystem == null) {
            return null;
        }
        return remoteFileSystem.get(fileName);
    }

    public CdcFile getBinlogFile(String fileName) {
        CdcFile res = localFileSystem.get(fileName);
        if (res == null && remoteFileSystem != null) {
            res = remoteFileSystem.get(fileName);
        }
        return res;
    }

    public BinlogFileReadChannel getLocalChannel(String fileName) throws IOException {
        return localFileSystem.getReadChannel(fileName);
    }

    public BinlogFileReadChannel getRemoteChannel(String fileName) throws IOException {
        if (remoteFileSystem == null) {
            return null;
        }
        return remoteFileSystem.getReadChannel(fileName);
    }

    public BinlogFileReadChannel getChannel(String fileName) throws IOException {
        BinlogFileReadChannel channel;

        Boolean testRemoteChannel =
            DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_REMOTE_CHANNEL_TEST_MODE);
        boolean preferLocalChannel = true;
        if (testRemoteChannel) {
            preferLocalChannel = new Random().nextBoolean();
        }

        BinlogFileReadChannel localChannel = getLocalChannel(fileName);
        BinlogFileReadChannel remoteChannel = getRemoteChannel(fileName);

        if (preferLocalChannel) {
            channel = ObjectUtils.firstNonNull(localChannel, remoteChannel);
        } else {
            channel = ObjectUtils.firstNonNull(remoteChannel, localChannel);
        }

        if (log.isDebugEnabled()) {
            if (channel == remoteChannel) {
                log.debug("read {} from remote channel", fileName);
            } else {
                log.debug("read {} from local channel", fileName);
            }
        }

        return channel;
    }

    public long getLocalFileSize(String fileName) {
        return localFileSystem.size(fileName);
    }

    private long getRemoteFileSize(String fileName) {
        return remoteFileSystem.size(fileName);
    }

    public long size(String fileName) {
        long size = getLocalFileSize(fileName);
        if (size < 0 && remoteFileSystem != null) {
            size = getRemoteFileSize(fileName);
        }
        return size;
    }

    public List<CdcFile> listLocalFiles() {
        return localFileSystem.listFiles();
    }

    public List<CdcFile> listRemoteFiles() {
        if (remoteFileSystem == null) {
            return new ArrayList<>();
        }
        return remoteFileSystem.listFiles();
    }

    public List<CdcFile> listBinlogFilesOrdered() {
        Map<String, CdcFile> fileMap = getLocalFileMap();
        if (remoteFileSystem != null) {
            List<CdcFile> remoteFiles = listRemoteFiles();
            for (CdcFile f : remoteFiles) {
                fileMap.putIfAbsent(f.getName(), f);
            }
        }
        ArrayList<CdcFile> res = new ArrayList<>(fileMap.values());
        res.sort(CdcFile::compareTo);
        return res;
    }

    private Map<String, CdcFile> getLocalFileMap() {
        Map<String, CdcFile> fileMap = new HashMap<>();
        List<CdcFile> localFiles = listLocalFiles();
        for (CdcFile f : localFiles) {
            fileMap.put(f.getName(), f);
        }
        return fileMap;
    }

    private Map<String, CdcFile> getRemoteFileMap() {
        Map<String, CdcFile> fileMap = new HashMap<>();
        if (remoteFileSystem == null) {
            return fileMap;
        }
        List<CdcFile> remoteFiles = listRemoteFiles();
        for (CdcFile f : remoteFiles) {
            fileMap.put(f.getName(), f);
        }
        return fileMap;
    }

    public CdcFile getLocalMinFile() {
        List<CdcFile> files = listLocalFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(0);
    }

    public CdcFile getRemoteMinFile() {
        if (remoteFileSystem == null) {
            return null;
        }
        List<CdcFile> files = listRemoteFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(0);
    }

    public CdcFile getMinFile() {
        List<CdcFile> files = listBinlogFilesOrdered();
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

    public CdcFile getRemoteMaxFile() {
        if (remoteFileSystem == null) {
            return null;
        }
        List<CdcFile> files = listRemoteFiles();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(files.size() - 1);
    }

    public CdcFile getMaxFile() {
        List<CdcFile> files = listBinlogFilesOrdered();
        if (files.isEmpty()) {
            return null;
        }
        return files.get(files.size() - 1);
    }

    public String getLocalFullName(String fileName) {
        return localFileSystem.getName(fileName);
    }

    public String getRemoteFullName(String fileName) {
        if (remoteFileSystem == null) {
            return null;
        }
        return remoteFileSystem.getName(fileName);
    }

    /**
     * SQL闪回专用，列举指定时间范围内的binlog
     * 用TreeSet保证binlog文件名的有序性
     */
    public static List<String> listGlobalBinlogFilesBetweenTimeRange(long startTime, long endTime) {
        TreeSet<String> set = new TreeSet<>();

        Date startDate = new Date(startTime);
        Date endDate = new Date(endTime);
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);

        List<BinlogOssRecord> unfinishedFiles = mapper.select(s -> s.
            where(BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(Constants.GROUP_NAME_GLOBAL)).
            and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(Constants.STREAM_NAME_GLOBAL)).
            and(BinlogOssRecordDynamicSqlSupport.logEnd, SqlBuilder.isNull()).
            and(BinlogOssRecordDynamicSqlSupport.logBegin, SqlBuilder.isLessThanOrEqualTo(endDate))
        );
        List<BinlogOssRecord> finishedFiles = mapper.select(s -> s.
            where(BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(Constants.GROUP_NAME_GLOBAL)).
            and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(Constants.STREAM_NAME_GLOBAL)).
            and(BinlogOssRecordDynamicSqlSupport.logEnd, SqlBuilder.isGreaterThanOrEqualTo(startDate)).
            and(BinlogOssRecordDynamicSqlSupport.logBegin, SqlBuilder.isLessThanOrEqualTo(endDate))
        );
        for (BinlogOssRecord record : unfinishedFiles) {
            set.add(record.getBinlogFile());
        }
        for (BinlogOssRecord record : finishedFiles) {
            set.add(record.getBinlogFile());
        }

        return new ArrayList<>(set);
    }
}