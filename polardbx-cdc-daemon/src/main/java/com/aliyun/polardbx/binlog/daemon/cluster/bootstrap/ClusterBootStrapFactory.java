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
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.binlog.enums.ClusterType.BINLOG;
import static com.aliyun.polardbx.binlog.enums.ClusterType.BINLOG_X;
import static com.aliyun.polardbx.binlog.enums.ClusterType.FLASHBACK;
import static com.aliyun.polardbx.binlog.enums.ClusterType.IMPORT;
import static com.aliyun.polardbx.binlog.enums.ClusterType.REPLICA;
import static com.aliyun.polardbx.binlog.enums.ClusterType.COLUMNAR;

public class ClusterBootStrapFactory {
    private static final ConcurrentHashMap<ClusterType, ClusterBootstrapService> map = new ConcurrentHashMap<>();

    public static ClusterBootstrapService getBootstrapService(ClusterType clusterType) {
        switch (clusterType) {
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
        case COLUMNAR:
            map.computeIfAbsent(COLUMNAR, k -> new ColumnarBootstrapService());
            return map.get(COLUMNAR);
        default:
            throw new PolardbxException("not supported cluster type " + clusterType);
        }
    }
}
