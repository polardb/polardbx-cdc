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

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author wenki
 */
@Mapper
public interface ColumnarInfoMapper {
    @Update(
        "update columnar_task set gmt_heartbeat = now() , role = #{role}, status = 0 where cluster_id = #{clusterId} and task_name = #{taskName}")
    int updateHeartbeat(@Param("taskName") String taskName, @Param("role") String role,
                        @Param("clusterId") String clusterId);

    @Select(
        "select timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 from columnar_task where cluster_id= #{clusterId} and task_name = #{taskName}")
    Long getHeartbeatInterval(@Param("clusterId") String taskName, @Param("taskName") String clusterId);

    @Select(
        "SELECT IFNULL((" +
            "    SELECT timestampdiff(MICROSECOND, update_time, now())/1000" +
            "    FROM columnar_checkpoints" +
            "    ORDER BY id DESC" +
            "    LIMIT 1" +
            "), 0)")
    Long getUpdateTimeInterval();

    @Select(
        "SELECT EXISTS(SELECT 1 FROM columnar_table_mapping)")
    boolean getColumnarIndexExist();

    @Select(
        "SELECT mem from columnar_node_info where container_id = #{containerId}")
    Long getContainerMemory(@Param("containerId") String containerId);
}
