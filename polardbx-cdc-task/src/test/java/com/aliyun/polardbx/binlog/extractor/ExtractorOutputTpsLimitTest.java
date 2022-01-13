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

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.extractor.binlog.BinlogGenerator;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExtractorOutputTpsLimitTest {

    static Random random = new Random(System.currentTimeMillis());

    public static void main(String args[]) throws Exception {
        int extractorNum = 1;

        MetricsManager metricsManager = new MetricsManager();
        metricsManager.start();
        CountDownLatch countDownLatch = new CountDownLatch(extractorNum);
        Executor executor = Executors.newFixedThreadPool(extractorNum, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < extractorNum; i++) {
            final int idx = i;
            executor.execute(() -> {
                try {
                    doExtractorTest(idx);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }

    public static void doExtractorTest(int i) throws Exception {
        String dbName = "test_db";
        BinlogGenerator generator = new BinlogGenerator(dbName,
            "tt",
            "create table tt(\n" + "\tid bigint(10) default 1,\n" + "\tname varchar(10) default 'abc',\n"
                + "\tname_1 varchar(10) default 'abc',\n" + "\tname_2 varchar(10) default 'abc',\n"
                + "\tgmt_create datetime(3) default null,\n" + "\tgmt_modify datetime(3) default null,\n"
                + "\tage int default 10,\n" + "\tage_1 int default 10,\n" + "\tage_2 int default 10,\n"
                + "\tcol_float float default 1.1,\n" + "\tcol_dec dec default 1.1,\n" + "\tcol_pt point default null,\n"
                + "\tcol_double double default 1.1,\n" + "\tstatus tinyint default 0\n" + ")");
        generator.generateFDE();

        int serverId = 111;

        EventAcceptFilter acceptFilter = new EventAcceptFilter("storage_" + i, true);
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

        //DisrupterFilter disrupterFilter = new DisrupterFilter(8192);
        // 记录RT
        //disrupterFilter.addFilter(new RtRecordFilter());
        // 先合并事务
        // 合并完事务后,要在合并事务是识别出逻辑DDL，，可以并发整形
        //disrupterFilter.addFilter(new TransactionBufferEventFilter(new LogEventStorage(null)));
        // 整形
        //disrupterFilter.addFilter(new RebuildEventLogFilter(serverId, acceptFilter, new HashSet<>()));

        //disrupterFilter.addFilter(new MinTSOFilter(null));

        //HandlerContext disruptorContext = new HandlerContext(disrupterFilter);

        HandlerContext tail = new HandlerContext(new LogEventFilter<Transaction>() {

            private Map<Long, Integer> set = new HashMap<>();

            @Override
            public void handle(Transaction event, HandlerContext context) throws Exception {
                Integer old = set.put(event.getTransactionId(), event.hashCode());
                if (old != null) {
                    throw new RuntimeException("duplicate exception old " + old + " , new " + event.hashCode());
                }
            }

            @Override
            public void onStart(HandlerContext context) {

            }

            @Override
            public void onStop() {

            }

            @Override
            public void onStartConsume(HandlerContext context) {

            }
        });

        //disruptorContext.setNext(tail);
        ThreadRecorder recorder = new ThreadRecorder("storage_" + i);
        RuntimeContext rc = new RuntimeContext(recorder);
        recorder.init();
        rc.putAttribute(RuntimeContext.DEBUG_MODE, new Object());
        rc.putAttribute(RuntimeContext.ATTRIBUTE_TABLE_META_MANAGER,
            new MockTableMetaDelegate(generator.getTableMeta(), dbName));
        ServerCharactorSet serverCharactorSet = new ServerCharactorSet();
        serverCharactorSet.setCharacterSetDatabase("utf8");
        serverCharactorSet.setCharacterSetConnection("utf8");
        serverCharactorSet.setCharacterSetDatabase("utf8");
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(new InetSocketAddress("127.0.0.1", 3306),
            "nnn                                                                                                                                                                                                                                                                                                                                  root",
            "admin",
            "utf8");
        authenticationInfo.setStorageInstId("storage_" + i);
        rc.setAuthenticationInfo(authenticationInfo);
        rc.setServerCharactorSet(serverCharactorSet);
        rc.setStartPosition(new BinlogPosition("binlog-00000", ""));
        //disruptorContext.setRuntimeContext(rc);
        //disruptorContext.fireStart();
        //disruptorContext.fireStartConsume();
        rc.getThreadRecorder().dump();
        //        SequenceLogEvent snap = generator.generateSNAPSequenceEvent();
        TableMapLogEvent tb1 = generator.generateTableMap();
        RowsQueryLogEvent rowLog1 = generator.generateRowQueryLogEvent();
        RowsLogEvent row1 = generator.generateRowsLogEvent();
        TableMapLogEvent tb2 = generator.generateTableMap();
        RowsQueryLogEvent rowLog2 = generator.generateRowQueryLogEvent();
        RowsLogEvent row2 = generator.generateRowsLogEvent();
        TableMapLogEvent tb3 = generator.generateTableMap();
        RowsQueryLogEvent rowLog3 = generator.generateRowQueryLogEvent();
        RowsLogEvent row3 = generator.generateRowsLogEvent();

        do {
            int size = random.nextInt(60) + 1;
            //            List<LogEvent> pCommitList = new ArrayList<>();
            List<LogEvent> pEventList = new ArrayList<>(size);
            for (int r = 0; r < size; r++) {
                pEventList.add(generator.generateBegin());
                //                pEventList.add(snap);
                pEventList.add(tb1);
                pEventList.add(rowLog1);
                pEventList.add(row1);
                pEventList.add(tb2);
                pEventList.add(rowLog2);
                pEventList.add(row2);
                pEventList.add(tb3);
                pEventList.add(rowLog3);
                pEventList.add(row3);
                //                pEventList.add(generator.generatePrepareLogEvent());

                //                pCommitList.add(generator.generateCMTSequenceEvent());
                pEventList.add(generator.generateCommit());
            }
            int[] randomIdx = randomIdx(size);
            int eventSize = 11;
            for (int r = 0; r < size; r++) {
                int idx = randomIdx[r];
//                disruptorContext.doNext(pEventList.get(eventSize * idx));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 1));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 2));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 3));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 4));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 5));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 6));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 7));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 8));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 9));
//                disruptorContext.doNext(pEventList.get(eventSize * idx + 10));
                //                disruptorContext.doNext(pEventList.get(12 * idx + 11));
            }
            //            randomIdx = randomIdx(size);
            //            for (int r = 0; r < size; r++) {
            //                int idx = randomIdx[r];
            //                disruptorContext.doNext(pCommitList.get(2 * idx));
            //                disruptorContext.doNext(pCommitList.get(2 * idx + 1));
            //            }
        } while (true);
        //        LockSupport.park();
    }

    static int[] randomIdx(int size) {
        int[] randomIdx = new int[size];
        for (int r = 0; r < size; r++) {
            randomIdx[r] = r;
        }
        for (int r = 0; r < size; r++) {
            int idx = random.nextInt(size);
            int d = randomIdx[idx];
            randomIdx[idx] = randomIdx[r];
            randomIdx[r] = d;
        }
        return randomIdx;
    }
}
