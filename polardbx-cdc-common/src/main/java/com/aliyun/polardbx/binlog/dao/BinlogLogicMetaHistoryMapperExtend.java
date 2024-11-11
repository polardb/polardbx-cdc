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
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BinlogLogicMetaHistoryMapperExtend {
    @Update("update binlog_logic_meta_history set `delete` = 1 where tso < #{tso}")
    public int softClean(@Param("tso") String tso);

    @Select("select tso from binlog_logic_meta_history where `type` = 1 order by tso desc limit 1")
    String getLatestSnapshotTso();

    @Select("select tso from binlog_logic_meta_history where `type` = 1 order by tso desc limit 2")
    List<String> getLatest2SnapshotTso();
}
