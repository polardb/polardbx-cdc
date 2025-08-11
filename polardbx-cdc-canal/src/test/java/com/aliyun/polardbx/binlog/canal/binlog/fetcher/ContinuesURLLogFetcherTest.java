/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class ContinuesURLLogFetcherTest {

    @Test
    public void fetchTest() throws IOException {
        try (MockedStatic<LogFetcherFactory> factoryMockedStatic = mockStatic(LogFetcherFactory.class)) {
            LinkedList<BinlogFile> binlogFiles = new LinkedList<>();
            BinlogFile currentBinlogFile = new BinlogFile();
            currentBinlogFile.setLogname("my.01");
            currentBinlogFile.setIntranetDownloadLink("http://127.0.0.1/my.01");
            currentBinlogFile.setDownloadLink(currentBinlogFile.getIntranetDownloadLink());
            currentBinlogFile.setFileSize(1024L);
            BinlogFile nextBinlogFile = new BinlogFile();
            nextBinlogFile.setLogname("my.02");
            nextBinlogFile.setIntranetDownloadLink("http://127.0.0.1/my.02");
            nextBinlogFile.setDownloadLink(nextBinlogFile.getIntranetDownloadLink());
            nextBinlogFile.setFileSize(1024L);
            binlogFiles.add(currentBinlogFile);
            binlogFiles.add(nextBinlogFile);
            Logger logger = Mockito.mock(Logger.class);
            URLLogFetcher nextUrlLogFetcher = Mockito.mock(URLLogFetcher.class);
            factoryMockedStatic.when(() -> LogFetcherFactory.createURLLogFetcher(anyString(), anyString()))
                .thenReturn(nextUrlLogFetcher);
            URLLogFetcher urlLogFetcher = Mockito.mock(URLLogFetcher.class);
            Mockito.when(urlLogFetcher.fetch()).thenReturn(false);
            Mockito.when(urlLogFetcher.readSize()).thenReturn(1024L);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            ContinuesURLLogFetcher fetcher =
                new ContinuesURLLogFetcher("sid", urlLogFetcher, currentBinlogFile, binlogFiles, executorService);
            fetcher.setLogger(logger);
            fetcher.fetch();

            Mockito.verify(urlLogFetcher, Mockito.times(1)).close();
            Mockito.verify(logger, Mockito.times(1)).info("finish read {} size : {}", currentBinlogFile, 1024L);
            Mockito.verify(logger, Mockito.times(1))
                .info("last file {} finish , rotate to new binlog file {} with link : {}", "my.01", "my.02",
                    "http://127.0.0.1/my.02");
        }
    }
}
