/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jiyue 2021/8/13 21:51
 */
@Data
public class DataImportMeta {

    /**
     * physical infos
     */
    private List<PhysicalMeta> metaList;

    private ValidationMeta validationMeta;

    /**
     * back flow info
     */
    private PhysicalMeta backFlowMeta;

    /**
     * for full data extraction
     */
    private int fetchBatchSize = RplConstants.DEFAULT_FETCH_BATCH_SIZE;

    private int producerParallelCount = RplConstants.PRODUCER_DEFAULT_PARALLEL_COUNT;

    private int consumerParallelCount = RplConstants.CONSUMER_DEFAULT_PARALLEL_COUNT;

    /**
     * for increment data extraction
     */
    private ApplierType applierType = ApplierType.MERGE;

    private boolean supportXa = false;

    private int incMergeBatchSize = 20;

    private int fullMergeBatchSize = 500;

    private int ringBufferSize = 4096;

    /**
     * mapping: src logical db -> src rules for this db
     */
    private Map<String, String> rules;

    /**
     * mapping: src logical db -> dst logical db
     */
    private Map<String, String> logicalDbMappings;

    /**
     * mapping: src logical db -> src logical table list
     */
    private Map<String, List<String>> srcLogicalTableList;

    /**
     * cluster id of running cluster
     */
    private String cdcClusterId;

    /**
     * physical info
     */
    @Data
    public static class PhysicalMeta {
        /**
         * src physical conn info
         */
        private HostType srcType;
        private String srcHost;
        private int srcPort;
        private String srcUser;
        private String srcPassword;

        /**
         * src physical db list
         */
        private Set<String> srcDbList;

        /**
         * dst logical conn info
         */
        private HostType dstType;
        private String dstHost;
        private int dstPort;
        private String dstUser;
        private String dstPassword;
        private long dstServerId;
        private String ignoreServerIds;

        /**
         * mapping: src physical db -> dst logical db
         */
        private Map<String, String> dstDbMapping;

        /**
         * mapping: src physical db -> src physical table list
         */
        private Map<String, Set<String>> physicalDoTableList;

        /**
         * mapping: src physical db -> (src physical table -> src logical table)
         */
        private Map<String, Map<String, String>> rewriteTableMapping;

        private boolean skipException = false;
        private int fixedTpsLimit = -1;

        /**
         * rds info for rdsApi
         */
        private String rdsUid;
        private String rdsBid;
        private String rdsInstanceId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConnInfo {
        private String host;
        private int port;
        private String user;
        private String password;
        private HostType type;
    }

    @Data
    public static class ValidationMeta {
        private ConnInfo srcLogicalConnInfo;
        private ConnInfo dstLogicalConnInfo;
        private long dstServerId;
        private String ignoreServerIds;
        private ValidationTypeEnum type;

        private Set<String> srcLogicalDbList;
        private Map<String, String> dbMapping;
        private Map<String, Set<String>> srcDbToTables;
    }
}
