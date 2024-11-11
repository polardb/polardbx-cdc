/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * @author yanfenglin
 */
@Mapper
public interface SystemConfigMapperExtend {

    @Select("checksum table binlog_system_config")
    Map<String, Object> checksumTable();
}
