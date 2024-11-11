/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.extractor.log.VirtualTSO;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiStreamStartTsoWindow {

    private static final Logger logger = LoggerFactory.getLogger(MultiStreamStartTsoWindow.class);
    private static final MultiStreamStartTsoWindow instance = new MultiStreamStartTsoWindow();
    private final ConcurrentHashMap<String, VirtualTSO> virtualTsoMap = new ConcurrentHashMap<>();
    private final VirtualTSO nullObject = new VirtualTSO(-1, -1, -1);
    private final AtomicInteger atomicInteger = new AtomicInteger();

    public static MultiStreamStartTsoWindow getInstance() {
        return instance;
    }

    public void addNewStream(String storageInstanceId) {
        synchronized (nullObject) {
            // 首次添加，才会做记录，下面ready 算法相同， 理论上，一个dn，对应一个线程比较合理
            if (virtualTsoMap.put(storageInstanceId, nullObject) == null) {
                atomicInteger.incrementAndGet();
            } else {
                logger.warn("duplicate add storage to start window , storageInstanceId is : " + storageInstanceId);
            }
        }
    }

    public boolean readyFoConsume(String storageInstanceId, String tso) {
        synchronized (nullObject) {
            VirtualTSO virtualTSO = virtualTsoMap.get(storageInstanceId);
            if (virtualTSO == nullObject) {
                virtualTSO = new VirtualTSO(tso);
                virtualTsoMap.put(storageInstanceId, virtualTSO);
                atomicInteger.decrementAndGet();
            } else {
                logger.warn("duplicate ready for consume storage to start window , storageInstanceId is : "
                    + storageInstanceId);
            }
        }
        return atomicInteger.get() == 0;
    }

    public boolean isAllReady() {
        return atomicInteger.get() == 0;
    }

    public String getFilterTSO() {
        VirtualTSO maxTso = null;
        for (VirtualTSO virtualTSO : virtualTsoMap.values()) {
            if (maxTso == null) {
                maxTso = virtualTSO;
            } else {
                maxTso = maxTso.compareTo(virtualTSO) > 0 ? maxTso : virtualTSO;
            }
        }
        return CommonUtils.generateTSO(maxTso.tso, StringUtils.rightPad(maxTso.transactionId + "", 29, "0"), null);
    }

    public void clear() {
        synchronized (nullObject) {
            virtualTsoMap.clear();
            atomicInteger.set(0);
        }
    }
}
