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
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.ContinuesURLLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class URLBinlogDumpe {

    @Before
    public void before() {
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void testRange() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
            "")
            .openConnection();
        connection.connect();
        String messageString = connection.getHeaderField("Accept-Ranges");
        connection.disconnect();
        System.out.println(messageString);
    }

    @Test
    public void localBinlogDump() throws Exception {

        System.setProperty("taskName", "Final");
        LinkedList<BinlogFile> fileList = new LinkedList<>();
        BinlogFile first = new BinlogFile();
        first.setIntranetDownloadLink(
            "");
        first.setLogname("mysql-bin.000300");
        fileList.add(first);

        URLLogFetcher fetcher = new URLLogFetcher();
        fetcher.open(
            first.getIntranetDownloadLink(),
            503315890,
            409596450);
        ContinuesURLLogFetcher fetcherWrapper = new ContinuesURLLogFetcher("", fetcher, first, fileList);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setCharset("utf-8");

        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext lc = new LogContext(new FormatDescriptionLogEvent(7));
        lc.setServerCharactorSet(new ServerCharactorSet("utf8", "utf8", "utf8", "utf8"));
        lc.setLogPosition(new LogPosition("mysql-bin.000300", 0));

        long eventCount = 0;
        Set<String> xidSet = new HashSet<>();
        boolean existCurrentTran = false;
        String currentXid = null;
        BinlogPosition lastTran = null;
        System.out.println("begin fetch");
        while (fetcherWrapper.fetch()) {
            LogBuffer buffer = fetcherWrapper.buffer();
            LogEvent le = decoder.decode(buffer, lc);
            if (le == null) {
                continue;
            }
            if (LogEventUtil.isStart(le)) {
//                System.out.println("begin pos : " + lc.getLogPosition());
                if (existCurrentTran) {
                    throw new RuntimeException();
                }
                String xid = LogEventUtil.getXid(le);
                if (xid != null) {
                    xidSet.add(xid);
                    currentXid = xid;
                    Long tranId = LogEventUtil.getTranIdFromXid(xid, "utf8");
                }
                existCurrentTran = true;
                lastTran =
                    new BinlogPosition(lc.getLogPosition().getFileName(), lc.getLogPosition().getPosition(), -1, -1);
            } else if (LogEventUtil.isPrepare(le)) {
//                System.out.println("prepare pos : " + lc.getLogPosition());
                XaPrepareLogEvent xaPrepareLogEvent = (XaPrepareLogEvent) le;
                if (xaPrepareLogEvent.isOnePhase()) {
                    currentXid = null;
                }
                existCurrentTran = false;
            } else if (LogEventUtil.isCommit(le)) {
//                System.out.println("commit pos : " + lc.getLogPosition());
                String xid = LogEventUtil.getXid(le);
                if (xid != null) {
                    xidSet.remove(xid);
                }
                currentXid = null;
                existCurrentTran = false;
            }
//            searchTsoEventHandle.handle(le, lc.getLogPosition());
            {
                eventCount++;
            }
//            if (searchTsoEventHandle.interupt()) {
//                break;
//            }
        }
        fetcher.close();
        System.out.println("search pos : @ " + " count : " + eventCount);
    }
}
