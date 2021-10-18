/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.extractor.log.VirtualTSO;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiStreamStartTsoWindow {

    private static final MultiStreamStartTsoWindow instance = new MultiStreamStartTsoWindow();
    private final ConcurrentHashMap<String, VirtualTSO> virtualTsoMap = new ConcurrentHashMap<>();
    private final VirtualTSO nullObject = new VirtualTSO(-1, -1, -1);
    private final AtomicInteger atomicInteger = new AtomicInteger();

    public static MultiStreamStartTsoWindow getInstance() {
        return instance;
    }

    public void addNewStream(String storageInstanceId) {
        synchronized (nullObject) {
            virtualTsoMap.put(storageInstanceId, nullObject);
            atomicInteger.incrementAndGet();
        }
    }

    public boolean readyFoConsume(String storageInstanceId, String tso) {
        synchronized (nullObject) {
            VirtualTSO virtualTSO = virtualTsoMap.get(storageInstanceId);
            if (virtualTSO == nullObject) {
                virtualTSO = new VirtualTSO(tso);
                virtualTsoMap.put(storageInstanceId, virtualTSO);
                atomicInteger.decrementAndGet();
            }
        }
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
}
