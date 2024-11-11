/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.dao.RplSyncPointDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplSyncPointMapper;
import com.aliyun.polardbx.binlog.domain.po.RplSyncPoint;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author yudong
 * @since 2024/5/15 16:20
 **/
@Service
public class RplSyncPointService {
    @Resource
    private RplSyncPointMapper mapper;

    public void insert(String primaryTso, String secondaryTso) {
        Date expireTime = new Date(GmsTimeUtil.getCurrentTimeMillis() - TimeUnit.HOURS.toMillis(8));
        mapper.delete(r -> r.where(RplSyncPointDynamicSqlSupport.createTime, SqlBuilder.isLessThan(expireTime)));

        RplSyncPoint record = new RplSyncPoint();
        record.setPrimaryTso(primaryTso);
        record.setSecondaryTso(secondaryTso);
        mapper.insertSelective(record);
    }

    public Pair<String, String> selectLatestSyncPoint() {
        Optional<RplSyncPoint> syncPoint =
            mapper.selectOne(r -> r.orderBy(RplSyncPointDynamicSqlSupport.id.descending()).limit(1));
        return syncPoint.map(rplSyncPoint -> Pair.of(rplSyncPoint.getPrimaryTso(), rplSyncPoint.getSecondaryTso()))
            .orElse(null);
    }
}
