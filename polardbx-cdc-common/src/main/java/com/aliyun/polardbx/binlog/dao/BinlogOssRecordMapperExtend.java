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
