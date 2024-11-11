/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.random;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;

import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
@Data
@Slf4j
public class Metrics {
    //dml
    private AtomicLong insertSingleSuccess = new AtomicLong();
    private AtomicLong insertSingleFail = new AtomicLong();
    private AtomicLong updateSingleSuccess = new AtomicLong();
    private AtomicLong updateSingleFail = new AtomicLong();
    private AtomicLong deleteSingleSuccess = new AtomicLong();
    private AtomicLong deleteSingleFail = new AtomicLong();
    private AtomicLong insertBatchSuccess = new AtomicLong();
    private AtomicLong insertBatchFail = new AtomicLong();
    private AtomicLong updateBatchSuccess = new AtomicLong();
    private AtomicLong updateBatchFail = new AtomicLong();
    private AtomicLong deleteBatchSuccess = new AtomicLong();
    private AtomicLong deleteBatchFail = new AtomicLong();

    //ddl
    private AtomicLong addColumnSuccess = new AtomicLong();
    private AtomicLong addColumnFail = new AtomicLong();
    private AtomicLong dropColumnSuccess = new AtomicLong();
    private AtomicLong dropColumnFail = new AtomicLong();
    private AtomicLong modifyColumnSuccess = new AtomicLong();
    private AtomicLong modifyColumnFail = new AtomicLong();

    private static Metrics INSTANCE = new Metrics();

    public static Metrics getInstance() {
        return INSTANCE;
    }

    private Metrics() {

    }

    void print() {
        log.info("=============================== Metrics Begin===============================");
        log.info(this.toString());
        log.info("=============================== Metrics End  ===============================");
    }

    void check() {
        long dmlSuccessCount = insertSingleSuccess.get() + updateSingleSuccess.get() + deleteSingleSuccess.get()
            + insertBatchSuccess.get() + updateBatchSuccess.get() + deleteBatchSuccess.get();
        long dmlFailCount = insertSingleFail.get() + updateSingleFail.get() + deleteSingleFail.get()
            + insertBatchFail.get() + updateBatchFail.get() + deleteBatchFail.get();
        double ratio1 = ((double) dmlSuccessCount) / ((double) dmlSuccessCount + (double) dmlFailCount);
        Assert.assertTrue("dml success ratio must greater than 0.9, actual is " + ratio1, ratio1 >= 0.8d);

        long ddlSuccessCount = addColumnSuccess.get() + dropColumnSuccess.get() + modifyColumnSuccess.get();
        long ddlFailCount = addColumnFail.get() + dropColumnFail.get() + modifyColumnFail.get();
        double ratio2 = ((double) ddlSuccessCount) / ((double) ddlSuccessCount + (double) ddlFailCount);
        Assert.assertTrue("ddl success ratio must greater than 0.8, actual is " + ratio2, ratio2 >= 0.8d);
    }
}
