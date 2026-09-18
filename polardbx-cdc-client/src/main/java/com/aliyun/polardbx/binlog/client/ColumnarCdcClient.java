/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DumperConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.handler.ColumnarCdcClientParser;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 列存场景的CDC客户端，支持多流并行读取binlog文件。
 * <p>
 * 并行架构说明：
 * - 每个并行度(parallelism)对应一个独立的DumperDataSource和Parser线程
 * - 各Parser交替读取不同的binlog文件（如parallelism=2时，Parser0读取偶数号文件，Parser1读取奇数号文件）
 * - 通过ColumnarOutputHandler的轮转机制保证最终输出的全局有序性
 * <p>
 * 支持两种解析模式：
 * - optimize=false：传统的Disruptor RingBuffer模式
 * - optimize=true：基于无锁队列(LockFreeQueue) + BatchTaskQueue的优化模式
 */
@Slf4j
public class ColumnarCdcClient extends CdcClient {
    /**
     * 并行度，即同时读取binlog的线程数
     */
    private final int parallelism;
    /**
     * 各并行线程对应的Dumper数据源，负责从Dumper服务拉取binlog流
     */
    private final DumperDataSource[] dumperDataSources;
    /**
     * 各并行线程对应的解析器
     */
    @Setter
    private ColumnarCdcClientParser[] cdcClientParsers;
    /**
     * 各并行线程的解析线程引用
     */
    private final Thread[] parseThreads;

    /**
     * 是否启用优化模式（无锁队列替代Disruptor）
     */
    private final boolean optimize;
    /**
     * 多流输出协调锁，用于保证各并行线程轮流输出时的同步
     */
    @Getter
    private final Object lock = new Object();
    /**
     * 当前应该输出的Parser索引，各Parser轮流输出时通过此值协调顺序
     */
    private final AtomicInteger current = new AtomicInteger(0);

    /**
     * 下载窗口大小，控制并发下载的文件数
     */
    protected int windowSize = -1;
    /**
     * 每个文件的下载并行度
     */
    protected int parallelismPerFile = -1;
    /**
     * 分片下载的分片大小
     */
    protected long partSize = -1;
    /**
     * 批量任务队列的批次大小
     */
    protected int batchSize = 64;

    /**
     * 吞吐量监控回调，用于统计处理的事件数
     */
    @Setter
    private Consumer<Long> addBinlogEventThroughput = null;
    /**
     * 数据量监控回调，用于统计处理的字节数
     */
    @Setter
    private Consumer<Long> addBinlogEventSize = null;

    /**
     * Dry run config.
     */
    @Setter
    private boolean dryRunDecode = false;
    @Setter
    private boolean dryRunParse = false;
    /**
     * 下游事件处理器引用，用于DDL后重置Parser时传递给新建的Parser
     */
    private IEventHandler outputHandle;

    public ColumnarCdcClient(IMetaDBDataSourceProvider provider,
                             int windowSize,
                             int parallelismPerFile,
                             long partSize,
                             int parallelism,
                             boolean optimize,
                             int parseQueueSize) {
        super();
        this.metaDbHelper = new MetaDbHelper(provider);
        this.parallelism = parallelism;
        this.optimize = optimize;
        dumperDataSources = new DumperDataSource[parallelism];
        cdcClientParsers = new ColumnarCdcClientParser[parallelism];
        parseThreads = new Thread[parallelism];
        this.windowSize = windowSize;
        this.parallelismPerFile = parallelismPerFile;
        this.partSize = partSize;
        this.batchSize = parseQueueSize;
        for (int i = 0; i < parallelism; i++) {
            dumperDataSources[i] = new DumperDataSource(metaDbHelper, true);
        }
    }

    /**
     * 启动并行binlog dump。
     * 根据并行度创建多个Parser，每个Parser负责读取一部分binlog文件。
     * 例如parallelism=2时，Parser0读取binlog.000004、000006，Parser1读取binlog.000005、000007。
     */
    @Override
    protected void startDump(String binlogFileName, IEventHandler outputHandle)
        throws Exception {
        this.outputHandle = outputHandle;
        if (!startPosition.getFileName().equalsIgnoreCase(binlogFileName)) {
            throw new PolardbxException("binlog file name not match, binlog file name: "
                + binlogFileName + ", start file name: " + startPosition.getFileName());
        }
        log.info("start columnar client parallel, parallelism: {}.", parallelism);
        for (int i = 0; i < parallelism; i++) {
            BinlogPosition actualStartPosition;
            String actualStartFileName = BinlogFileUtil.getNextBinlogFileName(binlogFileName, i);
            if (i == 0) {
                actualStartPosition = startPosition;
            } else {
                actualStartPosition = new BinlogPosition(actualStartFileName, 4, -1, -1);
            }

            configColumnarParser(outputHandle, i, actualStartFileName, actualStartPosition);
        }
    }

    /**
     * 配置并启动单个并行Parser。
     * 包括创建Dumper连接、初始化解析器、设置过滤规则、启动解析线程。
     */
    private void configColumnarParser(IEventHandler outputHandle, int i, String startFileName,
                                      BinlogPosition startPosition) throws Exception {
        Map<String, String> ext = new HashMap<>();
        String trace = UUID.randomUUID().toString();
        ext.put(DumperConfigKeys.PARALLELISM, String.valueOf(parallelism));
        ext.put(DumperConfigKeys.PARALLELISM_ID, String.valueOf(i));
        ext.put(DumperConfigKeys.CLIENT_TRACE_MARK, trace);
        if (windowSize > 0) {
            ext.put(ConfigKeys.BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE, String.valueOf(windowSize));
        }
        if (parallelismPerFile > 0) {
            ext.put(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE, String.valueOf(parallelismPerFile));
        }
        if (partSize > 0) {
            ext.put(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PART_SIZE, String.valueOf(partSize));
        }
        dumperDataSources[i].initCharset();
        dumperDataSources[i].reConnect(flowControlWindow);
        StreamObserverLogFetcher logFetcher = providerLogFetcher();
        dumperDataSources[i].dump(new BinlogPosition(startFileName, 4, -1, -1), logFetcher, ext);
        logger.info("[{}] parallel dump {} start dump file {}!", trace, i, startFileName);
        cdcClientParsers[i] =
            new ColumnarCdcClientParser(logFetcher, startPosition, outputHandle,
                dumperDataSources[i].getServerCharset(), exceptionHandler, ringBufferSize, rowParseThreadNum,
                this, parallelism, i, optimize, trace, batchSize);
        // For dry run
        if (dryRun) {
            log.warn("start parallel dry run, dry run decode {}, dry run parse {}", dryRunDecode, dryRunParse);
        }
        cdcClientParsers[i].setDryRun(dryRun);
        cdcClientParsers[i].setDryRunDecode(dryRunDecode);
        cdcClientParsers[i].setDryRunParse(dryRunParse);
        cdcClientParsers[i].setAddBinlogEventThroughput(addBinlogEventThroughput);
        cdcClientParsers[i].setAddBinlogEventSize(addBinlogEventSize);

        cdcClientParsers[i].setWaitStrategy(waitStrategy);
        cdcClientParsers[i].setDecode64Enabled(decode64Enabled);
        cdcClientParsers[i].init();
        if (allowOrIgnoreTables != null) {
            if (whiteListMode) {
                cdcClientParsers[i].setAcceptTable(allowOrIgnoreTables);
            } else {
                cdcClientParsers[i].setIgnoreTable(allowOrIgnoreTables);
            }
        }
        cdcClientParsers[i].logBufferFilter.setEnabled(filterOptimizeEnabled);
        final int tmpI = i;
        parseThreads[i] = new Thread(() -> {
            try {
                cdcClientParsers[tmpI].parse();
            } finally {
                dumperDataSources[tmpI].releaseChannel();
            }
        }, "parser-thread-" + tmpI);
        parseThreads[i].start();
    }

    /**
     * 获取当前应该输出的Parser索引
     */
    public int getCurrent() {
        return current.get();
    }

    /**
     * 将当前输出权转移给下一个Parser，循环轮转。
     * 由ColumnarOutputHandler在处理完Rotate事件后调用。
     */
    public void moveToNext() {
        synchronized (lock) {
            int next = (current.get() + 1) % parallelism;
            current.set(next);
        }
    }

    @Override
    public StreamObserverLogFetcher providerLogFetcher() throws IOException {
        StreamObserverLogFetcher fetcher = new StreamObserverFileLogFetcher();
        fetcher.registerErrorHandle((t) -> {
            if (exceptionHandler != null) {
                exceptionHandler.handle(t);
            }
        });
        return fetcher;
    }

    @Override
    public void setExceptionHandler(IExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        for (int i = 0; i < parallelism; i++) {
            this.dumperDataSources[i].setExceptionHandler(exceptionHandler);
        }
    }

    @Override
    public void startAsync(IEventHandler handle) throws Exception {
        BinlogPosition position = dumperDataSources[0].findStartPosition(flowControlWindow);
        this.startAsync(position.getFileName(), position.getPosition(), handle);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        started.set(false);
        for (int i = 0; i < parallelism; i++) {
            if (dumperDataSources[i] != null) {
                dumperDataSources[i].releaseChannel();
            }
            if (cdcClientParsers[i] != null) {
                cdcClientParsers[i].stop();
            }
            if (parseThreads[i] != null) {
                parseThreads[i].interrupt();
            }
        }
    }

    @Override
    public LogPosition getLogPosition() {
        return cdcClientParsers[0].getLogPosition();
    }

    /**
     * 设置需要关心的数据Set
     * 每个值都是db.table 的小写形式
     */
    @Override
    public void setAcceptTable(Set<String> acceptTableSet) {
        this.allowOrIgnoreTables = acceptTableSet;
        this.whiteListMode = true;
        for (int i = 0; i < parallelism; i++) {
            // 调用该方法时，sync可能还没开始，也就是说parser还没初始化，因此加个!null的判断
            if (cdcClientParsers[i] != null) {
                cdcClientParsers[i].setAcceptTable(acceptTableSet);
            }
        }
    }

    @Override
    public void setIgnoreTable(Set<String> ignoreTableSet) {
        this.allowOrIgnoreTables = ignoreTableSet;
        this.whiteListMode = false;
        for (int i = 0; i < parallelism; i++) {
            if (cdcClientParsers[i] != null) {
                cdcClientParsers[i].setIgnoreTable(ignoreTableSet);
            }
        }
    }

    @Override
    public void setFilterOptimizeEnabled(boolean enabled) {
        this.filterOptimizeEnabled = enabled;
        for (int i = 0; i < parallelism; i++) {
            if (cdcClientParsers[i] != null) {
                cdcClientParsers[i].logBufferFilter.setEnabled(enabled);
            }
        }
    }

    @Override
    public void setDecode64Enabled(boolean enabled) {
        this.decode64Enabled = enabled;
        for (int i = 0; i < parallelism; i++) {
            if (cdcClientParsers[i] != null) {
                cdcClientParsers[i].setDecode64Enabled(enabled);
            }
        }
    }

    /**
     * Reset all threads, except the ddl thread.
     */
    /**
     * DDL后重置其他并行解析线程。
     * DDL可能会引起表结构变化，需要重建Dumper连接和Parser以使用最新的schema。
     * 注意：仅重置非当前DDL处理线程的Parser。
     */
    public void resetParserAfterDdl(int ddlHandleThreadId) throws Exception {
        for (int i = 0; i < parallelism; i++) {
            if (i != ddlHandleThreadId) {
                LogPosition logPosition = cdcClientParsers[i].getLogPosition();
                BinlogPosition startPosition = new BinlogPosition(logPosition.getFileName(), 4, -1, -1);
                dumperDataSources[i].setExceptionHandler(
                    (t) -> logger.error("resetting parser after ddl, only printing error but not throwing", t));
                dumperDataSources[i].releaseChannel();
                dumperDataSources[i] = null;
                dumperDataSources[i] = new DumperDataSource(metaDbHelper, true);
                cdcClientParsers[i].stop();
                cdcClientParsers[i] = null;
                parseThreads[i].interrupt();
                parseThreads[i] = null;
                configColumnarParser(this.outputHandle, i, startPosition.getFileName(), startPosition);
            }
        }
    }
}
