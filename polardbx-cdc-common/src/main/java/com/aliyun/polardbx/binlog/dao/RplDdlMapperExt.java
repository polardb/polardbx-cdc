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

import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RplDdlMapperExt {

    @Select("select schema_name,parallel_seq,max(ddl_tso) as ddl_tso "
        + "from rpl_ddl_main where fsm_id = #{fsmId} and task_id = #{taskId} group by schema_name,parallel_seq")
    List<RplDdl> getCheckpointTsoListForDdlMain(@Param("fsmId") long fsmId, @Param("taskId") long taskId);

    @Select("select schema_name,parallel_seq,max(ddl_tso) as ddl_tso "
        + "from rpl_ddl_sub where fsm_id = #{fsmId} and task_id = #{taskId} group by schema_name,parallel_seq")
    List<RplDdlSub> getCheckpointTsoListForDdlSub(@Param("fsmId") long fsmId, @Param("taskId") long taskId);
}
