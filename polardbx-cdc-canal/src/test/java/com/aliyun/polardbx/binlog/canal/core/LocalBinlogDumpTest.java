/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore
public class LocalBinlogDumpTest {

    @Test
    public void localBinlogDump() throws IOException {
        String fileName = "mysql-bin.003461";
        String path = "/Users/yanfenglin/Downloads/";
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(path + fileName, 0);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setCharset("utf-8");
//        SearchTsoEventHandle searchTsoEventHandle = new SearchTsoEventHandle(-1, authenticationInfo);
//        searchTsoEventHandle.setEndPosition(new BinlogPosition(fileName, 9999999, -1, -1));
//        searchTsoEventHandle.setCurrentFile(fileName);
//        searchTsoEventHandle.setTotalSize(new File(path + fileName).getTotalSpace());
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext lc = new LogContext(new FormatDescriptionLogEvent(7));
        lc.setLogPosition(new LogPosition(fileName, 0));
        long eventCount = 0;
        while (fetcher.fetch()) {
            LogEvent le = decoder.decode(fetcher, lc);
//            if (le instanceof TableMapLogEvent) {
//                TableMapLogEvent tml = (TableMapLogEvent) le;
//                if (tml.getTableName().contains("instruction")) {
//                    this.getClass();
//                }
//                System.out.println("table map db:" + tml.getDbName() + " ,tb:" + tml.getTableName());
//            }
//            else if (le instanceof UpdateRowsLogEvent) {
//                System.out.println("update event");
//            } else if (le instanceof WriteRowsLogEvent) {
//                System.out.println("write rows event");
//            }
//            searchTsoEventHandle.handle(le, lc.getLogPosition());
//            eventCount++;
//            if (searchTsoEventHandle.interupt()) {
//                break;
//            }
        }
//        System.out.println("search pos : @ " + searchTsoEventHandle.searchResult() + " count : " + eventCount);
    }
}
