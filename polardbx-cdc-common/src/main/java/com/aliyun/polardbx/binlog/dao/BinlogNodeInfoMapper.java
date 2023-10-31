/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author yudong
 * @since 2023/7/17 15:17
 **/
@Mapper
public interface BinlogNodeInfoMapper {
    @Update(
        "update binlog_node_info set gmt_heartbeat = now(), role = #{role} , cluster_type = #{clusterType} , cluster_role=#{clusterRole} where id = #{id}")
    int updateNodeHeartbeat(@Param("id") Long id, @Param("role") String role,
                            @Param("clusterType") String clusterType,
                            @Param("clusterRole") String clusterRole);

    @Select(
        "select * from binlog_node_info where cluster_id = #{clusterId} and status = 0 and timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 <= #{heartbeatTimeoutMs} order by id"
    )
    List<NodeInfo> getAliveNodes(@Param("clusterId") String clusterId,
                                 @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);

    @Select(
        "select * from binlog_node_info where cluster_id = #{clusterId} and status = 0 and timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 > #{heartbeatTimeoutMs} order by id"
    )
    List<NodeInfo> getDeadNodes(@Param("clusterId") String clusterId,
                                @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);
}
