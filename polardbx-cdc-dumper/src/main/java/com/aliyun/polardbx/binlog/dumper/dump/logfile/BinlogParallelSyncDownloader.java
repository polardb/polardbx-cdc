/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Binlog downloader for {@link BinlogParallelSyncReader}
 */
@Slf4j
public class BinlogParallelSyncDownloader extends BinlogDumpDownloader {
    private final int parallelism;
    /**
     * Actual start file seq for this downloader.
     * Only download:
     * binlog.{initialStartFileSeq}
     * binlog.{initialStartFileSeq + parallelism}
     * binlog.{initialStartFileSeq + 2 * parallelism}
     * ...
     */
    private final int initialStartFileSeq;

    public BinlogParallelSyncDownloader(LogFileManager logFileManager, String downloadPath, int windowSize,
                                        String startFile, long masterHeartbeatPeriod,
                                        ServerCallStreamObserver<DumpStream> observer,
                                        BinlogDumpReader dumpReader, int parallelismPerFile, long partSize,
                                        int parallelism, String trace) {
        super(logFileManager, downloadPath, windowSize, startFile, masterHeartbeatPeriod, observer, dumpReader,
            parallelismPerFile, partSize, trace);
        this.parallelism = parallelism;
        this.initialStartFileSeq = BinlogFileUtil.getBinlogSequence(startFile);
    }

    @Override
    protected void getDownloadFileList(String currentStartFile) {
        List<String> localFiles = logFileManager.getAllLocalBinlogFileNamesOrdered();
        BinlogOssRecordMapperExtend mapperExtend = SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class);
        int startFileSequence = BinlogFileUtil.getBinlogSequence(currentStartFile);
        if (!isValidSeq(startFileSequence)) {
            throw new RuntimeException("Get unexpected download file request, currentStartFile is " + currentStartFile
                + ", current seq is " + startFileSequence + ", initialStartFileSeq is " + initialStartFileSeq
                + ", parallelism is " + parallelism);
        }
        List<BinlogOssRecord> records =
            mapperExtend.getRecordsForBinlogDump(logFileManager.getGroupName(), logFileManager.getStreamName(),
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID), startFileSequence);
        List<Integer> filesToDownload =
            records.stream().map(BinlogOssRecord::getBinlogFile)
                .filter(f -> !localFiles.contains(f))
                .map(BinlogFileUtil::getBinlogSequence)
                .filter(this::isValidSeq)
                .collect(Collectors.toList());

        downloadQueue.clear();
        downloadedSet.clear();
        log.info("[{}] download file list: {}, parallelism: {}, init seq: {}", clientTraceMark, filesToDownload,
            parallelism,
            initialStartFileSeq);
        downloadQueue.addAll(filesToDownload);
    }

    private boolean isValidSeq(int seq) {
        return (seq - initialStartFileSeq) % parallelism == 0;
    }

    @Override
    protected String getNextBinlogFileName(String fileName) {
        return BinlogFileUtil.getNextBinlogFileName(fileName, parallelism);
    }

    public static BinlogDumpDownloader buildDownloader(BinlogDumpDownloader binlogDumpDownloader,
                                                       String startFile, String trace) {
        if (!(binlogDumpDownloader instanceof BinlogParallelSyncDownloader)) {
            throw new IllegalArgumentException("binlogDumpDownloader must be instanceof BinlogParallelSyncDownloader");
        }
        return new BinlogParallelSyncDownloader(binlogDumpDownloader.logFileManager, binlogDumpDownloader.downloadPath,
            binlogDumpDownloader.windowSize, startFile, binlogDumpDownloader.masterHeartbeatPeriod,
            binlogDumpDownloader.observer, binlogDumpDownloader.dumpReader,
            binlogDumpDownloader.parallelismPerFile, binlogDumpDownloader.partSize,
            ((BinlogParallelSyncDownloader) binlogDumpDownloader).parallelism, trace);
    }
}
