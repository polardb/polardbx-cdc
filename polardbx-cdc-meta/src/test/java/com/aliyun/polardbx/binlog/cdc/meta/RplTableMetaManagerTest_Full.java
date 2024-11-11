/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsData;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RplTableMetaManagerTest_Full extends BaseTestWithGmsData {

    @Test
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void testRollback() throws SQLException {
        BinlogLogicMetaHistoryMapper logicMapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);

        Optional<BinlogLogicMetaHistory> maxTso = logicMapper.selectOne(s ->
            s.orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso.descending()).limit(1));
        long count = logicMapper.count(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.type,
            isEqualTo((byte) 1)));
        log.info("logic snapshot count is " + count);

        RplTableMetaManager.DEFAULT_TABLE_NAME = "binlog_logic_meta_history";
        RplTableMetaManager tableMetaManager = new RplTableMetaManager(getGmsDataSource().getConnection());
        tableMetaManager.rollback(maxTso.get().getTso());
    }
}
