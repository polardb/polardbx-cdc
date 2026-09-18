/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Given starting file binlog.k, and parallelism n,
 * This reader reads binlog.k, binlog.{k+n}, binlog.{k+2n}, ...
 * ...
 */
@Slf4j
public class BinlogParallelSyncReader extends BinlogSyncReader {
    private final int parallelism;
    private final int initialStartFileSeq;
    private final TxnOutputStream<DumpStream> outputStream;
    private final int id;

    public BinlogParallelSyncReader(LogFileManager logFileManager, String fileName, long pos,
                                    EventSplitMode eventSplitMode, int maxPacketSize,
                                    int readBufferSize, EnumBinlogChecksumAlg slaveChecksumAlg,
                                    int parallelism, int id, String trace,
                                    TxnOutputStream<DumpStream> outputStream)
        throws IOException {
        super(logFileManager, fileName, pos, eventSplitMode, maxPacketSize, readBufferSize, slaveChecksumAlg, trace);
        this.parallelism = parallelism;
        this.initialStartFileSeq = BinlogFileUtil.getBinlogSequence(fileName);
        this.outputStream = outputStream;
        this.id = id;
    }

    /**
     * @return true if next binlog events exist.
     */
    @Override
    public boolean hasNext() {
        BinlogCursor cursor = logFileManager.getLatestFileCursor();
        int diff = cursor.getFileSequence() - fileSequence;
        if (diff == 0) {
            return lastPosition < cursor.getFilePosition();
        } else if (diff >= parallelism) {
            if (diff == parallelism) {
                // The latest binlog is the next binlog. Ensure the next binlog is available.
                return cursor.getFilePosition() > 4;
            } else {
                // The latest binlog is newer than the next binlog.
                return true;
            }
        } else if (diff < 0) {
            // The requested binlog is not ready.
            return false;
        } else {
            // 0 <= diff < parallelism
            //
            // For example:     binlog.n    binlog.n+1    binlog.n+2
            //                     |            |              |
            // parallelism: 2   current       ready        not ready
            //
            // Return true if current binlog is not read to the end
            // 1. channel is null when this stream is under init.
            // OR 2. the current binlog file still has unsent events.
            try {
                return null == channel || lastPosition < channel.size();
            } catch (Exception e) {
                log.warn("[{}] call hasNext failed, caused by {}", clientTraceMark, e.getMessage());
                throw new PolardbxException(e);
            }
        }
    }

    @Override
    protected void rotate() throws Exception {
        this.close();
        String preFileName = fileName;
        this.fileName = BinlogFileUtil.getNextBinlogFileName(fileName, parallelism);
        logFileManager.getLogFileLockManager().readLock(fileName);
        rotateObservers.forEach(o -> o.onRotate(preFileName));
        this.fileSequence = logFileManager.parseFileNumber(fileName);
        this.startPosition = 4;
        log.info("[{}] try get {} from local in quick mode", clientTraceMark, fileName);
        getFile();
        this.channel = cdcFile.getReadChannel();
        log.info("[{}] rotate to next file {}", clientTraceMark, fileName);
        this.read();
    }

    @Override
    protected void initCdcFile() throws Exception {
        int totalWaitSecond = 0;
        int waitSecond = 1;
        while (!hasNext()) {
            try {
                Thread.sleep(waitSecond * 1000);
            } catch (InterruptedException e) {
                throw new PolardbxException("[" + clientTraceMark + "] Wait cdc file when init failed.", e);
            }
            totalWaitSecond += waitSecond;
            if (totalWaitSecond >= 10) {
                // Send heartbeat every 10 seconds to keep stream alive.
                outputStream.onNext(DumpStream.newBuilder()
                    .setPayload(heartbeatEventPacket())
                    .setIsHeartBeat(true)
                    .build());
            }
        }
        log.info("[{}] Parallel reader {} inits cdc file {}, start seq: {}", clientTraceMark, id, fileName,
            initialStartFileSeq);
        getFile();
    }

    @Override
    protected void initDumpDownloader(String trace) {
        log.info("init parallel dump downloader...");
        dumpDownloader = BinlogParallelSyncDownloader.buildDownloader(dumpDownloader, fileName, trace);
        dumpDownloader.init();
        this.registerRotateObserver(dumpDownloader);
    }
}
