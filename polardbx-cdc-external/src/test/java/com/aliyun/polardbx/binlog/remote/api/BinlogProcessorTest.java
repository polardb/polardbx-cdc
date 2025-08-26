/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api;

import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BinlogProcessorTest {

    public static BinlogFile createBinlogFile(String logname, long serverId, long instanceId, long begin, long end) {
        BinlogFile binlogFile = Mockito.mock(BinlogFile.class);
        Mockito.when(binlogFile.getServerId()).thenReturn(serverId);
        Mockito.when(binlogFile.getLogname()).thenReturn(logname);
        Mockito.when(binlogFile.getInstanceID()).thenReturn(instanceId);
        Mockito.when(binlogFile.getBeginTime()).thenReturn(begin);
        Mockito.when(binlogFile.getEndTime()).thenReturn(end);
        try {
            Mockito.doNothing().when(binlogFile).initRegionTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return binlogFile;
    }

    @Test
    public void testProcessorHasMasterServerId() {
        List<BinlogFile> rawBinlogFileList = new ArrayList<>();
        rawBinlogFileList.add(createBinlogFile("b.2", 1, 1, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.1", 1, 1, 2L, 3L));
        rawBinlogFileList.add(createBinlogFile("b.3", 2, 2, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.4", 2, 2, 2L, 3L));
        Set<Long> ignoreHostSet = new HashSet<>();
        Long preferHostId = null;
        Long startTime = null;
        Long serverId = 1L;
        List<BinlogFile> binlogFiles =
            BinlogProcessor.process(rawBinlogFileList, ignoreHostSet, preferHostId, startTime, serverId);
        Assert.assertEquals(2, binlogFiles.size());
        Assert.assertEquals("b.1", binlogFiles.get(0).getLogname());
        Assert.assertEquals("b.2", binlogFiles.get(1).getLogname());
    }

    @Test
    public void testProcessorIgnoreHost() {
        List<BinlogFile> rawBinlogFileList = new ArrayList<>();
        rawBinlogFileList.add(createBinlogFile("b.2", 1, 1, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.1", 1, 1, 2L, 3L));
        rawBinlogFileList.add(createBinlogFile("b.3", 2, 2, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.4", 2, 2, 2L, 3L));
        Set<Long> ignoreHostSet = new HashSet<>();
        ignoreHostSet.add(1L);
        Long preferHostId = null;
        Long startTime = null;
        Long serverId = 1L;
        List<BinlogFile> binlogFiles =
            BinlogProcessor.process(rawBinlogFileList, ignoreHostSet, preferHostId, startTime, serverId);
        Assert.assertEquals(2, binlogFiles.size());
        Assert.assertEquals("b.3", binlogFiles.get(0).getLogname());
        Assert.assertEquals("b.4", binlogFiles.get(1).getLogname());
    }

    @Test
    public void testProcessorBeginRegion() {
        List<BinlogFile> rawBinlogFileList = new ArrayList<>();
        rawBinlogFileList.add(createBinlogFile("b.3", 2, 2, 2L, 3L));
        rawBinlogFileList.add(createBinlogFile("b.4", 2, 2, 3L, 4L));
        rawBinlogFileList.add(createBinlogFile("b.2", 1, 1, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.1", 1, 1, 2L, 4L));
        Set<Long> ignoreHostSet = new HashSet<>();
        Long preferHostId = null;
        Long startTime = 1L;
        Long serverId = 3L;
        List<BinlogFile> binlogFiles =
            BinlogProcessor.process(rawBinlogFileList, ignoreHostSet, preferHostId, startTime, serverId);
        Assert.assertEquals(2, binlogFiles.size());
        Assert.assertEquals("b.1", binlogFiles.get(0).getLogname());
        Assert.assertEquals("b.2", binlogFiles.get(1).getLogname());
    }

    @Test
    public void testProcessorMaxTimeRegion() {
        List<BinlogFile> rawBinlogFileList = new ArrayList<>();
        rawBinlogFileList.add(createBinlogFile("b.1", 2, 2, 1L, 3L));
        rawBinlogFileList.add(createBinlogFile("b.2", 2, 2, 3L, 4L));
        rawBinlogFileList.add(createBinlogFile("b.3", 1, 1, 1L, 2L));
        rawBinlogFileList.add(createBinlogFile("b.4", 1, 1, 2L, 5L));
        Set<Long> ignoreHostSet = new HashSet<>();
        Long preferHostId = null;
        Long startTime = 1L;
        Long serverId = 3L;
        List<BinlogFile> binlogFiles =
            BinlogProcessor.process(rawBinlogFileList, ignoreHostSet, preferHostId, startTime, serverId);
        Assert.assertEquals(2, binlogFiles.size());
        Assert.assertEquals("b.3", binlogFiles.get(0).getLogname());
        Assert.assertEquals("b.4", binlogFiles.get(1).getLogname());
    }
}
