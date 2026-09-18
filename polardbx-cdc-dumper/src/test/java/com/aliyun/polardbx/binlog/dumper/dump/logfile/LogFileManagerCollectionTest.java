/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.task.IDumperStatisticProvider;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LogFileManagerCollectionTest extends BaseTest {

    private LogFileManagerCollection collection;

    @Mock
    private LogFileManager logFileManager1;

    @Mock
    private LogFileManager logFileManager2;

    @Mock
    private ExecutionConfig executionConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        collection = new LogFileManagerCollection();
    }

    @Test
    public void testConstructor() {
        LogFileManagerCollection newCollection = new LogFileManagerCollection();
        assertNotNull(newCollection);
        assertNotNull(newCollection.streamSet());
        assertTrue(newCollection.streamSet().isEmpty());
    }

    @Test
    public void testAddAndGet() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        assertEquals(logFileManager1, collection.get("key1"));
        assertEquals(logFileManager2, collection.get("key2"));
        assertNull(collection.get("nonexistent"));
    }

    @Test
    public void testContains() {
        collection.add("key1", logFileManager1);

        assertTrue(collection.contains("key1"));
        assertFalse(collection.contains("key2"));
    }

    @Test
    public void testStart() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        collection.start();

        verify(logFileManager1).start();
        verify(logFileManager2).start();
    }

    @Test
    public void testStartWithMap() {
        Map<String, LogFileManager> logFileManagerMap = new HashMap<>();
        logFileManagerMap.put("key1", logFileManager1);
        logFileManagerMap.put("key2", logFileManager2);

        collection.start(logFileManagerMap);

        verify(logFileManager1).start();
        verify(logFileManager2).start();
        assertEquals(logFileManager1, collection.get("key1"));
        assertEquals(logFileManager2, collection.get("key2"));
    }

    @Test
    public void testStopSingle() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        collection.stop("key1");

        verify(logFileManager1).stop();
        verify(logFileManager2, never()).stop();
    }

    @Test
    public void testStopNonExistent() {
        collection.add("key1", logFileManager1);

        collection.stop("nonexistent");

        verify(logFileManager1, never()).stop();
    }

    @Test
    public void testStopAll() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        collection.stop();

        verify(logFileManager1).stop();
        verify(logFileManager2).stop();
    }

    @Test
    public void testClean() throws Exception {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        collection.clean("key1");

        verify(logFileManager1).clean();
        assertFalse(collection.contains("key1"));
        assertTrue(collection.contains("key2"));
    }

    @Test
    public void testCleanNonExistent() throws Exception {
        collection.add("key1", logFileManager1);

        collection.clean("nonexistent");

        verify(logFileManager1, never()).clean();
        assertTrue(collection.contains("key1"));
    }

    @Test
    public void testRefreshAndRestart() {
        collection.add("key1", logFileManager1);

        collection.refreshAndRestart("key1", executionConfig);

        verify(logFileManager1).refreshAndRestart(executionConfig);
    }

    @Test
    public void testRefresh() {
        collection.add("key1", logFileManager1);

        collection.refresh("key1", executionConfig);

        verify(logFileManager1).refresh(executionConfig);
    }

    @Test
    public void testGetCursorProviders() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        Map<String, IDumperStatisticProvider> cursorProviders = collection.getCursorProviders();

        assertNotNull(cursorProviders);
        assertEquals(2, cursorProviders.size());
        assertEquals(logFileManager1, cursorProviders.get("key1"));
        assertEquals(logFileManager2, cursorProviders.get("key2"));
        assertNotSame(collection.streamSet(), cursorProviders.keySet()); // Should be a new map
    }

    @Test
    public void testStreamSet() {
        collection.add("key1", logFileManager1);
        collection.add("key2", logFileManager2);

        Set<String> streamSet = collection.streamSet();

        assertNotNull(streamSet);
        assertEquals(2, streamSet.size());
        assertTrue(streamSet.contains("key1"));
        assertTrue(streamSet.contains("key2"));
    }
}