/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NodeInfoMapperExt {

    @Update("update binlog_node_info set `last_tso_heartbeat` = current_timestamp(3) where container_id = #{instId}")
    int updateLastTsoHeartbeat(@Param("instId") String instId);

    @Select(
        "select  count(*) as c from binlog_node_info where cluster_role = 'MASTER' and last_tso_heartbeat >= DATE_SUB(NOW(), INTERVAL #{interval} MICROSECOND)")
    int checkHasAvailableTsoHeartbeat(@Param("interval") long interval);

    @Update(
        "update binlog_node_info set gmt_heartbeat = now(), role = #{role} , cluster_type = #{clusterType} , cluster_role=#{clusterRole}, enable_light_rebalance=#{enableLightRebalance} where id = #{id}")
    int updateNodeHeartbeat(@Param("id") Long id,
                            @Param("role") String role,
                            @Param("clusterType") String clusterType,
                            @Param("clusterRole") String clusterRole,
                            @Param("enableLightRebalance") boolean enableLightRebalance);

    @Select(
        "select * from binlog_node_info where cluster_id = #{clusterId} and status = 0 and (timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 <= #{heartbeatTimeoutMs} or container_id = #{currentContainerId}) order by id"
    )
    List<NodeInfo> getAliveNodes(@Param("clusterId") String clusterId,
                                 @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs,
                                 @Param("currentContainerId") String currentContainerId);

    @Select(
        "select * from binlog_node_info where cluster_id = #{clusterId} and status = 0 and (timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 > #{heartbeatTimeoutMs} and container_id <> #{currentContainerId}) order by id"
    )
    List<NodeInfo> getDeadNodes(@Param("clusterId") String clusterId,
                                @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs,
                                @Param("currentContainerId") String currentContainerId);

    /**
     * 探活的时间比较全部在DB侧完成，不能把JVM侧的时间当做参数传入，
     * 否则CDC与MetaDB时区不一致时，时间字面量会按JVM时区渲染，导致查询结果错误（如恒为空）
     */
    @Select(
        "select * from binlog_node_info where cluster_type = #{clusterType} and timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 <= #{heartbeatTimeoutMs} order by id"
    )
    List<NodeInfo> getAliveNodesByClusterType(@Param("clusterType") String clusterType,
                                              @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);
}
