/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.LocalBinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.filter.MinTSOFilter;
import com.aliyun.polardbx.binlog.extractor.filter.TransactionBufferEventFilter;
import com.aliyun.polardbx.binlog.storage.DeleteMode;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.aliyun.polardbx.binlog.storage.Repository;

//import com.aliyun.polardbx.binlog.extractor.filter.DisrupterFilter;

public class LocalBinlogExtractorTest {

    public static void main(String args[]) {
        LocalBinlogParser localBinlogParser = new LocalBinlogParser("/Users/yanfenglin/Downloads/mysql-bin.000001");
        EventAcceptFilter acceptFilter = new EventAcceptFilter("", true);
        acceptFilter.addAcceptEvent(LogEvent.FORMAT_DESCRIPTION_EVENT);
        // accept dml
        acceptFilter.addAcceptEvent(LogEvent.WRITE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.WRITE_ROWS_EVENT_V1);
        acceptFilter.addAcceptEvent(LogEvent.DELETE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.DELETE_ROWS_EVENT_V1);
        acceptFilter.addAcceptEvent(LogEvent.UPDATE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.UPDATE_ROWS_EVENT_V1);
        // accept query
        acceptFilter.addAcceptEvent(LogEvent.QUERY_EVENT);
        // support trace
        acceptFilter.addAcceptEvent(LogEvent.ROWS_QUERY_LOG_EVENT);
        // accept xa
        acceptFilter.addAcceptEvent(LogEvent.XA_PREPARE_LOG_EVENT);
        // accept tso
        acceptFilter.addAcceptEvent(LogEvent.SEQUENCE_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.GCN_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.TABLE_MAP_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.XID_EVENT);

        //DisrupterFilter disrupterFilter = new DisrupterFilter(1024);
        //localBinlogParser.addFilter(disrupterFilter);
        //        disrupterFilter.addFilter(new PositionRecorderLogEventFilter());
        // 记录RT
        //disrupterFilter.addFilter(new RtRecordFilter());
        // 先合并事务
        // 合并完事务后,要在合并事务是识别出逻辑DDL，，可以并发整形
        LogEventStorage storage = new LogEventStorage(
            new Repository(false, "/Users/yanfenglin/Downloads/db", false, 80, 80, 80, DeleteMode.RANGE, 1000));
        storage.start();
        localBinlogParser.addFilter(new TransactionBufferEventFilter(storage));
        // 整形
//        localBinlogParser.addFilter(new RebuildEventLogFilter(1, acceptFilter, Sets.newHashSet("__cdc___000000")));

        localBinlogParser.addFilter(new MinTSOFilter("682996183695755398413610270520519024780000000008192210"));
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setStorageInstId("111");
//        localBinlogParser.setEventHandler(new LogEventHandler() {
//            @Override
//            public void handle(Object event) throws Exception {
//
//            }
//
//            @Override
//            public void onStart(HandlerContext context) {
//
//            }
//
//            @Override
//            public void onStop() {
//
//            }
//        });
        localBinlogParser.start(authenticationInfo,
            new BinlogPosition("", "682996183695755398413610270520519024780000000008192210"));
        try {
            Thread.sleep(100000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
