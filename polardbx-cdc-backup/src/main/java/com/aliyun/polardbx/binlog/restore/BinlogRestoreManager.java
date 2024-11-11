/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

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
    private final String clusterId;
    private final String binlogFullPath;
    private final BinlogOssRecordService recordService;

    public BinlogRestoreManager(String groupName, String streamName, String rootPath) {
        this.groupName = groupName;
        this.streamName = streamName;
        this.clusterId = getString(ConfigKeys.CLUSTER_ID);
        this.binlogFullPath = BinlogFileUtil.getFullPath(rootPath, groupName, streamName);
        this.recordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
    }

    public void start() {
        log.info("binlog restore manager start to run");
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            int n = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LAST_FILE_COUNT);
            List<String> downloadFiles = getDownloadFiles(n);
            log.info("download file list:{}", downloadFiles);
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
        List<BinlogOssRecord> result;
        Optional<BinlogOssRecord> firstUploadingFile =
            recordService.getFirstUploadingRecord(groupName, streamName, clusterId);
        if (firstUploadingFile.isPresent()) {
            String fileName = firstUploadingFile.get().getBinlogFile();
            log.info("first uploading file exists, file name:{}", fileName);
            result = recordService.getRecordsBefore(groupName, streamName, clusterId, fileName, n + 1);
        } else {
            result = recordService.getLastUploadSuccessRecords(groupName, streamName, clusterId, n);
        }

        return result.stream().map(BinlogOssRecord::getBinlogFile).filter(f -> {
            File file = new File(binlogFullPath, f);
            return !file.exists();
        }).collect(Collectors.toList());
    }

}
