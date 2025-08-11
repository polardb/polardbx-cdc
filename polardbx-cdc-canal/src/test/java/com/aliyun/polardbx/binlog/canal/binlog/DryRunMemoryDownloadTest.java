/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.SearchMetricsManager;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DryRunMemoryDownloadTest extends BaseTest {
    @Mock
    private URLLogFetcher urlLogFetcher;

    @Mock
    private LogBuffer logBuffer;

    @Mock
    private LogEvent logEvent;

    @Mock
    private LogContext logContext;

    @Mock
    private ServerCharactorSet serverCharactorSet;

    @Mock
    private LogPosition logPosition;

    @Mock
    private Logger logger;

    @Test
    public void testMain() throws IOException {
        MockitoAnnotations.openMocks(this);
        DryRunMemoryDownload.logger = logger;
        SearchMetricsManager.setLogger(logger);

        // Mock the behavior of URLLogFetcher
        doNothing().when(urlLogFetcher).open(anyString(), anyLong(), any());
        doNothing().when(urlLogFetcher).open(anyString(), anyLong(), anyLong(), any());
        when(urlLogFetcher.fetch()).thenReturn(true, false); // Simulate one fetch
        when(urlLogFetcher.buffer()).thenReturn(logBuffer);

        // Mock the behavior of LogContext
        when(logContext.getServerCharactorSet()).thenReturn(serverCharactorSet);
        when(logContext.getLogPosition()).thenReturn(logPosition);

        // Call the main method
        DryRunMemoryDownload dryRunMemoryDownload = Mockito.mock(DryRunMemoryDownload.class);
        Mockito.doCallRealMethod().when(dryRunMemoryDownload).setUrl(anyString());
        Mockito.when(dryRunMemoryDownload.createFetcher()).thenReturn(urlLogFetcher);
        dryRunMemoryDownload.setUrl("http://example.com/test");
        Mockito.doCallRealMethod().when(dryRunMemoryDownload).dryRun();
        dryRunMemoryDownload.dryRun();

        // Verify interactions
        verify(urlLogFetcher).open(anyString(), anyLong(), any());
        verify(urlLogFetcher, Mockito.times(2)).fetch();
        verify(urlLogFetcher, Mockito.times(1)).buffer();
        verify(urlLogFetcher).close();
    }

    private void registerCacheManager(CacheManager cacheManager) throws NoSuchFieldException, IllegalAccessException {
        cacheManager.registerStorage("test");
        registerSpringObject("cacheManager", cacheManager);
    }

    @Test
    public void testMainFunc() throws IOException, NoSuchFieldException, IllegalAccessException {
        CacheManager cacheManager = new CacheManager();
        registerCacheManager(cacheManager);
        HttpURLConnection conn = Mockito.mock(HttpURLConnection.class);
        InputStream is = new ByteArrayInputStream(new byte[] {-2, 0x62, 0x69, 0x6e});
        Mockito.when(conn.getInputStream()).thenReturn(is);
        mockUrlConnection("my-proto-dryrun://example.com/test", conn);
        DryRunMemoryDownload runMemoryDownload = new DryRunMemoryDownload();
        runMemoryDownload.main(new String[] {"my-proto-dryrun://example.com/test"});
        unregisterSpringObject("cacheManager", cacheManager);
    }
}
