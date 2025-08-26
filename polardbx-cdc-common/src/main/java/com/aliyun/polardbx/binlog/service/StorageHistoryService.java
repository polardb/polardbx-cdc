/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Slf4j
public class StorageHistoryService {

    public static void saveStorageHistoryDetail(String tso, String streamName, String instructionId) {
        StorageHistoryDetailInfoMapper historyDetailMapper = getObject(StorageHistoryDetailInfoMapper.class);

        List<StorageHistoryDetailInfo> detailInfos = historyDetailMapper.select(
            s -> s.where(StorageHistoryDetailInfoDynamicSqlSupport.tso, isEqualTo(tso))
                .and(StorageHistoryDetailInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID)))
                .and(StorageHistoryDetailInfoDynamicSqlSupport.streamName, isEqualTo(streamName)));

        if (detailInfos.isEmpty()) {
            try {
                StorageHistoryDetailInfo detailInfo = new StorageHistoryDetailInfo();
                detailInfo.setStatus(-1);
                detailInfo.setClusterId(getString(CLUSTER_ID));
                detailInfo.setTso(tso);
                detailInfo.setStreamName(streamName);
                detailInfo.setInstructionId(instructionId);
                historyDetailMapper.insert(detailInfo);
            } catch (DuplicateKeyException e) {
                log.warn("storage history detail info is already existing, tso {}, stream name {}.", tso, streamName);
            }
        }
    }
}
