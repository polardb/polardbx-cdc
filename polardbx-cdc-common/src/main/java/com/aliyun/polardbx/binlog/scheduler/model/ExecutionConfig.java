/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ziyang
 */
@Data
public class ExecutionConfig {

    public static final String ORIGIN_TSO = "000000000000000000000000000000000000000000000000000000";
    public static final String MAX_TSO = "999999999999999999999999999999999999999999999999999999";
    public static final String ORIGIN_BINLOG_FILE = "ORIGIN_BINLOG_FILE";

    /**
     * source type @see com.aliyun.polardbx.binlog.domain.TaskType
     */
    private String type;
    /**
     * 上游sources
     */
    private List<String> sources;
    /**
     * 当前配置的对应的storage tso
     */
    private String tso;
    /**
     * 和xstream的映射关系
     */
    private Set<String> streamNameSet;
    /**
     * 运行时版本号
     */
    private long runtimeVersion;
    /**
     * recover tso for each stream
     */
    private Map<String, String> recoverTsoMap;
    /**
     * recover type
     */
    private String recoverType;
    /**
     * recover binlog file name for each stream
     */
    private Map<String, String> recoverFileNameMap;
    /**
     * force recover by recover tso
     */
    private boolean forceRecover;
    /**
     * force download from remote storage
     */
    private boolean forceDownload;
    /**
     * timestamp for this config
     */
    long timestamp;
    /**
     * 是否需要清空上一版本的binlog文件
     */
    boolean needCleanBinlogOfPreVersion = true;
    /**
     * 当前运行时使用的serverId
     */
    Long serverId;
    /**
     * 容器预留的可用内存大小(一般给RockDB和grpc预留)
     */
    int reservedMemMb;

    public long getServerIdWithCompatibility() {
        return serverId == null ? ServerConfigUtil.getGlobalNumberVar(ServerConfigUtil.SERVER_ID) : serverId;
    }
}
