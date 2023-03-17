/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        Assert.assertTrue(ratio1 >= 0.9d);

        long ddlSuccessCount = addColumnSuccess.get() + dropColumnSuccess.get() + modifyColumnSuccess.get();
        long ddlFailCount = addColumnFail.get() + dropColumnFail.get() + modifyColumnFail.get();
        double ratio2 = ((double) ddlSuccessCount) / ((double) ddlSuccessCount + (double) ddlFailCount);
        Assert.assertTrue(ratio2 >= 0.9d);
    }
}
