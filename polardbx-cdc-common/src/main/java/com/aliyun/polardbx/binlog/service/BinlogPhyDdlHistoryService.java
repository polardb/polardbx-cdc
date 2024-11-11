/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-16 14:58
 **/
@Service
@Slf4j
public class BinlogPhyDdlHistoryService {

    private static final String TABLE_NAME = "binlog_phy_ddl_history";

    @Resource
    private BinlogPhyDdlHistoryMapper mapper;

    @Resource(name = "metaJdbcTemplate")
    private JdbcTemplate metaJdbcTemplate;

    public List<BinlogPhyDdlHistory> getPhyDdlHistoryForRollback(String storageInstId, String snapshotTsoCondition,
                                                                 String rollbackTso, String clusterId, int pageSize) {
        return mapper.select(
            s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isGreaterThan(snapshotTsoCondition))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThanOrEqualTo(rollbackTso))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.tso).limit(pageSize)
        );
    }

    public void insetSelectiveIgnore(BinlogPhyDdlHistory phyDdlHistory, String logInfo) {
        try {
            mapper.insertSelective(phyDdlHistory);
        } catch (DuplicateKeyException e) {
            if (log.isDebugEnabled()) {
                log.debug(logInfo);
            }
        }
    }

    public String getMaxTso(String clusterId, String storageInstId) {
        String sql = String.format(
            "select max(tso) tso from %s where storage_inst_id = '%s' and cluster_id = '%s'",
            TABLE_NAME, storageInstId, clusterId);
        String result = metaJdbcTemplate.queryForObject(sql, String.class);
        return result == null ? "" : result;
    }
}
