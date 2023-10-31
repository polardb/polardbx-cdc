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

@Mapper
public interface DdlEngineArchiveMapper {
    @Select(
        "select abs(cast(gmt_modified as signed) - cast(gmt_created as signed)) as cost  from ddl_engine_archive where job_id = #{jobId}")
    Long selectArchiveDdlCost(@Param("jobId") long jobId);

    @Select(
        "select abs(cast(gmt_modified as signed) - cast(gmt_created as signed)) as cost  from ddl_engine where job_id = #{jobId}")
    Long selectDdlCost(@Param("jobId") long jobId);
}
