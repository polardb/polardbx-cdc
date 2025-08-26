/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV2;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BinlogEventProcessorTest {
    @Test
    public void testRemoteBuildFile() throws IOException {
        BinlogEventProcessor processor = new BinlogEventProcessor();
        OssConnection connection = Mockito.mock(OssConnection.class);
        String binlogFile = "mysql-bin.001245";
        Mockito.when(connection.getLastConnectFile()).thenReturn(binlogFile);
        processor.init(connection, null, 0L, true, new ServerCharactorSet(), 1L, 0);
        Assert.assertEquals(binlogFile, processor.currentFileName());
    }

    @Test
    public void testStartWithSearchV2() throws Exception {
        BinlogEventProcessor processor = new BinlogEventProcessor();
        SearchTsoEventHandleV2 v2 = Mockito.mock(SearchTsoEventHandleV2.class);
        ErosaConnection connection = Mockito.mock(ErosaConnection.class);
        try (FileLogFetcher fetcher = new FileLogFetcher()) {
            fetcher.open(BinlogEventProcessorTest.class.getResource("/mysql_bin.19_1").getFile());
            Mockito.when(connection.providerFetcher(Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean()))
                .thenReturn(fetcher);

            Set<Integer> integerSet = new HashSet<>();
            for (int i = LogEvent.START_EVENT_V3; i < LogEvent.ENUM_END_EVENT; i++) {
                integerSet.add(i);
            }
            Mockito.when(v2.interestEvents()).thenReturn(integerSet);
            Mockito.doNothing().when(v2).onStart();
            processor.setHandle(v2);
            SearchRecorder recorder = Mockito.mock(SearchRecorder.class);
            Mockito.doNothing().when(recorder).setUnCompleteTran(Mockito.anyString());
            Mockito.doNothing().when(recorder).setPosition(Mockito.anyLong());
            Mockito.doNothing().when(recorder).setTimestamp(Mockito.anyLong());
            processor.setSearchRecorder(recorder);

            processor.init(connection, "mysql-bin.001245", 0L, true, new ServerCharactorSet(), 1L, 0);
            processor.start();

            Mockito.verify(recorder, Mockito.times(12)).setUnCompleteTran(Mockito.any());
        }
    }
}
