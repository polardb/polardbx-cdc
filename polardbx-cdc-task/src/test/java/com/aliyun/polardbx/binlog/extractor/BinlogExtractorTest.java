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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.heartbeat.TsoHeartbeat;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BinlogExtractorTest {

    TsoHeartbeat heartbeat;
    private List<BinlogParameter> parameterList;

    @Before
    public void initConfig() {
        parameterList = Lists.newArrayList();
        BinlogParameter parameter1 = new BinlogParameter();
        parameter1.setStorageInstId("polardbx-storage-1-master");
        parameterList.add(parameter1);
        //        BinlogParameter parameter2 = new BinlogParameter();
        //        parameter2.setStorageInstId("polardbx-storage-1-master");
        //        parameterList.add(parameter2);
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        heartbeat = new TsoHeartbeat();
        heartbeat.start();
    }

    @After
    public void after() {
        heartbeat.stop();
    }

    @Test
    public void testSearchStartCDC() {
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        List<BinlogExtractor> extractors = Lists.newArrayList();
        CountDownLatch countDownLatch = new CountDownLatch(parameterList.size());
        for (BinlogParameter parameter : parameterList) {
            BinlogExtractor extractor = new BinlogExtractor();
            List<LogEventFilter> filters = new ArrayList<>();
            filters.add(new LogEventFilter<LogEvent>() {

                private boolean isCountDown = false;

                @Override
                public void handle(LogEvent event, HandlerContext context) throws Exception {
                    //                    System.out.println(
                    //                            "handle event : " + event.getHeader().getType() + " : " + event.getHeader().getWhen());
                    //                    if (!isCountDown) {
                    //                        countDownLatch.countDown();
                    //                        isCountDown = true;
                    //                    }
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
            //            extractor.setProcessLogEventFilter(filters);
            extractor.init(parameter, new LogEventStorage(null), null);
            extractor.setLogEventHandler(new LogEventHandler<Transaction>() {

                private boolean isFirst = true;

                @Override
                public void handle(Transaction event) {
                    //                    System.out.println("handle transaction tso : " + event.getVirtualTSO());
                    //                    event.getLogEventList().forEach(e -> {
                    //                        System.out.println(
                    //                                "handle event : " + e.getHeader().getType() + " : " + e.getHeader().getWhen());
                    //                    });

                }

                @Override
                public void onStart(HandlerContext context) {
                    System.out.println("start handle");
                }

                @Override
                public void onStop() {
                    System.out.println("stop handle");
                }
            });
            extractors.add(extractor);
        }
        extractors.forEach(e -> e.start("678079212709144883213118573433606635530000000001213466"));
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
