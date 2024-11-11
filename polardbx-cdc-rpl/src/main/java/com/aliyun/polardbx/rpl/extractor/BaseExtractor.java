/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import lombok.Data;

/**
 * @author shicai.xsc 2021/2/18 21:41
 * @since 5.0.0.0
 */
@Data
public class BaseExtractor {

    protected BasePipeline pipeline;
    protected ExtractorConfig extractorConfig;
    protected boolean running;

    public BaseExtractor() {
    }

    public BaseExtractor(ExtractorConfig extractorConfig) {
        this.extractorConfig = extractorConfig;
    }

    public void init() throws Exception {
        pipeline = TaskContext.getInstance().getPipeline();
    }

    public void start() throws Exception {
    }

    public void stop() {

    }

    public boolean isDone() {
        return false;
    }

    public boolean isRunning() {
        return running;
    }

    public void tryPersist(MessageEvent messageEvent, MySQLDBMSEvent mySQLDBMSEvent) {
        if (mySQLDBMSEvent.isPersisted()) {
            messageEvent.persist();
        }
    }
}
