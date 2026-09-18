/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.error.PolardbxClientException;
import com.aliyun.polardbx.binlog.client.filter.LogBufferFilter;
import com.aliyun.polardbx.binlog.client.handler.LogEventPreHandler;
import com.aliyun.polardbx.binlog.client.handler.OutputHandler;
import com.aliyun.polardbx.binlog.client.handler.RowLogEventHandler;
import com.aliyun.polardbx.binlog.client.handler.RowTableNameFilter;
import com.aliyun.polardbx.binlog.client.handler.SimpleExceptionHandler;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CDC客户端的binlog解析器，基于Disruptor实现高性能事件处理管道。
 * <p>
 * 解析管道分为三个阶段：
 * 1. 预处理阶段（LogEventPreHandler）：识别事件类型，解析非DML事件（DDL/心跳/事务边界等）
 * 2. 并行解析阶段（RowLogEventHandler）：多线程并行解析行事件（RowsLogEvent）
 * 3. 输出阶段（OutputHandler）：按顺序将解析后的事件推送给下游
 */
public class CdcClientParser {

    protected static final Logger logger = LoggerFactory.getLogger(CdcClientParser.class);
    /**
     * 解析器运行状态标志，设为false时停止解析
     */
    protected final AtomicBoolean started = new AtomicBoolean(true);

    /**
     * 异常处理器，解析过程中的异常会回调此处理器
     */
    protected final IExceptionHandler exceptionHandler;
    /**
     * 基于StreamObserver的binlog数据拉取器，从Dumper服务端获取binlog流
     */
    protected final StreamObserverLogFetcher logFetcher;
    /**
     * Disruptor环形缓冲区，用于在解析管道各阶段间传递事件
     */
    protected Disruptor<LogEventWrapper> parserDisruptor;
    /**
     * 试运行模式，开启后仅解码不解析，用于性能基准测试
     */
    protected boolean dryRun = false;

    /**
     * 定时打印指标的时间间隔（秒）
     */
    protected static final long INTERVAL = 15;
    /**
     * 服务端字符集配置，用于解析binlog中的字符串数据
     */
    protected final ServerCharactorSet serverCharset;
    /**
     * 行事件的表名过滤器，支持白名单/黑名单模式
     */
    protected RowTableNameFilter filter = new RowTableNameFilter();
    /**
     * 最终输出处理器，负责按序将解析后的事件推送给下游
     */
    protected final OutputHandler outputHandler;
    /**
     * 健康检查器，用于在解析循环中检测异步处理阶段的异常
     */
    protected ClientHealthChecker checker;
    /**
     * binlog起始位置，用于初始化解析上下文
     */
    protected final BinlogPosition startPosition;
    /**
     * Disruptor RingBuffer的大小，必须为2的幂
     */
    protected int ringBufferSize;
    /**
     * 行事件并行解析的线程数
     */
    protected int rowParserThreadNum;
    /**
     * 是否启用base64解码
     */
    protected AtomicBoolean decode64Enabled = new AtomicBoolean(false);
    /**
     * RingBuffer等待策略，影响消费者等待时的CPU行为
     */
    protected RBWaitStrategy waitStrategy = RBWaitStrategy.YIELD;
    protected final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "parser-ringbuffer-metrics-thread");
            t.setDaemon(true);
            return t;
        });
    /**
     * LogBuffer级别的过滤器，在解码前就能快速过滤不需要的表的DML事件，减少解码开销
     */
    protected LogBufferFilter logBufferFilter = new LogBufferFilter(null, true);
    /**
     * 预处理器，在Disruptor管道的第一阶段识别并解析非DML事件
     */
    protected LogEventPreHandler logEventPreHandler;

    public CdcClientParser(StreamObserverLogFetcher logFetcher, BinlogPosition startPosition,
                           IEventHandler handle, ServerCharactorSet serverCharset, IExceptionHandler exceptionHandler,
                           int ringBufferSize, int rowParserThreadNum) {
        this.logFetcher = logFetcher;
        this.serverCharset = serverCharset;
        this.exceptionHandler = exceptionHandler;
        this.startPosition = startPosition;
        this.outputHandler = new OutputHandler(handle, startPosition);
        this.ringBufferSize = ringBufferSize;
        this.rowParserThreadNum = rowParserThreadNum;
    }

    public void setWaitStrategy(RBWaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public void init() {
        initDisruptor(ringBufferSize, rowParserThreadNum);
    }

    /**
     * 初始化Disruptor环形缓冲区及多阶段处理管道。
     * 管道结构：预处理(1线程) -> 并行解析(N线程) -> 顺序输出(1线程)
     */
    public void initDisruptor(int ringBufferSize, int rowParserThreadNum) {
        // 初始化 Disruptor
        WaitStrategy waitStrategyImpl;
        switch (this.waitStrategy) {
        case SLEEP:
            waitStrategyImpl = new SleepingWaitStrategy(50, 100);
            break;
        case BLOCK:
            waitStrategyImpl = new BlockingWaitStrategy();
            break;
        case YIELD:
            waitStrategyImpl = new YieldingWaitStrategy();
            break;
        default:
            throw new PolardbxException("unSupport wait strategy for " + waitStrategy);
        }

        parserDisruptor = new Disruptor<>(
            LogEventWrapper::new,
            ringBufferSize,
            r -> {
                Thread t = new Thread(r, "disruptor-parser-thread");
                t.setDaemon(true);
                return t;
            },
            ProducerType.SINGLE,
            waitStrategyImpl
        );

        RowLogEventHandler[] handlers = new RowLogEventHandler[rowParserThreadNum];
        for (int i = 0; i < rowParserThreadNum; i++) {
            handlers[i] = new RowLogEventHandler(serverCharset, filter);
        }
        logger.info("init parser ringbuffer with buffer size {}, row parser thread num {}", ringBufferSize,
            rowParserThreadNum);
        // 定义多阶段处理逻辑
        // 预处理阶段，执行原逻辑，忽略row event parser
        logEventPreHandler = new LogEventPreHandler();
        logEventPreHandler.getDecode64Enabled().set(decode64Enabled.get());
        parserDisruptor.handleEventsWith(logEventPreHandler).
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
        if (logFetcher != null) {
            try {
                logFetcher.close();
            } catch (IOException e) {
                logger.warn("close log fetcher failed", e);
            }
        }
        if (parserDisruptor != null) {
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

    /**
     * 主解析循环：从Fetcher读取binlog数据，解码后发布到Disruptor管道。
     * 处理流程：fetch -> 表级过滤 -> decode -> 发布到RingBuffer
     */
    public void parse() {
        try {
            logger.info("parser thread started!");
            final LogDecoder decoder = buildDecoder();
            final LogContext context = buildLogContext();
            while (started.get() && logFetcher.fetch()) {
                LogBuffer logBuffer = logFetcher.buffer();

                boolean filtered = true;
                while (filtered) {
                    filtered = logBufferFilter.filter(logBuffer);
                }

                LogEvent event = decoder.decode(logBuffer, context);
                if (event == null || dryRun) {
                    continue;
                }
                if (event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                    logBufferFilter.setFormatDescriptionLogEvent((FormatDescriptionLogEvent) event);
                }
                checker.check();
                // 将 LogEvent 包装为 LogEventWrapper 并提交到 Disruptor
                RingBuffer<LogEventWrapper> ringBuffer = parserDisruptor.getRingBuffer();
                if (event.getHeader().getType() == LogEvent.TRANSACTION_PAYLOAD_EVENT) {
                    // 解压
                    List<LogEvent> eventList = decoder.processIterateDecode(event, context);
                    for (LogEvent innerEvent : eventList) {
                        publishEvent(ringBuffer, innerEvent, context);
                    }
                } else {
                    publishEvent(ringBuffer, event, context);
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

    /**
     * 将解码后的事件发布到Disruptor的RingBuffer中。
     *
     * @deprecated 已被列存优化模式的BatchTaskQueue替代
     */
    @Deprecated
    protected void publishEvent(RingBuffer<LogEventWrapper> ringBuffer, LogEvent event, LogContext context) {
        long sequence = ringBuffer.next();
        try {
            LogEventWrapper wrapper = ringBuffer.get(sequence);
            wrapper.setLogEvent(event);
            wrapper.setLogPosition(context.getLogPosition().clone());
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 设置白名单表集合，同时更新行过滤器和LogBuffer过滤器。
     * 表名格式为 db.table 的小写形式。
     */
    public void setAcceptTable(Set<String> acceptTableSet) {
        this.filter.setAcceptTableSet(acceptTableSet);
        this.logBufferFilter.setAcceptTable(acceptTableSet);
        logger.info("set accept table set {}", JSON.toJSONString(acceptTableSet));
    }

    /**
     * 设置黑名单表集合，同时更新行过滤器和LogBuffer过滤器。
     */
    public void setIgnoreTable(Set<String> ignoreTableSet) {
        this.filter.setIgnoreTableSet(ignoreTableSet);
        this.logBufferFilter.setIgnoreTable(ignoreTableSet);
        logger.info("set ignore table set {}", JSON.toJSONString(ignoreTableSet));
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setDecode64Enabled(boolean enabled) {
        this.decode64Enabled.set(enabled);
        if (logEventPreHandler != null) {
            this.logEventPreHandler.getDecode64Enabled().set(enabled);
        }
    }

}
