/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.ParallelLogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.CdcClientParser;
import com.aliyun.polardbx.binlog.client.CdcEventData;
import com.aliyun.polardbx.binlog.client.ColumnarCdcClient;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.client.concurrent.BatchTask;
import com.aliyun.polardbx.binlog.client.concurrent.BatchTaskQueue;
import com.aliyun.polardbx.binlog.client.concurrent.LockFreeQueue;
import com.aliyun.polardbx.binlog.client.error.PolardbxClientException;
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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 列存场景的CDC客户端解析器，继承自CdcClientParser，增加了并行读取和优化模式支持。
 * <p>
 * 优化模式(optimize=true)的处理管道：
 * 1. 主解析线程：fetch -> 过滤 -> decode -> 预处理 -> 放入batchQueue
 * 2. batch线程：从batchQueue批量取出 -> 提交给parser线程池并行解析行事件
 * 3. output线程：按顺序从 parseQueue 取出已完成的批次，输出给下游
 * <p>
 * 传统模式(optimize=false)使用Disruptor RingBuffer，已废弃。
 */
@Slf4j
public class ColumnarCdcClientParser extends CdcClientParser {
    /**
     * 列存专用的输出处理器，支持多流轮转输出
     */
    private final ColumnarOutputHandler columnarOutputHandler;
    /**
     * 总并行度
     */
    private final int parallelism;
    /**
     * 当前Parser的索引编号
     */
    private final int id;
    /**
     * 是否启用优化模式（无锁队列替代Disruptor）
     */
    private final boolean optimize;
    /**
     * 追踪标识，用于日志中区分不同的Parser实例
     */
    private final String trace;
    /**
     * Dry run config.
     */
    @Setter
    private boolean dryRunDecode = false;
    @Setter
    private boolean dryRunParse = false;
    /**
     * 是否应该跳过当前事件（用于在续传时跳过已处理的事件）
     */
    private boolean shouldSkip = true;
    /**
     * eps 监控回调
     */
    @Setter
    private Consumer<Long> addBinlogEventThroughput = null;
    /**
     * bps 监控回调
     */
    @Setter
    private Consumer<Long> addBinlogEventSize = null;
    /**
     * 无锁队列，主解析线程将预处理后的事件放入此队列
     */
    private LockFreeQueue<CdcEventData> batchQueue = null;
    /**
     * 批量任务队列，batch线程将批量事件提交给parser线程池后放入此队列，output线程按序取出
     */
    private BatchTaskQueue<CdcEventData> parseQueue = null;
    /**
     * 每批的事件数量
     */
    private int batchSize = -1;
    /**
     * 行事件解析线程池
     */
    private ExecutorService parserThreadPool = null;
    /**
     * 预处理器，处理非行事件（DDL/心跳/事务边界等）
     */
    private ColumnarLogEventPreHandler preHandler = null;
    /**
     * 行事件解析器
     */
    private RowLogEventHandler rowLogEventHandler = null;
    /**
     * batch线程：从batchQueue批量取事件并提交给parser线程池
     */
    private Thread batchThread = null;
    /**
     * output线程：按顺序取出已完成的批次并输出给下游
     */
    private Thread outputThread = null;
    /**
     * binlog解析上下文，包含当前解析位置和字符集等信息
     */
    private final LogContext context;
    /**
     * 列存客户端引用，用于多流输出协调和rename table后重置Parser
     */
    private final ColumnarCdcClient columnarCdcClient;

    public ColumnarCdcClientParser(StreamObserverLogFetcher logFetcher,
                                   BinlogPosition startPosition,
                                   IEventHandler handle,
                                   ServerCharactorSet serverCharset,
                                   IExceptionHandler exceptionHandler,
                                   int ringBufferSize,
                                   int rowParserThreadNum,
                                   ColumnarCdcClient columnarCdcClient,
                                   int parallelism,
                                   int id,
                                   boolean optimize,
                                   String trace,
                                   int parseQueueSize) {
        super(logFetcher, startPosition, handle, serverCharset, exceptionHandler, ringBufferSize, rowParserThreadNum);
        this.columnarOutputHandler =
            new ColumnarOutputHandler(handle, startPosition, parallelism, id, columnarCdcClient, optimize, trace);
        this.parallelism = parallelism;
        this.id = id;
        this.optimize = optimize;
        this.trace = trace;
        if (optimize) {
            configParseQueue(parseQueueSize);
        }
        this.context = buildLogContext();
        this.columnarCdcClient = columnarCdcClient;
    }

    /**
     * 配置优化模式下的队列。
     * batchQueue: 主解析线程 -> batch线程的无锁队列
     * parseQueue: batch线程 -> output线程的批量任务队列
     */
    public void configParseQueue(int batchSize) {
        this.batchQueue = new LockFreeQueue<>(ringBufferSize);
        this.parseQueue = new BatchTaskQueue<>(rowParserThreadNum << 1);
        this.batchSize = batchSize;
    }

    @Override
    public void init() {
        if (optimize) {
            initParseQueue(rowParserThreadNum);
        } else {
            // legacy way
            initDisruptor(ringBufferSize, rowParserThreadNum);
        }
    }

    @Deprecated
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
                Thread t = new Thread(r, "disruptor-parser-thread-" + id);
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
        logger.info("[{}] init parser ringbuffer with buffer size {}, row parser thread num {}", trace, ringBufferSize,
            rowParserThreadNum);
        // 定义多阶段处理逻辑
        // 预处理阶段，执行原逻辑，忽略row event parser
        ColumnarLogEventPreHandler columnarLogEventPreHandler = new ColumnarLogEventPreHandler();
        columnarLogEventPreHandler.getDecode64Enabled().set(decode64Enabled.get());
        parserDisruptor.handleEventsWith(columnarLogEventPreHandler)
            // 多线程并行 parser event
            .handleEventsWithWorkerPool(handlers)
            // 按顺序输出下游
            .then(columnarOutputHandler);

        SimpleExceptionHandler seh = new SimpleExceptionHandler();
        parserDisruptor.setDefaultExceptionHandler(seh);
        checker = seh;
        // 启动 Disruptor
        parserDisruptor.start();
        scheduledExecutorService.scheduleAtFixedRate(this::print, INTERVAL, INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * 初始化优化模式的解析管道。
     * 启动batch线程和output线程，初始化parser线程池。
     */
    private void initParseQueue(int rowParserThreadNum) {
        logger.info("[{}] init parser queue with row parser thread num {}", trace,
            rowParserThreadNum);
        ClientExceptionHandler chc = new ClientExceptionHandler();
        this.checker = chc;
        // row parser thread pool
        this.parserThreadPool = Executors.newFixedThreadPool(rowParserThreadNum);
        // batch thread: batch events
        this.batchThread = new Thread(() -> batch(chc), "cdc-client-batch-thread-" + id);
        // output thread: output DBMSEvent to columnar
        this.outputThread = new Thread(() -> output(chc), "cdc-client-output-thread-" + id);
        this.preHandler = new ColumnarLogEventPreHandler();
        this.preHandler.getDecode64Enabled().set(decode64Enabled.get());
        this.rowLogEventHandler = new RowLogEventHandler(serverCharset, logBufferFilter.isEnabled() ? null : filter);

        this.batchThread.start();
        this.outputThread.start();
        logger.info("[{}] start batch thread and output thread", trace);
    }

    public void print() {
        logger.info("[{}] ringbuffer queue remain data size : {}", trace,
            parserDisruptor.getRingBuffer().getBufferSize() - parserDisruptor.getRingBuffer().remainingCapacity());
    }

    @Override
    public LogDecoder buildDecoder() {
        LogDecoder decoder =
            new ParallelLogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT, trace, parallelism);
        decoder.setNeedRecordData(false);
        return decoder;
    }

    /**
     * 列存场景的主解析循环。
     * 处理流程：
     * 1. fetch binlog数据
     * 2. LogBuffer级别表过滤
     * 3. decode解码
     * 4. 位置跳过检查（续传时跳过已处理的事件）
     * 5. 发布到处理管道（Disruptor或BatchTaskQueue）
     */
    public void parse() {
        try {
            final LogDecoder decoder = buildDecoder();
            logger.info("[{}] parser thread {} started, init file name {}, init pos {} !",
                trace, id, context.getLogPosition().getFileName(), context.getLogPosition().getPosition());
            logBufferFilter.setAddBinlogEventSize(addBinlogEventSize);
            logBufferFilter.setAddBinlogEventThroughput(addBinlogEventThroughput);
            logger.info("[{}] logBufferFilter:{} ", trace, logBufferFilter);
            while (started.get() && logFetcher.fetch()) {
                if (!started.get()) {
                    logger.warn("[{}] parser thread {} stopped!", trace, id);
                    return;
                }

                if (dryRun && !dryRunDecode) {
                    // For dry run sync but not decode
                    logFetcher.buffer().consume(logFetcher.buffer().limit());
                    continue;
                }

                LogBuffer logBuffer = logFetcher.buffer();

                boolean filtered = true;
                while (filtered) {
                    filtered = logBufferFilter.filter(logBuffer);
                }

                LogEvent event = decoder.decode(logFetcher.buffer(), context);
                if (event == null) {
                    continue;
                }
                if (event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                    logBufferFilter.setFormatDescriptionLogEvent((FormatDescriptionLogEvent) event);
                }
                if (dryRun && !dryRunParse) {
                    // for dry run decode but not parse
                    logFetcher.buffer().consume(event.getEventLen());
                    continue;
                }

                checker.check();

                if (null != addBinlogEventThroughput) {
                    addBinlogEventThroughput.accept(1L);
                }
                if (null != addBinlogEventSize) {
                    addBinlogEventSize.accept((long) event.getEventLen());
                }

                // Skip this event if current position < start position.
                if (optimize && shouldSkip && event.getHeader().getType() != LogEvent.ROTATE_EVENT
                    && shouldSkip(context.getLogPosition())) {
                    continue;
                }

                // 将 LogEvent 包装为 LogEventWrapper 并提交到 Disruptor
                if (!optimize) {
                    // Deprecated, will be removed in future
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
                } else {
                    if (event.getHeader().getType() == LogEvent.TRANSACTION_PAYLOAD_EVENT) {
                        List<LogEvent> eventList = decoder.processIterateDecode(event, context);
                        for (LogEvent innerEvent : eventList) {
                            putEventIntoBatchQueue(innerEvent, context);
                        }
                    } else {
                        putEventIntoBatchQueue(event, context);
                    }
                }
            }
        } catch (Throwable e) {
            if (!started.get()) {
                logger.warn("[{}] parser thread {} stopped, and get exception {}!", trace, id, e.getMessage());
                return;
            }
            if (exceptionHandler != null) {
                exceptionHandler.handle(e);
            } else {
                logger.error("[{}] exceptionHandler is null , parser event failed!", trace, e);
                throw new PolardbxClientException("exceptionHandler is null , parser event failed!", e);
            }
        } finally {
            started.set(false);
        }

    }

    /**
     * 判断当前位置是否应该跳过。
     * 在续传场景下，跳过startPosition之前的所有事件。
     * 一旦超过startPosition，将shouldSkip置为false，后续不再检查。
     */
    private boolean shouldSkip(final LogPosition logPosition) {
        if (StringUtils.equalsIgnoreCase(logPosition.getFileName(), startPosition.getFileName()) &&
            logPosition.getPosition() <= startPosition.getPosition()) {
            return true;
        }
        shouldSkip = false;
        return false;
    }

    private String getLogBufferInfo(LogBuffer logBuffer) {
        return String.format("logBuffer{pos:%s, origin:%s, limit:%s}", logBuffer.position(), logBuffer.getOrigin(),
            logBuffer.limit());
    }

    /**
     * 将事件放入无锁队列，等待batch线程消费。
     * 对于DDL事件，需要阻塞等待下游处理完成，
     * 并根据需要重置其他并行Parser（因为DDL可能改变表结构）。
     */
    private void putEventIntoBatchQueue(LogEvent innerEvent, LogContext context) throws Exception {
        CdcEventData cdcEventData = new CdcEventData();
        cdcEventData.setLogEvent(innerEvent);
        cdcEventData.setBinlogFileName(context.getLogPosition().getFileName());
        cdcEventData.setPosition(context.getLogPosition().getPosition());

        preHandler.preHandle(cdcEventData);
        batchQueue.put(cdcEventData);
        if (cdcEventData.getEvent() instanceof DefaultQueryLog) {
            logger.info("[{}] start handling ddl event: {}", trace,
                ((DefaultQueryLog) cdcEventData.getEvent()).getQuery().trim());
            // Block the following binlog events until the ddl is done.
            while (!((DefaultQueryLog) cdcEventData.getEvent()).isCompleted()) {
                // using sleep to poll
                Thread.sleep(100);
                if (!started.get()) {
                    return;
                }
            }
            logger.info("[{}] finish handling ddl event: {}", trace,
                ((DefaultQueryLog) cdcEventData.getEvent()).getQuery().trim());
            if (((DefaultQueryLog) cdcEventData.getEvent()).isShouldResetParser()) {
                logger.info("[{}] reset other parser, ddl handling id {}", trace, id);
                columnarCdcClient.resetParserAfterDdl(id);
            }
        }
    }

    /**
     * batch线程的主循环。
     * 从batchQueue中批量取出事件，组装成BatchTask后提交给parser线程池并行解析行事件。
     * 每个BatchTask完成后会被标记为completed，output线程会按顺序取出。
     */
    private void batch(ClientExceptionHandler chc) {
        logger.info("[{}] columnar cdc client start batch...", trace);
        try {
            while (true) {
                if (!started.get()) {
                    return;
                }

                // make a batch task
                BatchTask<CdcEventData> batchTask = parseQueue.put(batchSize);
                if (null == batchTask) {
                    throw new RuntimeException("batchTask is null");
                }

                // get batch from queue
                int cnt = 0;
                CdcEventData cdcEventData = batchQueue.take(false);
                if (cdcEventData == null) {
                    // queue is empty, block wait
                    cdcEventData = batchQueue.take(true);
                }

                if (!started.get()) {
                    return;
                }

                if (cdcEventData == null) {
                    throw new RuntimeException("cdcEventData is null after block wait");
                }

                do {
                    cnt++;
                    batchTask.put(cdcEventData);
                } while (cnt < batchSize && (cdcEventData = batchQueue.take(false)) != null);

                // submit batch task
                parserThreadPool.submit(() -> batchTask.handle(event -> {
                    try {
                        rowLogEventHandler.handle(event);
                    } catch (Throwable t) {
                        chc.setThrowable(t);
                        throw new RuntimeException(t);
                    }
                }));
            }
        } catch (Throwable t) {
            logger.error("[{}] columnar cdc client batch failed...", trace, t);
            chc.setThrowable(t);
            throw new RuntimeException(t);
        }
    }

    /**
     * output线程的主循环。
     * 按插入顺序从 parseQueue 中取出已完成的BatchTask，
     * 通过ColumnarOutputHandler输出给下游，保证事件的全局有序性。
     */
    private void output(ClientExceptionHandler chc) {
        logger.info("[{}] columnar cdc client start output...", trace);
        try {
            while (true) {
                if (!started.get()) {
                    logger.info("[{}] columnar cdc client output not started, return...", trace);
                    return;
                }
                parseQueue.take(columnarOutputHandler::output);
            }
        } catch (Throwable t) {
            logger.error("[{}] columnar cdc client output failed...", trace, t);
            chc.setThrowable(t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public LogPosition getLogPosition() {
        return columnarOutputHandler.getLastPushLogPosition();
    }

    @Override
    public void stop() {
        super.stop();
        if (batchThread != null) {
            batchThread.interrupt();
        }
        if (outputThread != null) {
            outputThread.interrupt();
        }
        if (parserThreadPool != null) {
            parserThreadPool.shutdownNow();
        }
    }
}
