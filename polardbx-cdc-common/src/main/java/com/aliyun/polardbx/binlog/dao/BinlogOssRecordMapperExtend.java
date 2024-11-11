/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BinlogOssRecordMapperExtend {

    @Select("select stream_id as streamId ,MAX(last_tso) as lastTso from binlog_oss_record  group by stream_id")
    List<BinlogOssRecord> selectMaxTso();

    @Select(
        "select count(*) as TOTAL_COUNT from binlog_oss_record where log_begin >= #{begin} and log_end <= #{end} and group_id = #{groupId} ")
    Integer count(@Param("begin") String begin, @Param("end") String end, @Param("groupId") String groupId);

    @Select(
        "select * from binlog_oss_record where log_begin >= #{begin} and log_end <= #{end} and group_id = #{groupId} order by  id limit #{rb},#{re}")
    List<BinlogOssRecord> selectList(@Param("begin") String begin, @Param("end") String end,
                                     @Param("groupId") String groupId, @Param("rb") int rb, @Param("re") int re);
}
