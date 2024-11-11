/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
