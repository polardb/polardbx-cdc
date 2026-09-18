/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.filter.LogBufferFilter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
public class CdcClientFilterTest {
    AtomicInteger filterCount = new AtomicInteger();
    AtomicLong filteredSize = new AtomicLong();
    Consumer<Long> addBinlogEventThroughput = (binlogEventThroughput) -> {
        filterCount.getAndIncrement();
    };
    Consumer<Long> addBinlogEventSize = filteredSize::getAndAdd;

    @Test
    @SneakyThrows
    public void testFilter() {
        //logFetcher.open("/Users/zm/Downloads/tmp/binlog22k.020834", 4);
        String path =
            CdcClientFilterTest.class.getClassLoader().getResource(".").getPath() + "binlog/binlog220k.020834";
        Set<String> tableNameSet = new HashSet<>();
        tableNameSet.add("wuzhe.pm_user_bill");
        // 成功被whitelist过滤
        LogBufferFilter logBufferFilter = new LogBufferFilter(tableNameSet, true);
        filterBinlogFile(path, logBufferFilter);
        Assert.assertTrue(filterCount.get() > 0);
        // 没有任何过滤
        logBufferFilter = new LogBufferFilter(tableNameSet, false);
        filterBinlogFile(path, logBufferFilter);
        Assert.assertEquals(0, filterCount.get());
        // 成功被blacklist 过滤
        tableNameSet.add("wuzhe.pm_order");
        logBufferFilter = new LogBufferFilter(tableNameSet, false);
        filterBinlogFile(path, logBufferFilter);
        Assert.assertTrue(filterCount.get() > 0);
    }

    @SneakyThrows
    private void filterBinlogFile(String path, LogBufferFilter logBufferFilter) {
        log.info("try filter binlog file: {} with filter:{}", path, logBufferFilter);
        FileLogFetcher logFetcher = new FileLogFetcher();
        logFetcher.open(path, 4);
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setServerCharactorSet(new ServerCharactorSet());
        logContext.setLogPosition(new LogPosition("binlog.020834", 4));
        logBufferFilter.setAddBinlogEventSize(addBinlogEventSize);
        logBufferFilter.setAddBinlogEventThroughput(addBinlogEventThroughput);
        filterCount.set(0);
        filteredSize.set(0);
        while (logFetcher.fetch()) {
            LogBuffer logBuffer = logFetcher.buffer();
            while (logBufferFilter.filter(logBuffer)) {
                log.info("filtered event count: {}, size:{}", filterCount.get(), filteredSize.get());
            }
            LogEvent event = logDecoder.decode(logFetcher.buffer(), logContext);
            if (event == null) {
                continue;
            }
            int type = event.getHeader().getType();
            if (type == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                logBufferFilter.setFormatDescriptionLogEvent((FormatDescriptionLogEvent) event);
            }
        }
        logFetcher.close();
    }
}