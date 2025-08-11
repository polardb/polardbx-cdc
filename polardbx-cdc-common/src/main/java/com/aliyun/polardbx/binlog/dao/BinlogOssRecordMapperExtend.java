/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
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

    @Select(
        "select * from binlog_oss_record where group_id = #{groupId} and stream_id = #{streamId} and cluster_id = #{clusterId} and upload_status = 2 and purge_status = 0 and CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) >= #{binlogFileSequence} order by CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED)"
    )
    List<BinlogOssRecord> getRecordsForBinlogDump(@Param("groupId") String groupId, @Param("streamId") String streamId,
                                                  @Param("clusterId") String clusterId,
                                                  @Param("binlogFileSequence") Integer binlogFileSequence);

    @Select(
        "select * from binlog_oss_record where group_id = #{groupId} and stream_id = #{streamId} and cluster_id = #{clusterId} and upload_status = 2 and purge_status = 0 and CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) <= #{binlogFileSequence} order by CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) DESC limit #{n}"
    )
    List<BinlogOssRecord> getRecordsBefore(@Param("groupId") String groupId, @Param("streamId") String streamId,
                                           @Param("clusterId") String clusterId,
                                           @Param("binlogFileSequence") Integer binlogFileSequence,
                                           @Param("n") Integer n);

    @Select(
        "select * from binlog_oss_record where group_id = #{groupId} and stream_id = #{streamId} and cluster_id = #{clusterId} and upload_status = 2 and purge_status = 0 order by CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) DESC limit #{n}"
    )
    List<BinlogOssRecord> getLastUploadSuccessRecords(@Param("groupId") String groupId,
                                                      @Param("streamId") String streamId,
                                                      @Param("clusterId") String clusterId,
                                                      @Param("n") Integer n);

    /**
     * 给定过期时间，返回需要清理的记录
     * 清理操作是指：将远端存储上的文件删除，并将记录的purge_status设置为COMPLETE
     * 如果没有开启远端存储，仅仅是将记录的purge_status设置为COMPLETE
     */
    @Select(
        "select * from binlog_oss_record where group_id = #{groupId} and stream_id = #{streamId} and cluster_id = #{clusterId} and purge_status = 0 and gmt_modified < #{gmtModified} order by CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED)"
    )
    List<BinlogOssRecord> getRecordsForPurge(@Param("groupId") String groupId,
                                             @Param("streamId") String streamId,
                                             @Param("clusterId") String clusterId,
                                             @Param("gmtModified") Date gmtModified);

    @Select(
        "select * from binlog_oss_record where group_id = #{groupId} and stream_id = #{streamId} and cluster_id = #{clusterId} and CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) <= #{endFileSequence} and CAST(SUBSTRING_INDEX(binlog_file, '.', -1) AS UNSIGNED) >= #{startFileSequence}"
    )
    List<BinlogOssRecord> getRecordsInFileRange(@Param("groupId") String groupId,
                                                @Param("streamId") String streamId,
                                                @Param("clusterId") String clusterId,
                                                @Param("startFileSequence") Integer startFileSequence,
                                                @Param("endFileSequence") Integer endFileSequence);
}
