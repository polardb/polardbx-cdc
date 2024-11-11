/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkResult;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;

import java.io.IOException;

public class LocalBinlogParser extends AbstractEventParser {

    private int bufferSize = 8192;

    private String binlogfilename;

    public LocalBinlogParser(String binlogfilename) {
        this.binlogfilename = binlogfilename;
    }

    public void dump(SinkFunction sinkFunction) throws IOException, TableIdNotFoundException {
        FileLogFetcher fetcher = new FileLogFetcher(bufferSize);
        fetcher.open(binlogfilename);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.ROTATE_EVENT);
        decoder.handle(LogEvent.FORMAT_DESCRIPTION_EVENT);
        decoder.handle(LogEvent.QUERY_EVENT);
        decoder.handle(LogEvent.TABLE_MAP_EVENT);
        decoder.handle(LogEvent.XID_EVENT);
        decoder.handle(LogEvent.SEQUENCE_EVENT);
        decoder.handle(LogEvent.GCN_EVENT);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT);
        decoder.handle(LogEvent.UPDATE_ROWS_EVENT);
        decoder.handle(LogEvent.DELETE_ROWS_EVENT);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.UPDATE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.DELETE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.ROWS_QUERY_LOG_EVENT);
        decoder.handle(LogEvent.XA_PREPARE_LOG_EVENT);
        LogContext context = new LogContext();
        context.setServerCharactorSet(
            ServerCharactorSet.builder().characterSetServer("utf8mb4").characterSetClient("utf8mb4")
                .characterSetConnection("utf8mb4").characterSetDatabase("utf8mb4").build());
        LogPosition logPosition = new LogPosition(binlogfilename, 0);
        context.setLogPosition(logPosition);
        while (fetcher.fetch()) {
            LogEvent logEvent = decoder.decode(fetcher, context);

            if (Thread.interrupted()) {
                break;
            }

            if (logEvent == null) {
                continue;
            }

            if (!sinkFunction.sink(logEvent, context.getLogPosition())) {
                break;
            }
        }
    }

    @Override
    protected ErosaConnection buildErosaConnection() {
        return null;
    }

    @Override
    protected BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) throws IOException {
        return position;
    }

    @Override
    public void start(AuthenticationInfo master, BinlogPosition startPosition) {
        Thread t = new Thread(() -> {
            try {
                ThreadRecorder recorder = new ThreadRecorder("111");
                recorder.init();

                final RuntimeContext runtimeContext = new RuntimeContext(recorder);
                runtimeContext.setAuthenticationInfo(master);
                head.setRuntimeContext(runtimeContext);

                recorder.dump();

                //                    if (startPosition.getTso() > 0) {
                //                        runtimeContext.setMaxTSO(startPosition.getTso());
                //                    }
                runtimeContext.setVersion(polarxVersion);
                runtimeContext.setRecovery(true);
                runtimeContext.setStartPosition(startPosition);
                runtimeContext.setServerCharactorSet(serverCharactorSet);
                runtimeContext.setHostAddress("127");
                runtimeContext.setLowerCaseTableNames(1);
                if (searchFunction instanceof SinkResult) {
                    runtimeContext.setInitTopology(((SinkResult) searchFunction).getTopologyContext());
                }
                head.fireStart();

                final SinkFunction sinkHandler = (event, logPosition) -> {
                    runtimeContext.setBinlogFile(logPosition.getFileName());
                    head.setRuntimeContext(runtimeContext);
                    try {
                        head.doNext(event);
                    } catch (Exception e) {
                        System.out.println("fatal error at position " + logPosition);
                        e.printStackTrace();
                        return false;
                    }

                    return true;

                };

                head.setRuntimeContext(runtimeContext);
                head.fireStartConsume();

                dump(sinkHandler);

                // 4. 开始dump数据
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {

    }
}
