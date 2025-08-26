/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BinlogPhyDdlHistoryMapperExtend {

    @Update("delete from binlog_phy_ddl_history where tso < #{tso} limit #{limitSize}")
    int deleteByTsoWithLimit(@Param("tso") String tso, @Param("limitSize") int limitSize);
}
