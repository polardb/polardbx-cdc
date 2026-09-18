/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RelayLogEventCleanerTest extends BaseTest {

    private RelayLogEventTransmitter logEventTransmitter;
    private XStreamMapper xStreamMapper;
    private BinlogOssRecordMapper ossRecordMapper;
    private StoreEngine storeEngine1;
    private RelayLogEventCleaner cleaner;
    private Map<Integer, StoreEngine> storeEngines;
    private RelayFileCounter mockFileCounter;

    /**
     * 构建合法的38位TSO字符串，与生产格式一致
     */
    private static String buildTso(long physicalTimeMs) {
        long tsoTimestamp = physicalTimeMs << 22;
        return String.format("%019d%019d", tsoTimestamp, 0L);
    }

    @Before
    public void setUp() {
        logEventTransmitter = mock(RelayLogEventTransmitter.class);
        xStreamMapper = mock(XStreamMapper.class);
        ossRecordMapper = mock(BinlogOssRecordMapper.class);

        registerSpringObject("XStreamMapper", xStreamMapper);
        registerSpringObject(BinlogOssRecordMapper.class, ossRecordMapper);

        storeEngine1 = mock(StoreEngine.class);

        storeEngines = new HashMap<>();
        storeEngines.put(1, storeEngine1);

        mockFileCounter = mock(RelayFileCounter.class);
        when(mockFileCounter.getTotalRelayFileCount()).thenReturn(10);

        cleaner = new RelayLogEventCleaner(logEventTransmitter);
        cleaner.setStoreEngines(storeEngines);
    }

    @Test
    public void testDoClean_Rule1_CheckpointWithinLag() {
        // Rule 1: checkpoint在安全范围内，使用checkpoint作为cleanupTso
        mockConfig(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD, "100");
        mockConfig(BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO, "0.8");
        mockConfig(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES, "120");
        mockConfig(BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES, "1");

        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class);
            MockedStatic<RelayFileStoreEngine> mockFileStore = mockStatic(RelayFileStoreEngine.class)) {

            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));
            mockFileStore.when(RelayFileStoreEngine::getRelayFileCounter)
                .thenReturn(mockFileCounter);

            long now = System.currentTimeMillis();
            String maxReadTso = buildTso(now);
            // checkpoint 30分钟前，小于maxLag=120min → Rule 1
            String checkpointLastTso = buildTso(now - 30 * 60 * 1000L);

            when(storeEngine1.getMaxReadTso()).thenReturn(maxReadTso);
            BinlogOssRecord record = new BinlogOssRecord();
            record.setLastTso(checkpointLastTso);
            when(logEventTransmitter.getCheckpointTsoFromBackup("stream_1")).thenReturn(record);
            when(storeEngine1.getMaxCleanTso()).thenReturn("");

            cleaner.doClean();

            // Rule 1: cleanupTso = checkpointLastTso directly
            verify(storeEngine1).clean(checkpointLastTso);
        }
    }

    @Test
    public void testDoClean_Rule2_CheckpointLagTooLarge() {
        // Rule 2: checkpoint距离maxReadTso超过阈值(8h)，清理8小时前的数据
        mockConfig(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD, "100");
        mockConfig(BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO, "0.8");
        mockConfig(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES, "120");
        mockConfig(BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES, "1");

        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class);
            MockedStatic<RelayFileStoreEngine> mockFileStore = mockStatic(RelayFileStoreEngine.class)) {

            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));
            mockFileStore.when(RelayFileStoreEngine::getRelayFileCounter)
                .thenReturn(mockFileCounter);

            long now = System.currentTimeMillis();
            String maxReadTso = buildTso(now);
            // checkpoint 12小时前，大于maxLag=8h → Rule 2
            String checkpointLastTso = buildTso(now - 12 * 3600 * 1000L);

            when(storeEngine1.getMaxReadTso()).thenReturn(maxReadTso);
            BinlogOssRecord record = new BinlogOssRecord();
            record.setLastTso(checkpointLastTso);
            when(logEventTransmitter.getCheckpointTsoFromBackup("stream_1")).thenReturn(record);
            when(storeEngine1.getMaxCleanTso()).thenReturn("");

            cleaner.doClean();

            // Rule 2: cleanupTso = computeTsoBefore(maxReadTso, maxLag=120min)
            String expectedCleanupTso = RelayLogEventTransmitter.computeTsoBefore(maxReadTso, 120);
            verify(storeEngine1).clean(expectedCleanupTso);
        }
    }

    @Test
    public void testDoClean_MaxReadTsoBlank() {
        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class)) {
            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));

            when(storeEngine1.getMaxReadTso()).thenReturn("");

            cleaner.doClean();

            verify(storeEngine1, never()).clean(anyString());
        }
    }

    @Test
    public void testDoClean_EmptyStreamList() {
        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class)) {
            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Collections.emptyList());

            cleaner.doClean();

            verify(storeEngine1, never()).clean(anyString());
            verify(logEventTransmitter, never()).getCheckpointTsoFromBackup(anyString());
        }
    }

    @Test
    public void testDoClean_NoProgressWhenCleanupTsoNotAdvancing() {
        mockConfig(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD, "100");
        mockConfig(BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO, "0.8");
        mockConfig(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES, "120");
        mockConfig(BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES, "1");

        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class);
            MockedStatic<RelayFileStoreEngine> mockFileStore = mockStatic(RelayFileStoreEngine.class)) {

            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));
            mockFileStore.when(RelayFileStoreEngine::getRelayFileCounter)
                .thenReturn(mockFileCounter);

            long now = System.currentTimeMillis();
            String maxReadTso = buildTso(now);
            String checkpointLastTso = buildTso(now - 30 * 60 * 1000L);

            when(storeEngine1.getMaxReadTso()).thenReturn(maxReadTso);
            BinlogOssRecord record = new BinlogOssRecord();
            record.setLastTso(checkpointLastTso);
            when(logEventTransmitter.getCheckpointTsoFromBackup("stream_1")).thenReturn(record);

            // maxCleanTso 已经 >= cleanupTso → 不再推进
            when(storeEngine1.getMaxCleanTso()).thenReturn(checkpointLastTso);

            cleaner.doClean();

            verify(storeEngine1, never()).clean(anyString());
        }
    }

    @Test
    public void testDoClean_Rule3_SpacePressure() {
        // Rule 3: 空间压力触发，使用aggressive buffer
        mockConfig(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD, "100");
        mockConfig(BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO, "0.8");
        mockConfig(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES, "120");
        mockConfig(BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES, "1");

        // file count=90, slowdownThreshold=100, slowdownRatio=0.9 >= spaceSlowdownRatio=0.8 → 空间压力触发
        when(mockFileCounter.getTotalRelayFileCount()).thenReturn(90);

        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class);
            MockedStatic<RelayFileStoreEngine> mockFileStore = mockStatic(RelayFileStoreEngine.class)) {

            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));
            mockFileStore.when(RelayFileStoreEngine::getRelayFileCounter)
                .thenReturn(mockFileCounter);

            long now = System.currentTimeMillis();
            String maxReadTso = buildTso(now);
            // checkpoint 30分钟前 → Rule 1 gives computeTsoBefore(checkpointLastTso, 1min)
            String checkpointLastTso = buildTso(now - 30 * 60 * 1000L);

            when(storeEngine1.getMaxReadTso()).thenReturn(maxReadTso);
            BinlogOssRecord record = new BinlogOssRecord();
            record.setLastTso(checkpointLastTso);
            when(logEventTransmitter.getCheckpointTsoFromBackup("stream_1")).thenReturn(record);
            when(storeEngine1.getMaxCleanTso()).thenReturn("");

            cleaner.doClean();

            // Rule 3 overrides: computeTsoBefore(maxReadTso, 1min) > computeTsoBefore(checkpointLastTso, 1min)
            String expectedCleanupTso = RelayLogEventTransmitter.computeTsoBefore(maxReadTso, 1);
            verify(storeEngine1).clean(expectedCleanupTso);
        }
    }

    @Test
    public void testDoClean_NoCheckpoint_FallbackToMaxLagWindow() {
        // 没有可用的checkpoint，降级为基于maxReadTso的时间窗口清理
        mockConfig(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD, "100");
        mockConfig(BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO, "0.8");
        mockConfig(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES, "120");
        mockConfig(BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES, "1");

        try (MockedStatic<RelayStreamUtils> mockStreamUtils = mockStatic(RelayStreamUtils.class);
            MockedStatic<RelayFileStoreEngine> mockFileStore = mockStatic(RelayFileStoreEngine.class)) {

            mockStreamUtils.when(RelayStreamUtils::getStreamListAndCheck)
                .thenReturn(Arrays.asList("stream_1"));
            mockFileStore.when(RelayFileStoreEngine::getRelayFileCounter)
                .thenReturn(mockFileCounter);

            long now = System.currentTimeMillis();
            String maxReadTso = buildTso(now);

            when(storeEngine1.getMaxReadTso()).thenReturn(maxReadTso);
            // checkpoint为null → 没有可用的checkpoint
            when(logEventTransmitter.getCheckpointTsoFromBackup("stream_1")).thenReturn(null);
            when(storeEngine1.getMaxCleanTso()).thenReturn("");

            cleaner.doClean();

            // 降级：cleanupTso = computeTsoBefore(maxReadTso, maxLag=120min)
            String expectedCleanupTso = RelayLogEventTransmitter.computeTsoBefore(maxReadTso, 120);
            verify(storeEngine1).clean(expectedCleanupTso);
        }
    }
}
