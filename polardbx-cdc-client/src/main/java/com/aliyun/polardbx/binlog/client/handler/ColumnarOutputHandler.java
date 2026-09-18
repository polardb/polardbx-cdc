/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRotateEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.CdcEventData;
import com.aliyun.polardbx.binlog.client.ColumnarCdcClient;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.metrics.CdcClientMetricsManager;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 列存场景的事件输出处理器，继承自OutputHandler。
 * <p>
 * 核心职责：
 * 1. 多流输出协调：当parallelism>1时，各Parser的OutputHandler按照binlog文件顺序轮流输出
 * 2. 文件轮转处理：收到RotateEvent时，将输出权转移给下一个Parser
 * 3. 位置跳过：续传时跳过已处理的事件
 * <p>
 * 多流输出顺序示例（parallelism=2）：
 * Parser0输出binlog.000004 -> Parser1输出binlog.000005 -> Parser0输出binlog.000006 -> ...
 */
@Slf4j
public class ColumnarOutputHandler extends OutputHandler {
    protected static final Logger logger = LoggerFactory.getLogger(ColumnarOutputHandler.class);
    /**
     * 总并行度
     */
    private final int parallelism;
    /**
     * 当前OutputHandler的索引编号
     */
    private final int id;
    /**
     * 列存客户端引用，用于多流输出协调
     */
    private final ColumnarCdcClient columnarCdcClient;
    /**
     * 是否启用优化模式
     */
    private final boolean optimize;
    /**
     * 是否需要等待轮到自己输出
     */
    private boolean shouldWait = true;
    /**
     * 当前期望处理的binlog文件名，用于校验文件顺序
     */
    private String expectedFileName;
    /**
     * 追踪标识
     */
    private final String trace;
    /**
     * 停止标志
     */
    private AtomicBoolean stop = new AtomicBoolean();

    public ColumnarOutputHandler(IEventHandler handle,
                                 BinlogPosition startPosition,
                                 int parallelism,
                                 int id,
                                 ColumnarCdcClient columnarCdcClient,
                                 boolean optimize,
                                 String trace) {
        super(handle, startPosition);
        this.parallelism = parallelism;
        this.id = id;
        this.columnarCdcClient = columnarCdcClient;
        this.optimize = optimize;
        this.expectedFileName = startPosition.getFileName();
        this.trace = trace;
        log.info("[{}] Columnar output handler init, expected file is {}, parallelism is {}, id is {}", trace,
            expectedFileName, parallelism, id);
    }

    /**
     * 优化模式的事件输出入口，由output线程调用。
     * 处理逻辑：
     * 1. 位置跳过检查（续传场景）
     * 2. 多流输出等待（轮到自己时才输出）
     * 3. RotateEvent处理（转移输出权给下一个Parser）
     * 4. 将事件推送给下游
     */
    public void output(CdcEventData cdcEventData) {
        if (stop.get()) {
            return;
        }

        LogPosition logPosition = new LogPosition(cdcEventData.getBinlogFileName(), cdcEventData.getPosition());
        this.lastPushLogPosition = logPosition;
        if (!optimize && shouldSkip && shouldSkip(logPosition)) {
            // Already skip right after decode.
            return;
        }

        DBMSEvent eventData = cdcEventData.getEvent();
        if (optimize && eventData == null) {
            // Not pass null event to columnar.
            return;
        }

        // After output all binlog events in the current binlog file,
        // wait for others handlers finishing output.
        while (shouldWait && shouldWait(logPosition, eventData)) {
            synchronized (columnarCdcClient.getLock()) {
                if (shouldWait(logPosition, eventData)) {
                    try {
                        columnarCdcClient.getLock().wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (stop.get()) {
            return;
        }

        if (parallelism > 1 && eventData instanceof DBMSRotateEvent) {
            // Reach the end of current binlog file
            String currentFileName = expectedFileName;
            shouldWait = true;
            // Move to next expected file
            expectedFileName = BinlogFileUtil.getNextBinlogFileName(currentFileName, parallelism);
            synchronized (columnarCdcClient.getLock()) {
                columnarCdcClient.moveToNext();
                log.debug("[{}] Reader {} reaches end of binlog file {}, next file {}, next reader {}", trace, id,
                    currentFileName, expectedFileName, columnarCdcClient.getCurrent());
                columnarCdcClient.getLock().notifyAll();
            }
            // Not pass rotate event to columnar.
            return;
        }

        long begin = System.nanoTime();
        handle.onHandle(cdcEventData);
        long rt = (System.nanoTime() - begin) / 1_000_000;
        CdcClientMetricsManager.getInstance().recordRt(rt);
        CdcClientMetricsManager.getInstance().addEvent(1);
    }

    private boolean shouldSkip(final LogPosition logPosition) {
        if (StringUtils.equalsIgnoreCase(logPosition.getFileName(), startPosition.getFileName()) &&
            logPosition.getPosition() <= startPosition.getPosition()) {
            return true;
        }
        shouldSkip = false;
        return false;
    }

    /**
     * 判断当前是否需要等待轮到自己输出。
     * 同时校验binlog文件名是否符合预期，不符合则抛出异常。
     */
    private boolean shouldWait(LogPosition logPosition, DBMSEvent eventData) {
        if (parallelism > 1 && columnarCdcClient.getCurrent() != id) {
            log.debug("[{}] Check should wait return true, current is {}, log position is {}",
                trace, columnarCdcClient.getCurrent(), logPosition);
            return true;
        }
        // validate file name
        if (parallelism > 1 && !(eventData instanceof DBMSRotateEvent) &&
            !StringUtils.equalsIgnoreCase(logPosition.getFileName(), expectedFileName)) {
            throw new RuntimeException(
                "[" + trace + "] Wrong binlog file, expected " + expectedFileName + ", got " + logPosition.getFileName()
                    + ", my id is " + id + ", current id is " + columnarCdcClient.getCurrent() + ", parallelism is "
                    + parallelism + ", pos is " + logPosition.getPosition());
        }
        log.debug("[{}] Check should wait return false, current log position is {}", trace, logPosition);
        shouldWait = false;
        return false;
    }

    @Deprecated
    @Override
    public void onEvent(LogEventWrapper event, long sequence, boolean endOfBatch) throws Exception {
        List<DBMSEvent> dbmsEventList = event.getDbmsEventList();
        if (dbmsEventList != null) {
            for (DBMSEvent dbmsEvent : dbmsEventList) {
                pushEvent(dbmsEvent, event.getLogPosition());
            }
        }
        event.setLogEvent(null);
        event.setDbmsEventList(null);
    }

    @Deprecated
    private void pushEvent(DBMSEvent eventData, LogPosition logPosition) {
        this.lastPushLogPosition = logPosition;
        if (!optimize && shouldSkip && shouldSkip(logPosition)) {
            // Already skip right after decode.
            return;
        }

        if (optimize && eventData == null) {
            // Not pass null event to columnar.
            return;
        }

        // After output all binlog events in the current binlog file,
        // wait for others handlers finishing output.
        while (shouldWait && shouldWait(logPosition, eventData)) {
            synchronized (columnarCdcClient.getLock()) {
                if (shouldWait(logPosition, eventData)) {
                    try {
                        columnarCdcClient.getLock().wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (parallelism > 1 && eventData instanceof DBMSRotateEvent) {
            // Reach the end of current binlog file
            String currentFileName = expectedFileName;
            shouldWait = true;
            // Move to next expected file
            expectedFileName = BinlogFileUtil.getNextBinlogFileName(currentFileName, parallelism);
            synchronized (columnarCdcClient.getLock()) {
                columnarCdcClient.moveToNext();
                log.debug("[{}] Reader {} reaches end of binlog file {}, next file {}, next reader {}", trace, id,
                    currentFileName, expectedFileName, columnarCdcClient.getCurrent());
                columnarCdcClient.getLock().notifyAll();
            }
            // Not pass rotate event to columnar.
            return;
        }

        long begin = System.currentTimeMillis();
        CdcEventData cdcEventData =
            new CdcEventData(logPosition.getFileName(), logPosition.getPosition(), eventData);
        handle.onHandle(cdcEventData);
        long rt = System.currentTimeMillis() - begin;
        CdcClientMetricsManager.getInstance().recordRt(rt);
        CdcClientMetricsManager.getInstance().addEvent(1);
    }
}
