/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class RtRecordFilter implements LogEventFilter<LogEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RtRecordFilter.class);
    private ThreadRecorder recorder;
    private Thread c;
    private AtomicBoolean running;
    private final boolean recordCommit;

    public RtRecordFilter() {
        this.running = new AtomicBoolean(false);
        this.recordCommit = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_LOG_EVENT);
    }

    @Override
    public void handle(LogEvent event, HandlerContext context) throws Exception {
        recorder = context.getRuntimeContext().getThreadRecorder();
        recorder.setBinlogFile(context.getRuntimeContext().getBinlogFile());
        recorder.setLogPos(event.getLogPos());
        recorder.setWhen(event.getWhen());
        if (c == null) {
            c = Thread.currentThread();
        }
        if (recordCommit) {
            logger.info(context.getRuntimeContext().getBinlogFile() + ":" + event.getLogPos() + ":"
                + event.getWhen() + ":" + event.getHeader().getType());
        }
        recorder.doRecord(() -> context.doNext(event));
    }

    @Override
    public void onStart(HandlerContext context) {
        if (running.compareAndSet(false, true)) {
            ThreadRecorder.registerRecorder(context.getRuntimeContext().getThreadRecorder());
        }
    }

    @Override
    public void onStop() {
        if (running.compareAndSet(true, false)) {
            if (recorder != null) {
                ThreadRecorder.removeRecord(recorder);
            }
            if (c != null) {
                c.interrupt();
                c = null;
            }
        }
    }

    @Override
    public void onStartConsume(HandlerContext context) {

    }
}
