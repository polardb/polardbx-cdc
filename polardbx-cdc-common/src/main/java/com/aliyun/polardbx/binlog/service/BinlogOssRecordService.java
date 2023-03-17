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
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.groupId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.streamId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.uploadStatus;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThan;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotNull;

/**
 * created by ziyang.lb
 **/
@Service
@Slf4j
public class BinlogOssRecordService {

    @Resource
    private BinlogOssRecordMapper mapper;

    /**
     * 获取purgeStatus为Complete的，binlogFileName最大的记录
     */
    public Optional<BinlogOssRecord> getMaxPurgedRecord(String groupName, String streamName) {
        return mapper.selectOne(s -> s.where(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
            .and(purgeStatus, isEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue())).orderBy(binlogFile.descending())
            .limit(1));
    }

    /**
     * 从binlog数据表中选择某条record，以它的last_tso作为recover tso
     * 说明：
     * 1. 使用numLimit参数限制，避免恢复的binlog太多，影响SLA
     * 2. 使用hourLimit参数限制，避免恢复很久之前的binlog，没有意义
     * 选择算法：
     * 1. 如果指定时间范围内所有的binlog record的last_tso字段都为空，返回null，降级至latest_cursor模式
     * 2. 如果指定时间范围内存在某个或者某几个binlog record的last_tso字段为空，返回编号最小的空洞的上一个binlog record
     * 3. 如果指定时间范围内的binlog record的last_tso字段都非空，则使用numLimit进行限制，返回编号最大的倒数第numLimit个binlog record
     */
    public Optional<BinlogOssRecord> getRecordForRecovery(String groupName, String streamName, int hourLimit,
                                                          int numLimit) {
        long startTimestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hourLimit);
        List<BinlogOssRecord> records = mapper.select(
            s -> s.where(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
                .and(gmtModified, isGreaterThanOrEqualTo(new Date(startTimestamp))).orderBy(binlogFile));
        if (records.isEmpty() || records.get(0).getLastTso() == null) {
            log.info("records is empty or the first record's last_tso is null");
            return Optional.empty();
        }

        // records中最后一个记录是当前正在生成的最新binlog对应的记录，极大概率last_tso为空
        for (int i = 1; i < records.size() - 1; i++) {
            BinlogOssRecord curr = records.get(i);
            if (curr.getLastTso() == null) {
                log.info("meet a hole, will return record before this hole");
                return Optional.of(records.get(i - 1));
            }
        }

        if (records.size() <= numLimit) {
            return Optional.of(records.get(0));
        } else {
            return Optional.of(records.get(records.size() - numLimit));
        }
    }

    /**
     * 获得last_tso字段非空且文件编号最大的一个record
     */
    public Optional<BinlogOssRecord> getLatestRecordWithLastTsoNotNull(String groupName, String streamName) {
        return mapper.selectOne(s -> s.where(BinlogOssRecordDynamicSqlSupport.lastTso, isNotNull())
            .and(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
            .orderBy(binlogFile.descending()).limit(1));
    }

    public Optional<BinlogOssRecord> getRecordByLastTso(String groupName, String streamName, String lastTso) {
        return mapper.selectOne(s -> s.where(BinlogOssRecordDynamicSqlSupport.lastTso, SqlBuilder.isEqualTo(lastTso))
            .and(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName)));
    }

    /**
     * 获取需要上传到远端存储的文件对应的记录
     */
    public List<BinlogOssRecord> getRecordsForUpload(String groupName, String streamName) {
        return mapper.select(s -> s.where(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
            .and(uploadStatus,
                isIn(BinlogUploadStatusEnum.CREATE.getValue(), BinlogUploadStatusEnum.UPLOADING.getValue())));
    }

    /**
     * 给定过期时间，返回需要purge的记录
     * purge操作是指：将远端存储上的文件删除，并将记录的purge_status设置为COMPLETE
     */
    public List<BinlogOssRecord> getRecordsForPurge(String groupName, String streamName, Date expireTime) {
        return mapper.select(s -> s.where(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
            .and(uploadStatus,
                isIn(BinlogUploadStatusEnum.SUCCESS.getValue(), BinlogUploadStatusEnum.IGNORE.getValue()))
            .and(gmtModified, isLessThan(expireTime))
            .and(purgeStatus, isNotEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue())));
    }

    /**
     * 删除表中已被purge的、并且过期的记录
     */
    public void deletePurgedRecords(String groupName, String streamName) {
        Optional<BinlogOssRecord> maxPurgedRecord = getMaxPurgedRecord(groupName, streamName);
        maxPurgedRecord.ifPresent(record -> {
            int recordExpireDays = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_RECORD_EXPIRE_DAYS);
            long end = record.getGmtCreated().getTime() - TimeUnit.DAYS.toMillis(recordExpireDays);
            Date endDate = new Date(end);
            mapper.delete(s -> s.where(groupId, isEqualTo(groupName)).and(streamId, isEqualTo(streamName))
                .and(binlogFile, isLessThan(record.getBinlogFile()))
                .and(purgeStatus, isEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue()))
                .and(gmtCreated, isLessThan(endDate)));
        });
    }

}
