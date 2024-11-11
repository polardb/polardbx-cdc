/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.HEARTBEAT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TSO;

/**
 * created by ziyang.lb
 **/
@Data
@Slf4j
public class EventDataSinkHandler implements EventHandler<EventData>, LifecycleAware {

    private final HandleContext handleContext;
    private final boolean dryRun;
    private final int dryRunMode;

    public EventDataSinkHandler(HandleContext handleContext, boolean dryRun, int dryRunMode) {
        this.handleContext = handleContext;
        this.dryRun = dryRun;
        this.dryRunMode = dryRunMode;
    }

    @Override
    public void onEvent(EventData event, long sequence, boolean endOfBatch) {
        try {
            if (!handleContext.getRunning().get()) {
                throw new InterruptedException();
            }

            if (dryRun && dryRunMode == 2) {
                if (log.isDebugEnabled()) {
                    log.debug("work in dry run mode, sequence " + sequence);
                }
            } else {
                if (event.getEventToken() instanceof BatchEventToken) {
                    BatchEventToken batchEventToken = (BatchEventToken) event.getEventToken();
                    for (SingleEventToken t : batchEventToken.getTokens()) {
                        processSingleEventToken(event, t);
                    }
                } else if (event.getEventToken() instanceof SingleEventToken) {
                    processSingleEventToken(event, (SingleEventToken) event.getEventToken());
                } else {
                    throw new PolardbxException("unsupported event token : " +
                        event.getEventToken().getClass().getName());
                }
            }

            handleContext.setLatestSinkSequence(event.getEventToken().getSequence());
            event.clear();
        } catch (Throwable t) {
            PolardbxException exception = new PolardbxException("error occurred when do event sink.", t);
            handleContext.setException(exception);
            throw exception;
        }
    }

    private void processSingleEventToken(EventData event, SingleEventToken eventToken) throws IOException {
        try {
            if (eventToken.getType() == HEARTBEAT) {
                handleContext.getLogFileGenerator().tryFlush4ParallelWrite(eventToken.getNextPosition(),
                    eventToken.getTso(), eventToken.getTsoTimeSecond(), false);
            } else {
                BinlogFile binlogFile = handleContext.getLogFileGenerator().getBinlogFile();
                if (eventToken.isUseTokenData()) {
                    byte[] data = eventToken.getData();
                    binlogFile.writeEvent(data, 0, data.length, false, eventToken.getCheckServerId());
                } else {
                    binlogFile.writeEvent(event.getData(), eventToken.getOffset(), eventToken.getLength(), false,
                        eventToken.getCheckServerId());
                }
                afterWrite(eventToken);
            }
        } catch (Throwable t) {
            log.error("process singe event token error , token content is {}!", eventToken.toString(), t);
            throw t;
        }
    }

    private void afterWrite(SingleEventToken eventToken) throws IOException {
        SingleEventToken.Type type = eventToken.getType();
        if (type == TSO) {
            handleContext.getLogFileGenerator().tryFlush4ParallelWrite(eventToken.getNextPosition(),
                eventToken.getTso(), eventToken.getTsoTimeSecond(), eventToken.isForceFlush());
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onShutdown() {

    }
}
