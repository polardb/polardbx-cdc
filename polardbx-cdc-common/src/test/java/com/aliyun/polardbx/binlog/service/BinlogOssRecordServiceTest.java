/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BinlogOssRecordServiceTest extends BaseTestWithGmsTables {

    @Test
    public void getRecordsInTimeRange_WithFiles_ReturnsCorrectCount() {

        BinlogOssRecordService binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);

        String groupId = "group1";
        String streamId = "stream1";
        Date start = new Date(100L);
        Date end = new Date(200L);

        BinlogOssRecord unfinishedFile = new BinlogOssRecord();
        unfinishedFile.setGroupId(groupId);
        unfinishedFile.setStreamId(streamId);
        unfinishedFile.setBinlogFile("binlog_file1");
        unfinishedFile.setLogBegin(new Date(50L));
        unfinishedFile.setLogEnd(null);
        unfinishedFile.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(unfinishedFile);

        BinlogOssRecord finishedFile = new BinlogOssRecord();
        finishedFile.setGroupId(groupId);
        finishedFile.setStreamId(streamId);
        finishedFile.setBinlogFile("binlog_file2");
        finishedFile.setLogBegin(new Date(50L));
        finishedFile.setLogEnd(new Date(150L));
        finishedFile.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(finishedFile);

        List<BinlogOssRecord> result = binlogOssRecordService.getRecordsInTimeRange(groupId, streamId, start, end);

        assertEquals(2, result.size());
    }

    @Test
    public void getRecordsInTimeRange_WithPurgedFiles_ReturnsEmptyListo() {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecordService binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        String groupId = "group1";
        String streamId = "stream1";
        Date start = new Date(100L);
        Date end = new Date(200L);

        BinlogOssRecord unfinishedFile = new BinlogOssRecord();
        unfinishedFile.setGroupId(groupId);
        unfinishedFile.setStreamId(streamId);
        unfinishedFile.setBinlogFile("binlog_file3");
        unfinishedFile.setLogBegin(new Date(50L));
        unfinishedFile.setLogEnd(null);
        unfinishedFile.setPurgeStatus(BinlogPurgeStatus.COMPLETE.getValue());
        mapper.insertSelective(unfinishedFile);

        BinlogOssRecord finishedFile = new BinlogOssRecord();
        finishedFile.setGroupId(groupId);
        finishedFile.setStreamId(streamId);
        finishedFile.setBinlogFile("binlog_file4");
        finishedFile.setLogBegin(new Date(50L));
        finishedFile.setLogEnd(new Date(150L));
        finishedFile.setPurgeStatus(BinlogPurgeStatus.COMPLETE.getValue());
        mapper.insertSelective(finishedFile);

        List<BinlogOssRecord> result = binlogOssRecordService.getRecordsInTimeRange(groupId, streamId, start, end);

        assertEquals(0, result.size());
    }

    @Test
    public void getRecordsInTimeRange_NoFiles_ReturnsEmptyList() {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecordService binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        String groupId = "group1";
        String streamId = "stream1";
        Date start = new Date(100L);
        Date end = new Date(200L);

        BinlogOssRecord unfinishedFile = new BinlogOssRecord();
        unfinishedFile.setGroupId(groupId);
        unfinishedFile.setStreamId(streamId);
        unfinishedFile.setBinlogFile("binlog_file5");
        unfinishedFile.setLogBegin(new Date(250L));
        unfinishedFile.setLogEnd(null);
        unfinishedFile.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(unfinishedFile);

        BinlogOssRecord finishedFile = new BinlogOssRecord();
        finishedFile.setGroupId(groupId);
        finishedFile.setStreamId(streamId);
        finishedFile.setBinlogFile("binlog_file6");
        finishedFile.setLogBegin(new Date(10L));
        finishedFile.setLogEnd(new Date(90L));
        finishedFile.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(finishedFile);

        List<BinlogOssRecord> result = binlogOssRecordService.getRecordsInTimeRange(groupId, streamId, start, end);

        assertEquals(0, result.size());
    }
}
