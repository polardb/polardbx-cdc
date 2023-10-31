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

import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author yudong
 * @since 2023/7/17 15:30
 **/
@Mapper
public interface BinlogSemiSnapshotMapper {
    @Select(
        "select * from binlog_semi_snapshot where storage_inst_id = #{storageInstId} and timestampdiff(HOUR, gmt_created, now()) >= #{preserveHour} order by tso desc limit 1"
    )
    List<SemiSnapshotInfo> getPreservedSnapshot(@Param("storageInstId") String storageInstId,
                                                @Param("preserveHour") int preserveHour);
}
