/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jiyue 2021/8/13 21:51
 */
@Data
public class DataImportMeta {
    private List<PhysicalMeta> metaList;
    private int mergeBatchSize = 500;
    private int sendBatchSize = 100;
    private int ringBufferSize = 2048;
    /* full */
    private int fetchBatchSize = RplConstants.DEFAULT_FETCH_BATCH_SIZE;
    private int producerParallelCount = RplConstants.PRODUCER_DEFAULT_PARALLEL_COUNT;
    private int consumerParallelCount = RplConstants.CONSUMER_DEFAULT_PARALLEL_COUNT;
    // inc
    private ApplierType applierType = ApplierType.MERGE;
    private boolean supportXa = false;

    private HostType backflowType;
    private String backflowHost;
    private Integer backflowPort;
    private String backflowUser;
    private String backflowPwd;
    private String backflowDbName;
    private String backflowDstDbName;
    private List<String> backflowTableList;
    private long backflowServerId;
    private long backflowIgnoreServerId;

    private String rules;
    private List<String> allTableList;

    private String cdcClusterId;

    @Data
    public static class PhysicalMeta {
        private HostType srcType;
        private String srcHost;
        private int srcPort;
        private String srcUser;
        private String srcPassword;
        private Set<String> srcDbList;

        private HostType dstType;
        private String dstHost;
        private int dstPort;
        private String dstDb;
        private String dstUser;
        private String dstPassword;
        private long dstServerId;

        private String ignoreServerIds;
        private List<String> logicalTableList;
        private Map<String, Set<String>> allowTableList;
        private Set<String> denyTableList;
        // src physical table -> src logical table
        private Map<String, String> rewriteTableMapping;

        private boolean skipException = false;
        private int fixedTpsLimit = -1;

        private String rdsUid;
        private String rdsBid;
        private String rdsInstanceId;
    }
}
