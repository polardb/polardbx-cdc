/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.extractor.MultiStreamStartTsoWindow;
import com.aliyun.polardbx.binlog.metrics.ExtractorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RtRecordFilter implements LogEventFilter<LogEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RtRecordFilter.class);
    private ThreadRecorder recorder;
    private Thread c;
    private volatile boolean run;
    private boolean recordCommit = false;

    public RtRecordFilter() {
        this.recordCommit = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EVENT_COMMITLOG);
    }

    @Override
    public void handle(LogEvent event, HandlerContext context) throws Exception {
        recorder = context.getRuntimeContext().getThreadRecorder();
        recorder.setBinlogFile(context.getRuntimeContext().getBinlogFile());
        recorder.setLogPos(event.getLogPos());
        recorder.setWhen(event.getWhen());
        ExtractorMetrics.get().recordRt(recorder);
        if (c == null) {
            c = Thread.currentThread();
        }
        if (recordCommit) {
            logger.info(
                context.getRuntimeContext().getBinlogFile() + ":" + event.getLogPos() + ":" + event.getWhen() + ":"
                    + event.getHeader().getType());
        }
        recorder.doRecord(() -> context.doNext(event));
    }

    @Override
    public void onStart(HandlerContext context) {
        ExtractorMetrics.get().recordRt(context.getRuntimeContext().getThreadRecorder());
        run = true;
        while (run) {
            BinlogPosition position = context.getRuntimeContext().getStartPosition();
            boolean isReady = MultiStreamStartTsoWindow.getInstance().readyFoConsume(
                context.getRuntimeContext().getStorageInstId(), position.getRtso());
            if (isReady) {
                break;
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
            }
            if (logger.isDebugEnabled()) {
                logger.debug("wait for all stream ready!");
            }
        }
    }

    @Override
    public void onStop() {
        run = false;
        if (recorder != null) {
            ExtractorMetrics.get().removeRecord(recorder);
        }
        if (c != null) {
            c.interrupt();
            c = null;
        }
    }

    @Override
    public void onStartConsume(HandlerContext context) {

    }
}
