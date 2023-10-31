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

import com.aliyun.polardbx.binlog.domain.po.BinlogLabEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BinlogLabEventMapper {

    @Insert(
        "insert into `binlog_lab_event`(`event_type`, `desc`, `params`) values(#{eventType}, #{desc}, #{params})")
    int insert(@Param("eventType") int eventType, @Param("desc") String desc,
               @Param("params") String params);

    @Select("select * from binlog_lab_event order by id")
    @ResultType(BinlogLabEvent.class)
    List<BinlogLabEvent> selectAll();

    @Select("select * from binlog_lab_event where event_type=#{actionType} order by id")
    @ResultType(BinlogLabEvent.class)
    List<BinlogLabEvent> selectByEventType(@Param("actionType") int actionType);

    @Select("select count(*) from binlog_lab_event where event_type = #{eventType} and params = #{params}")
    int countEventWithParams(@Param("eventType") int eventType, @Param("params") String params);

}
