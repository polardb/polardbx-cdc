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

    public long getServerIdWithCompatibility() {
        return serverId == null ? ServerConfigUtil.getGlobalNumberVar(ServerConfigUtil.SERVER_ID) : serverId;
    }
}
