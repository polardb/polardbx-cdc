/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
