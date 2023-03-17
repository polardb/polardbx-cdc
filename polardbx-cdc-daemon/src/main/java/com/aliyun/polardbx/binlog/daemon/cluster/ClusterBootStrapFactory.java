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
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.daemon.cluster.service.BinlogXBootStrapService;
import com.aliyun.polardbx.binlog.daemon.cluster.service.GlobalBinlogBootstrapService;
import com.aliyun.polardbx.binlog.daemon.cluster.service.ImportBootstrapService;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.binlog.ClusterTypeEnum.BINLOG;
import static com.aliyun.polardbx.binlog.ClusterTypeEnum.BINLOG_X;
import static com.aliyun.polardbx.binlog.ClusterTypeEnum.FLASHBACK;
import static com.aliyun.polardbx.binlog.ClusterTypeEnum.IMPORT;
import static com.aliyun.polardbx.binlog.ClusterTypeEnum.REPLICA;

public class ClusterBootStrapFactory {
    private static final ConcurrentHashMap<ClusterTypeEnum, ClusterBootstrapService> map = new ConcurrentHashMap<>();

    public static ClusterBootstrapService getBootstrapService(ClusterTypeEnum clusterTypeEnum) {
        switch (clusterTypeEnum) {
        case BINLOG:
            map.computeIfAbsent(BINLOG, k -> new GlobalBinlogBootstrapService());
            return map.get(BINLOG);
        case IMPORT:
            map.computeIfAbsent(IMPORT, k -> new ImportBootstrapService());
            return map.get(IMPORT);
        case FLASHBACK:
            map.computeIfAbsent(FLASHBACK, k -> new ImportBootstrapService());
            return map.get(FLASHBACK);
        case REPLICA:
            map.computeIfAbsent(REPLICA, k -> new ImportBootstrapService());
            return map.get(REPLICA);
        case BINLOG_X:
            map.computeIfAbsent(BINLOG_X, k -> new BinlogXBootStrapService());
            return map.get(BINLOG_X);
        default:
            throw new PolardbxException("not supported cluster type " + clusterTypeEnum);
        }
    }
}
