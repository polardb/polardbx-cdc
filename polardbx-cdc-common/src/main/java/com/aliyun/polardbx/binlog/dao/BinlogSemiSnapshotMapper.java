/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
