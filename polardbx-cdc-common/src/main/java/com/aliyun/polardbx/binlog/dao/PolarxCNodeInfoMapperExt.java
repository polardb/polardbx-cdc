/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PolarxCNodeInfoMapperExt {

    /**
     * 探活的时间比较全部在DB侧完成，不能把JVM侧的时间当做参数传入，
     * 否则CDC与MetaDB时区不一致时，时间字面量会按JVM时区渲染，导致查询结果错误（如恒为空）
     */
    @Select(
        "select * from node_info where timestampdiff(MICROSECOND, gmt_modified, now())/1000 <= #{heartbeatTimeoutMs} order by id"
    )
    List<PolarxCNodeInfo> getAliveNodes(@Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);
}
