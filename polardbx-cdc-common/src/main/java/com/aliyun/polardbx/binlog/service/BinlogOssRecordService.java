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

import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.clusterId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.groupId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.lastTso;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.logBegin;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.logEnd;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.streamId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.uploadStatus;
import static com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus.COMPLETE;
import static com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus.UN_COMPLETE;
import static com.aliyun.polardbx.binlog.enums.BinlogUploadStatus.CREATE;
import static com.aliyun.polardbx.binlog.enums.BinlogUploadStatus.IGNORE;
import static com.aliyun.polardbx.binlog.enums.BinlogUploadStatus.SUCCESS;
import static com.aliyun.polardbx.binlog.enums.BinlogUploadStatus.UPLOADING;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThan;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotNull;
import static org.mybatis.dynamic.sql.SqlBuilder.isNull;

/**
 * @author ziyang.lb, yudong
 **/
@Service
@Slf4j
public class BinlogOssRecordService {

    @Resource
    private BinlogOssRecordMapper mapper;

    /**
     * 已经从远端存储上删除的，获得编号最大的binlog record
     */
    public Optional<BinlogOssRecord> getMaxPurgedRecord(String gid, String sid, String cid) {
        return mapper.selectOne(s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid))
            .and(clusterId, isEqualTo(cid)).and(purgeStatus, isEqualTo(COMPLETE.getValue()))
            .orderBy(binlogFile.descending()).limit(1));
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
     * 同时，需要保证选出来的recover tso要大于或等于最近一次扩缩容对应的tso，以免在缩容场景下选出来的recover tso对应的DN列表中的某些DN已经被删掉了
     */
    public Optional<BinlogOssRecord> getRecordForRecovery(String gid, String sid, String cid, int hourLimit,
                                                          int numLimit, String expectedStorageTso) {
        long startTimestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hourLimit);
        List<BinlogOssRecord> records = mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(gmtModified, isGreaterThanOrEqualTo(new Date(startTimestamp))).orderBy(binlogFile));
        if (records.isEmpty() || records.get(0).getLastTso() == null) {
            log.info("records is empty or the first record's last_tso is null");
            return Optional.empty();
        }

        // 当选出来的这一批记录中，有某个记录的last tso字段为空，称之为空洞
        // 如果存在空洞，则需要返回编号最小的空洞的上一个记录
        // 最后一个记录是当前正在生成的最新binlog对应的记录，极大概率last_tso为空
        // 存在空洞的情况下，一定不会是发生过扩缩容，因为扩缩容会等待所有文件上传完成
        for (int i = 1; i < records.size() - 1; i++) {
            BinlogOssRecord curr = records.get(i);
            if (curr.getLastTso() == null) {
                log.info("meet a hole at {}, try to return the previous record", curr.getBinlogFile());
                BinlogOssRecord prev = records.get(i - 1);
                if (prev.getLastTso() == null) {
                    return Optional.empty();
                } else {
                    return Optional.of(prev);
                }
            }
        }

        // 保证 recover tso >= expected storage tso
        Collections.reverse(records);
        int fromIndex = records.get(0).getLastTso() == null ? 1 : 0;
        int toIndex = Math.min(records.size(), fromIndex + numLimit);
        List<BinlogOssRecord> recordList = records.subList(fromIndex, toIndex);
        log.info("after processing record list:{}", recordList);
        for (int i = 0; i < recordList.size(); i++) {
            if (recordList.get(i).getLastTso().compareTo(expectedStorageTso) < 0) {
                log.info("meet a record whose last_tso is less than expected storage tso, record:{}",
                    recordList.get(i));
                return i > 0 ? Optional.of(recordList.get(i - 1)) : Optional.empty();
            }
        }

        return Optional.of(recordList.get(recordList.size() - 1));
    }

    /**
     * 获得last_tso字段非空且文件编号最大的一个record
     */
    public Optional<BinlogOssRecord> getLastTsoRecord(String gid, String sid, String cid) {
        return mapper.selectOne(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(lastTso, isNotNull()).and(uploadStatus, isIn(SUCCESS.getValue(), IGNORE.getValue()))
                .orderBy(binlogFile.descending()).limit(1));
    }

    public Optional<BinlogOssRecord> getRecordByTso(String gid, String sid, String cid, String tso) {
        return mapper.selectOne(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(lastTso, isEqualTo(tso)));
    }

    /**
     * 获取需要上传到远端存储的文件对应的记录
     */
    public List<BinlogOssRecord> getRecordsForUpload(String gid, String sid, String cid) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(uploadStatus, isIn(CREATE.getValue(), UPLOADING.getValue())));
    }

    /**
     * 给定过期时间，返回需要清理的记录
     * 清理操作是指：将远端存储上的文件删除，并将记录的purge_status设置为COMPLETE
     * 如果没有开启远端存储，仅仅是将记录的purge_status设置为COMPLETE
     */
    public List<BinlogOssRecord> getRecordsForPurge(String gid, String sid, String cid, Date expireTime) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(uploadStatus, isIn(SUCCESS.getValue(), IGNORE.getValue())).and(gmtModified, isLessThan(expireTime))
                .and(purgeStatus, isNotEqualTo(COMPLETE.getValue())));
    }

    /**
     * 获得还保存在远端存储上的binlog文件对应的记录
     */
    public List<BinlogOssRecord> getRecordsOfExistingFiles(String gid, String sid, String cid) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(uploadStatus, isEqualTo(SUCCESS.getValue())).and(purgeStatus, isEqualTo(UN_COMPLETE.getValue())));
    }

    public Optional<BinlogOssRecord> getRecordByName(String gid, String sid, String cid, String name) {
        return mapper.selectOne(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(binlogFile, isEqualTo(name)));
    }

    public List<BinlogOssRecord> getRecords(String gid, String sid, String cid) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid)));
    }

    public List<BinlogOssRecord> getRecordsInTimeRange(String gid, String sid, Date start, Date end) {
        List<BinlogOssRecord> unfinishedFiles = mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(logEnd, isNull())
                .and(logBegin, isLessThanOrEqualTo(end))
                .and(purgeStatus, isEqualTo(BinlogPurgeStatus.UN_COMPLETE.getValue())));
        List<BinlogOssRecord> finishedFiles = mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid))
                .and(logEnd, isGreaterThanOrEqualTo(start)).and(logBegin, isLessThanOrEqualTo(end))
                .and(purgeStatus, isEqualTo(BinlogPurgeStatus.UN_COMPLETE.getValue())));

        List<BinlogOssRecord> res = new ArrayList<>(finishedFiles);
        res.addAll(unfinishedFiles);
        return res;
    }

    public Optional<BinlogOssRecord> getRecordById(int rid) {
        return mapper.selectOne(s -> s.where(id, isEqualTo(rid)));
    }

    public Optional<BinlogOssRecord> getFirstUploadingRecord(String gid, String sid, String cid) {
        return mapper.selectOne(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(uploadStatus, isIn(CREATE.getValue(), UPLOADING.getValue())).orderBy(binlogFile).limit(1));
    }

    public List<BinlogOssRecord> getLastUploadSuccessRecords(String gid, String sid, String cid, int n) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(uploadStatus, isEqualTo(SUCCESS.getValue())).and(purgeStatus, isEqualTo(UN_COMPLETE.getValue()))
                .orderBy(binlogFile.descending()).limit(n));
    }

    public List<BinlogOssRecord> getRecordsBefore(String gid, String sid, String cid, String fileName, int n) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(binlogFile, isLessThanOrEqualTo(fileName)).and(uploadStatus, isEqualTo(SUCCESS.getValue()))
                .and(purgeStatus, isEqualTo(UN_COMPLETE.getValue())).orderBy(binlogFile.descending()).limit(n));
    }

    public List<BinlogOssRecord> getRecordsForBinlogDump(String gid, String sid, String cid, String fileName) {
        return mapper.select(
            s -> s.where(groupId, isEqualTo(gid)).and(streamId, isEqualTo(sid)).and(clusterId, isEqualTo(cid))
                .and(binlogFile, isGreaterThanOrEqualTo(fileName)).and(uploadStatus, isEqualTo(SUCCESS.getValue()))
                .and(purgeStatus, isEqualTo(UN_COMPLETE.getValue()))
                .orderBy(binlogFile));
    }

}
