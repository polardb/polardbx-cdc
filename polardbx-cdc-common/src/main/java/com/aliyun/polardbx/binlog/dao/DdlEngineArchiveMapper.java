/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DdlEngineArchiveMapper {
    @Select(
        "select abs(cast(gmt_modified as signed) - cast(gmt_created as signed)) as cost  from ddl_engine_archive where job_id = #{jobId}")
    Long selectArchiveDdlCost(@Param("jobId") long jobId);

    @Select(
        "select abs(cast(gmt_modified as signed) - cast(gmt_created as signed)) as cost  from ddl_engine where job_id = #{jobId}")
    Long selectDdlCost(@Param("jobId") long jobId);
}
