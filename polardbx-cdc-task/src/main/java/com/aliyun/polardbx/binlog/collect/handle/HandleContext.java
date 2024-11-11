/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.handle;

import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.merge.HeartBeatWindow;

/**
 * created by ziyang.lb
 **/
public class HandleContext {

    private volatile CollectException exception;
    private volatile HeartBeatWindow currentHeartBeatWindow;

    public CollectException getException() {
        return exception;
    }

    public void setException(CollectException exception) {
        this.exception = exception;
    }

    public HeartBeatWindow getCurrentHeartBeatWindow() {
        return currentHeartBeatWindow;
    }

    public void setCurrentHeartBeatWindow(HeartBeatWindow currentHeartBeatWindow) {
        this.currentHeartBeatWindow = currentHeartBeatWindow;
    }
}
