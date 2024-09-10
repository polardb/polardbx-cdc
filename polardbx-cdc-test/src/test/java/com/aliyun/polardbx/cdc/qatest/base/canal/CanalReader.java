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
package com.aliyun.polardbx.cdc.qatest.base.canal;

import com.alibaba.otter.canal.instance.core.CanalInstance;
import com.alibaba.otter.canal.instance.core.CanalInstanceGenerator;
import com.alibaba.otter.canal.instance.manager.CanalInstanceWithManager;
import com.alibaba.otter.canal.instance.manager.model.Canal;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter;
import com.alibaba.otter.canal.parse.CanalEventParser;
import com.alibaba.otter.canal.parse.inbound.mysql.MysqlEventParser;
import com.alibaba.otter.canal.protocol.ClientIdentity;
import com.alibaba.otter.canal.protocol.Message;
import com.alibaba.otter.canal.server.embedded.CanalServerWithEmbedded;
import com.alibaba.otter.canal.sink.entry.EntryEventSink;
import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.exception.ZkInterruptedException;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class CanalReader {

    private final String destination;
    private final CanalParameter canalParameter;
    private CanalServerWithEmbedded canalServer;
    private ClientIdentity clientIdentity;
    private CanalReaderDownStreamHandler handler;
    private volatile boolean running = false;
    private final AtomicBoolean needRestartDestination = new AtomicBoolean(false);

    public CanalReader(String destination, CanalParameter parameter) {
        this.destination = destination;
        this.canalParameter = parameter;
    }

    public void start() {
        if (running) {
            return;
        }

        this.handler = new CanalReaderDownStreamHandler();
        this.canalServer = new CanalServerWithEmbedded();
        this.canalServer.setCanalInstanceGenerator(new CanalInstanceGenerator() {

            @Override
            public CanalInstance generate(String destination) {
                final Canal canal = buildCanalConfig(destination, canalParameter);
                final CanalReaderAlarmHandler alarmHandler = new CanalReaderAlarmHandler();

                CanalInstanceWithManager instance = new CanalInstanceWithManager(canal, "") {

                    @Override
                    protected void initMetaManager() {
                        metaManager = new CanalReaderMetaManager();
                    }

                    @Override
                    protected void startEventParserInternal(CanalEventParser parser, boolean isGroup) {
                        if (parser instanceof MysqlEventParser) {
                            MysqlEventParser mysqlEventParser = (MysqlEventParser) parser;
                            mysqlEventParser.setSupportBinlogFormats("ROW,STATEMENT,MIXED");
                            mysqlEventParser.setSupportBinlogImages("FULL,MINIMAL,NOBLOB");
                        }

                        super.startEventParserInternal(parser, isGroup);
                    }

                    @Override
                    protected void initEventSink() {
                        log.info("init eventSink begin...");
                        eventSink = new EntryEventSink();
                        ((EntryEventSink) eventSink).setFilterTransactionEntry(false);
                        ((EntryEventSink) eventSink).setEventStore(getEventStore());
                        ((EntryEventSink) eventSink).addHandler(handler);
                        log.info("init eventSink end! \n\t load CanalEventSink:{}", eventSink.getClass().getName());
                    }

                    @Override
                    protected void initEventParser() {
                        super.initEventParser();
                    }
                };

                instance.setAlarmHandler(alarmHandler);
                return instance;
            }
        });

        //构造instance的时候已经指定了filter，clientIdentity就不需要指定filter了
        this.clientIdentity = new ClientIdentity(destination, (short) 1001, "");
        this.canalServer.start();
        this.startDestination();

        running = true;
    }

    public void shutdown() {
        if (handler != null) {
            try {
                handler.stop();
            } catch (Exception e) {
                log.warn("failed destroy handler", e);
            }
            handler = null;
        }

        //停止的时候可能Server从来没有启动过，所以加非空校验
        if (canalServer != null) {
            canalServer.stop(destination);
            canalServer.stop();
        }

        running = false;
    }

    public Message getWithoutAck() throws InterruptedException {
        checkBeforeGet();
        return doGet(false);
    }

    public Message getAndAck() throws InterruptedException {
        checkBeforeGet();
        return doGet(true);
    }

    public void ack(long batchId) {
        this.canalServer.ack(clientIdentity, batchId);
    }

    public void rollback(long batchId) {
        this.canalServer.rollback(clientIdentity, batchId);
    }

    private void startDestination() {
        this.canalServer.start(destination);
        this.canalServer.subscribe(clientIdentity);
    }

    private void stopDestination() {
        this.canalServer.stop(destination);
    }

    private void checkBeforeGet() {
        checkRestart();
    }

    private Message doGet(boolean ack) throws InterruptedException {
        Message message;
        try {
            int batchSize = 10000;
            if (ack) {
                message = canalServer.get(clientIdentity, batchSize);
            } else {
                message = canalServer.getWithoutAck(clientIdentity, batchSize);
            }
        } catch (ZkInterruptedException e) {
            throw new InterruptedException();
        }

        if (message == null || message.getId() == -1L) {
            return null;
        } else {
            return message;
        }
    }

    private void checkRestart() {
        if (needRestartDestination.compareAndSet(true, false)) {
            log.info("restart destination begin.");
            stopDestination();
            startDestination();
            log.info("restart destination end.");
        }
    }

    public static Canal buildCanalConfig(String destination, CanalParameter parameter) {
        Canal canal = new Canal();
        canal.setId(9999L);//id值没有太多意义，赋一个固定值即可
        canal.setName(destination);
        canal.setCanalParameter(parameter);
        return canal;
    }
}
