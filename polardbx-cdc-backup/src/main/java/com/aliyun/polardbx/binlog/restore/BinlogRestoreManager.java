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

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责Dumper重启后的恢复工作
 * 目前有两种恢复方式：
 * 1.有备份存储，从备份存储上下载最近产生的3个binlog，从中搜索最后一个tso
 * 2.无备份存储，Daemon在构造集群拓扑时会计算出一个tso
 *
 * @author yudong
 * @since 2022/12/2 15:20
 **/
@Slf4j
public class BinlogRestoreManager {
    private final String groupName;
    private final String streamName;
    private final String binlogFullPath;

    public BinlogRestoreManager(String groupName, String streamName, String binlogFullPath) {
        this.groupName = groupName;
        this.streamName = streamName;
        this.binlogFullPath = binlogFullPath;
    }

    public void start() {
        log.info("binlog restore manager start to run");
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            int n = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DOWNLOAD_LAST_NUM);
            List<String> downloadFiles = getDownloadFiles(n);
            if (log.isDebugEnabled()) {
                log.info("download file list:{}", downloadFiles);
            }
            BinlogDownloader downloader = new BinlogDownloader(groupName, streamName, binlogFullPath, downloadFiles);
            downloader.start();
        }
    }

    /**
     * 获得需要从远端存储下载的文件列表
     * 1. 找文件编号最小的上传中的文件
     * 2. 如果找到，下载该文件以及该文件之前的n个文件，下载最近产生的这个不完整的文件的目的是为了seekLastTso更快
     * 3. 如果没有找到，则下载最近上传成功的n个文件
     *
     * @param n number of files
     * @return binlog file name list
     */
    private List<String> getDownloadFiles(int n) {
        List<String> res = new ArrayList<>();
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        String firstUploadingFile = getFirstUploadingFile();
        List<BinlogOssRecord> recordList;
        // firstUploadingFile==null可能有两种情况：
        // 1. 没有开启远端存储
        // 2. 开启了远端存储，所有文件都上传成功
        // 这里把uploadStatus==Success条件加上，就是为了在第一种情况下返回空列表
        if (firstUploadingFile == null) {
            recordList = mapper.select(s -> s.
                where(BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(groupName)).
                and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(streamName)).
                and(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                    SqlBuilder.isEqualTo(BinlogUploadStatusEnum.SUCCESS.getValue())).
                and(BinlogOssRecordDynamicSqlSupport.purgeStatus,
                    SqlBuilder.isEqualTo(BinlogPurgeStatusEnum.UN_COMPLETE.getValue())).
                orderBy(BinlogOssRecordDynamicSqlSupport.binlogFile.descending()).
                limit(n));
        } else {
            recordList = mapper.select(s -> s.
                where(BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(groupName)).
                and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(streamName)).
                and(BinlogOssRecordDynamicSqlSupport.purgeStatus,
                    SqlBuilder.isEqualTo(BinlogPurgeStatusEnum.UN_COMPLETE.getValue())).
                and(BinlogOssRecordDynamicSqlSupport.binlogFile, SqlBuilder.isLessThanOrEqualTo(firstUploadingFile)).
                orderBy(BinlogOssRecordDynamicSqlSupport.binlogFile.descending()).
                limit(n + 1));
        }
        recordList.forEach(r -> res.add(r.getBinlogFile()));
        return res;
    }

    /**
     * 获得第一个正在上传的binlog文件的文件名
     * 如果没有正在上传的文件，返回null
     */
    private String getFirstUploadingFile() {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> recordList = mapper.select(s -> s.
            where(BinlogOssRecordDynamicSqlSupport.groupId, SqlBuilder.isEqualTo(groupName)).
            and(BinlogOssRecordDynamicSqlSupport.streamId, SqlBuilder.isEqualTo(streamName)).
            and(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                SqlBuilder.isEqualTo(BinlogUploadStatusEnum.UPLOADING.getValue())).
            orderBy(BinlogOssRecordDynamicSqlSupport.binlogFile).
            limit(1));
        return recordList.isEmpty() ? null : recordList.get(0).getBinlogFile();
    }
}
