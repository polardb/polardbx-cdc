/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.error.PolardbxClientException;
import com.aliyun.polardbx.binlog.client.handler.LogEventPreHandler;
import com.aliyun.polardbx.binlog.client.handler.OutputHandler;
import com.aliyun.polardbx.binlog.client.handler.RowLogEventHandler;
import com.aliyun.polardbx.binlog.client.handler.RowTableNameFilter;
import com.aliyun.polardbx.binlog.client.handler.SimpleExceptionHandler;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CdcClientParser {

    private static final Logger logger = LoggerFactory.getLogger(CdcClientParser.class);
    private final AtomicBoolean started = new AtomicBoolean(true);

    private final IExceptionHandler exceptionHandler;
    private final StreamObserverLogFetcher logFetcher;
    private Disruptor<LogEventWrapper> parserDisruptor;
    private boolean dryRun = false;

    private static final long INTERVAL = 15;
    private final ServerCharactorSet serverCharset;
    private final RowTableNameFilter filter = new RowTableNameFilter();
    private final OutputHandler outputHandler;
    private ClientHealthChecker checker;
    private final BinlogPosition startPosition;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "parser-ringbuffer-metrics-thread");
            t.setDaemon(true);
            return t;
        });

    public CdcClientParser(StreamObserverLogFetcher logFetcher, BinlogPosition startPosition,
                           IEventHandler handle, ServerCharactorSet serverCharset, IExceptionHandler exceptionHandler,
                           int ringBufferSize, int rowParserThreadNum) {
        this.logFetcher = logFetcher;
        this.serverCharset = serverCharset;
        this.exceptionHandler = exceptionHandler;
        this.startPosition = startPosition;
        this.outputHandler = new OutputHandler(handle, startPosition);
        initDisruptor(ringBufferSize, rowParserThreadNum);
    }

    public void initDisruptor(int ringBufferSize, int rowParserThreadNum) {
        // 初始化 Disruptor
        parserDisruptor = new Disruptor<>(
            LogEventWrapper::new,
            ringBufferSize,
            r -> {
                Thread t = new Thread(r, "disruptor-parser-thread");
                t.setDaemon(true);
                return t;
            },
            ProducerType.SINGLE,
            new YieldingWaitStrategy()
        );

        RowLogEventHandler[] handlers = new RowLogEventHandler[rowParserThreadNum];
        for (int i = 0; i < rowParserThreadNum; i++) {
            handlers[i] = new RowLogEventHandler(serverCharset, filter);
        }
        logger.info("init parser ringbuffer with buffer size {}, row parser thread num {}", ringBufferSize,
            rowParserThreadNum);
        // 定义多阶段处理逻辑
        // 预处理阶段，执行原逻辑，忽略row event parser
        parserDisruptor.handleEventsWith(new LogEventPreHandler()).
            // 多线程并行 parser event
                handleEventsWithWorkerPool(handlers).
            // 按顺序输出下游
                then(outputHandler);

        SimpleExceptionHandler seh = new SimpleExceptionHandler();
        parserDisruptor.setDefaultExceptionHandler(seh);
        checker = seh;
        // 启动 Disruptor
        parserDisruptor.start();
        scheduledExecutorService.scheduleAtFixedRate(this::print, INTERVAL, INTERVAL, TimeUnit.SECONDS);
    }

    public void stop() {
        started.set(false);
        if (parserDisruptor != null){
            parserDisruptor.shutdown();
        }
        scheduledExecutorService.shutdown();
    }

    public LogPosition getLogPosition() {
        return outputHandler.getLastPushLogPosition();
    }

    public void print() {
        logger.info("ringbuffer queue remain data size : {}",
            parserDisruptor.getRingBuffer().getBufferSize() - parserDisruptor.getRingBuffer().remainingCapacity());
    }

    public LogDecoder buildDecoder() {
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        decoder.setNeedRecordData(false);
        return decoder;
    }

    public LogContext buildLogContext() {
        LogContext context = new LogContext();
        context.setServerCharactorSet(serverCharset);
        FormatDescriptionLogEvent fdl = FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x;
        fdl.getHeader().setChecksumAlg(LogEvent.BINLOG_CHECKSUM_ALG_CRC32);
        context.setFormatDescription(fdl);
        context.setLogPosition(new LogPosition(startPosition.getFileName(), 4));
        return context;
    }

    public void parser() {
        try {
            logger.info("parser thread started!");
            final LogDecoder decoder = buildDecoder();
            final LogContext context = buildLogContext();
            while (started.get() && logFetcher.fetch()) {
                LogEvent event = decoder.decode(logFetcher.buffer(), context);
                if (event == null || dryRun) {
                    continue;
                }
                checker.check();
                // 将 LogEvent 包装为 LogEventWrapper 并提交到 Disruptor
                RingBuffer<LogEventWrapper> ringBuffer = parserDisruptor.getRingBuffer();
                long sequence = ringBuffer.next();
                try {
                    LogEventWrapper wrapper = ringBuffer.get(sequence);
                    wrapper.setLogEvent(event);
                    wrapper.setLogPosition(context.getLogPosition().clone());
                } finally {
                    ringBuffer.publish(sequence);
                }

            }
        } catch (Throwable e) {
            if (exceptionHandler != null) {
                exceptionHandler.handle(e);
            } else {
                logger.error("exceptionHandler is null , parser event failed!", e);
                throw new PolardbxClientException("exceptionHandler is null , parser event failed!", e);
            }
        } finally {
            started.set(false);
        }

    }

    public void setAcceptTable(Set<String> acceptTableSet) {
        this.filter.setAcceptTableSet(acceptTableSet);
        logger.info("set accept table set {}", JSON.toJSONString(acceptTableSet));
    }

    public void setIgnoreTable(Set<String> ignoreTableSet) {
        this.filter.setIgnoreTableSet(ignoreTableSet);
        logger.info("set ignore table set {}", JSON.toJSONString(ignoreTableSet));
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

}
