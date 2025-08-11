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

@Mapper
public interface RplTaskConfigMapperExtend {

    @Select(
        "select memory from rpl_task_config where task_id = #{task_id} limit 1")
    Integer getMemory(@Param("task_id") long task_id);

}
