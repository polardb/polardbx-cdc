/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_ALL_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_CHECK_INTERVAL_MILLS;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_MODE;

/**
 * create by ziyang.lb
 **/
@Slf4j
public class PersistAllChecker {
    private final int checkInterVal;
    private final PersistMode persistMode;
    private final boolean isSupportPersist;
    private long lastCheckTime;

    public PersistAllChecker() {
        checkInterVal = DynamicApplicationConfig.getInt(STORAGE_PERSIST_CHECK_INTERVAL_MILLS);
        persistMode = PersistMode.valueOf(DynamicApplicationConfig.getString(STORAGE_PERSIST_MODE));
        isSupportPersist = DynamicApplicationConfig.getBoolean(STORAGE_PERSIST_ENABLE);
    }

    public void checkWithCallback(boolean instantCheck, Supplier<String> supplier) {
        try {
            if (!isSupportPersist) {
                return;
            }

            long now = System.currentTimeMillis();
            if (instantCheck || now - lastCheckTime >= checkInterVal) {
                boolean randomFlag = randomFlag();
                boolean thresholdFlag = checkThreshold();
                if (randomFlag || thresholdFlag) {
                    String logMsg = supplier.get();
                    if (thresholdFlag) {
                        log.info(logMsg);
                    }
                }
                lastCheckTime = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            log.error("persistence checking failed", t);
        }
    }

    private boolean checkThreshold() {
        double threshold = DynamicApplicationConfig.getDouble(STORAGE_PERSIST_ALL_THRESHOLD);
        return JvmUtils.getOldUsedRatio() > threshold;
    }

    public boolean randomFlag() {
        boolean randomFlag = false;
        if (persistMode == PersistMode.RANDOM) {
            //实验室Random模式进行随机验证
            Random random = new Random();
            randomFlag = random.nextBoolean();
        }
        return randomFlag;
    }

    public boolean isSupportPersist() {
        return isSupportPersist;
    }

    public PersistMode getPersistMode() {
        return persistMode;
    }
}
