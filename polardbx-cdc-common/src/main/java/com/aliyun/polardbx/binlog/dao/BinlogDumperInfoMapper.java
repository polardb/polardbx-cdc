/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author yudong
 * @since 2023/7/17 15:18
 **/
@Mapper
public interface BinlogDumperInfoMapper {
    @Select(
        "select max(timestampdiff(MICROSECOND, gmt_heartbeat, now()))/1000 from binlog_dumper_info where cluster_id = #{clusterId}")
    Long maxDumperHeartbeatDelay(@Param("clusterId") String clusterId);

    @Update(
        "update binlog_dumper_info set gmt_heartbeat = now() , role = #{role}, status = 0, sub_version = #{subVersion}, enable_light_rebalance = #{enableLightRebalance}  where cluster_id = #{clusterId} and task_name = #{taskName}")
    int updateDumperHeartbeatBasic(@Param("taskName") String taskName,
                                   @Param("role") String role,
                                   @Param("clusterId") String clusterId,
                                   @Param("subVersion") long subVersion,
                                   @Param("enableLightRebalance") boolean enableLightRebalance);

    @Update(
        "update binlog_dumper_info set gmt_heartbeat = now() , role = #{role}, status = 0, delay = #{delay}, sub_version = #{subVersion}, enable_light_rebalance =#{enableLightRebalance} where cluster_id = #{clusterId} and task_name = #{taskName}")
    int updateDumperHeartbeatWithDelay(@Param("taskName") String taskName,
                                       @Param("role") String role,
                                       @Param("clusterId") String clusterId,
                                       @Param("delay") long delay,
                                       @Param("subVersion") long subVersion,
                                       @Param("enableLightRebalance") boolean enableLightRebalance);

    @Select(
        "select timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 from binlog_dumper_info where cluster_id= #{clusterId} and task_name = #{taskName}")
    Long getHeartbeatInterval(@Param("taskName") String taskName, @Param("clusterId") String clusterId);

}
