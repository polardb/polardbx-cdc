/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final BinlogOssRecordMapperExtend mapperExtend;
    private final AtomicBoolean restoreFlag;

    public BinlogRestoreManager(String groupName, String streamName, String rootPath) {
        this.groupName = groupName;
        this.streamName = streamName;
        this.clusterId = getString(ConfigKeys.CLUSTER_ID);
        this.binlogFullPath = BinlogFileUtil.getFullPath(rootPath, groupName, streamName);
        this.recordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        this.mapperExtend = SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class);
        this.restoreFlag = new AtomicBoolean(false);
    }

    public void tryRestore() {
        if (restoreFlag.compareAndSet(false, true)) {
            try {
                restore();
            } catch (Throwable t) {
                restoreFlag.compareAndSet(true, false);
                log.error("binlog restore error", t);
                throw t;
            }
        }
    }

    public void restore() {
        log.info("## binlog restore manager start to run ...");
        if (RemoteBinlogProxy.getInstance().isBackupOn()) {
            int n = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LAST_FILE_COUNT);
            List<String> downloadFiles = getDownloadFiles(n);
            log.info("download file list:{}", downloadFiles);
            BinlogDownloader downloader = new BinlogDownloader(groupName, streamName, binlogFullPath, downloadFiles);
            downloader.start();
        }
        log.info("## binlog restore manager end to run ...");
    }

    /**
     * 获得需要从远端存储下载的文件列表
     * 1. 找文件编号最小的上传中的文件
     * 2. 如果找到，下载该文件以及该文件之前的n个文件，下载最近产生的这个不完整的文件的目的是为了seekLastTso更快（以及在只有一个上传中文件的情况下，该文件必须下载）
     * 3. 如果没有找到，则下载最近上传成功的n个文件
     *
     * @param n number of files
     * @return binlog file name list
     */
    public List<String> getDownloadFiles(int n) {
        List<BinlogOssRecord> result;
        Optional<BinlogOssRecord> firstUploadingFile =
            recordService.getFirstUploadingRecord(groupName, streamName, clusterId);
        if (firstUploadingFile.isPresent()) {
            String fileName = firstUploadingFile.get().getBinlogFile();
            log.info("first uploading file exists, file name:{}", fileName);
            int fileSequence = Integer.parseInt(fileName.substring(fileName.lastIndexOf(".") + 1));
            result = mapperExtend.getRecordsBefore(groupName, streamName, clusterId, fileSequence, n);
            result.add(0, firstUploadingFile.get());
        } else {
            result = mapperExtend.getLastUploadSuccessRecords(groupName, streamName, clusterId, n);
        }

        // 不用过滤本地存在的文件
        // 1. 如果文件是上一次不完整下载导致的，会在之后检测到LOCK锁后，被清空重新下载
        // 2. 如果文件是上一次完整下载导致的，之后不会触发Downloader的download行为
        return result.stream().map(BinlogOssRecord::getBinlogFile).collect(Collectors.toList());
    }

}
