/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParallelLogDecoder extends LogDecoder {
    private final int parallelism;
    private final String trace;

    public ParallelLogDecoder(int fromIndex, int toIndex, String trace, int parallelism) {
        super(fromIndex, toIndex);
        this.parallelism = parallelism;
        this.trace = trace;
    }

    /**
     * Decode correct binlog position when encounter rotate event.
     */
    @Override
    protected LogPosition rotateToNextBinlog(LogPosition logPosition, RotateLogEvent event) {
        String nextFilename = BinlogFileUtil.getNextBinlogFileName(logPosition.getFileName(), parallelism);
        if (log.isDebugEnabled()) {
            log.debug("[{}] decoder reset position to {}.4, actual event position is {}.{}", trace, nextFilename,
                event.getFilename(), event.getPosition());
        }
        return new LogPosition(nextFilename, 4);
    }

    @Override
    protected String getNextBinlogFileName(String fileName) {
        return BinlogFileUtil.getNextBinlogFileName(fileName, parallelism);
    }
}
