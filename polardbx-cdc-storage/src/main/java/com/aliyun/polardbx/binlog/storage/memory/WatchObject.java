/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage.memory;

import org.apache.commons.lang.text.StrBuilder;

import java.io.PrintWriter;

public class WatchObject {

    private final long createTime;
    private String trace;

    public WatchObject() {
        this.createTime = System.currentTimeMillis();
        buildStackTrace();
    }

    private void buildStackTrace() {
        RuntimeException re = new RuntimeException();
        StrBuilder strBuilder = new StrBuilder();
        re.printStackTrace(new PrintWriter(strBuilder.asWriter()));
        this.trace = strBuilder.toString();
    }

    public long createTime() {
        return createTime;
    }

    public String trace() {
        return trace;
    }

}
