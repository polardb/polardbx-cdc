/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.rpl.applier.BaseApplier;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import lombok.Data;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author shicai.xsc 2020/11/30 15:00
 * @since 5.0.0.0
 */
@Data
public abstract class BasePipeline {

    protected BaseExtractor extractor;
    protected BaseApplier applier;
    protected PipelineConfig pipeLineConfig;
    protected final AtomicBoolean running = new AtomicBoolean(false);

    public abstract void init() throws Exception;

    public abstract void start() throws Exception;

    public abstract void stop();

    public abstract boolean checkDone();

    public abstract void writeRingbuffer(List<MessageEvent> events);

    public abstract void directApply(List<DBMSEvent> messages) throws Exception;
}
