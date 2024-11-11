/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;

/**
 * Created by ziyang.lb
 **/
public class StorageUtil {
    public static String getConfiguredExpectedStorageTso() {
        return SystemDbConfig.getSystemDbConfig(EXPECTED_STORAGE_TSO_KEY);
    }

    public static String buildExpectedStorageTso(String startTso) {
        return StringUtils.isBlank(startTso) ? ExecutionConfig.ORIGIN_TSO : buildInternal(startTso);
    }

    public static String buildExpectedStorageTso(String startTso, String streamName) {
        return StringUtils.isBlank(startTso) ? ExecutionConfig.ORIGIN_TSO : buildInternal(startTso, streamName);
    }

    private static String buildInternal(String startTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper
            .select(s -> s.where(StorageHistoryInfoDynamicSqlSupport.tso, isLessThanOrEqualTo(startTso))
                .and(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));
        return storageHistoryInfos.stream().map(StorageHistoryInfo::getTso).max(Comparator.comparing(s -> s)).get();
    }

    private static String buildInternal(String startTso, String streamName) {
        StorageHistoryDetailInfoMapper detailHistoryMapper =
            SpringContextHolder.getObject(StorageHistoryDetailInfoMapper.class);
        List<StorageHistoryDetailInfo> detailHistoryInfos = detailHistoryMapper
            .select(s -> s.where(StorageHistoryDetailInfoDynamicSqlSupport.tso, isLessThanOrEqualTo(startTso))
                .and(StorageHistoryDetailInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID)))
                .and(StorageHistoryDetailInfoDynamicSqlSupport.streamName, isEqualTo(streamName)));
        return detailHistoryInfos.stream().map(StorageHistoryDetailInfo::getTso)
            .max(Comparator.comparing(s -> s)).get();
    }
}
