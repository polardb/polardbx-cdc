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

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkResult;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

@Slf4j
public class MysqlWithTsoEventParserTest {
    BinlogPosition startPosition;

    @Test
    public void test() {
        MysqlWithTsoEventParser parser = new MysqlWithTsoEventParser();
        parser.setRds(true);

        startPosition = new BinlogPosition("binlog.000001", 4, 1, 0);
        AuthenticationInfo authInfo = new AuthenticationInfo(
            new InetSocketAddress("xxx.com", 3306), "root", "");

        parser.setSearchFunction(new Sk());
        parser.addFilter(new LogEventFilter() {
            @Override
            public void handle(HandlerEvent event, HandlerContext context) throws Exception {
                //log.info("handle {} {}", event, context);
                context.doNext(event);
            }

            @Override
            public void onStart(HandlerContext context) {
                log.info("onStart {}", context);
            }

            @Override
            public void onStop() {
                log.info("onStop");
            }

            @Override
            public void onStartConsume(HandlerContext context) {
                log.info("onStartConsume {}", context);
            }
        });

        parser.setEventHandler(new LogEventHandler() {
            @Override
            public void handle(Object event) throws Exception {
                log.info("handle {}", event);
            }

            @Override
            public void onStart(HandlerContext context) {
                log.info("onStart {}", context);
            }

            @Override
            public void onStop() {
                log.info("onStop");
            }
        });

        parser.start(authInfo, startPosition);

        try {
            Thread.sleep(100 * 1000L);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        parser.stop();
    }

    class Sk implements SinkFunction, SinkResult {

        @Override
        public boolean sink(LogEvent event, LogPosition logPosition)
            throws CanalParseException, TableIdNotFoundException {
            return false;
        }

        @Override
        public BinlogPosition searchResult() {
            return startPosition;
        }

        @Override
        public String getTopologyContext() {
            return null;
        }
    }

}