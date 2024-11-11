/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.github.rholder.retry.RetryException;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface FlowLimiter {

    void runTask(List<DBMSEvent> events) throws ExecutionException, RetryException;

    void runTranTask(List<Transaction> transactions) throws ExecutionException, RetryException;

    void acquire();
}
