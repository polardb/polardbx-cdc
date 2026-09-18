/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogTransactionCompressor;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.WorkHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.BEGIN;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.COMMIT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.DML;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.HEARTBEAT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.ROWSQUERY;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TRANSACTION_PAYLOAD;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TSO;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeBegin;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeCommit;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeMarkEvent;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class EventDataBuildHandler implements WorkHandler<EventData>, LifecycleAware {

    private final HandleContext handleContext;
    @Getter
    private final BinlogTransactionCompressor compressor;
    private int curTokenIdx = 0;

    public EventDataBuildHandler(HandleContext handleContext) {
        this.handleContext = handleContext;
        this.compressor = new BinlogTransactionCompressor();
    }

    /**
     * 从@link{ParallelWriter}中的RingBuffer中取数据并处理。
     * 由于并发压缩的存在，这里每个事件的nextPos字段并不准确，会在Sink的时候统一处理。
     */
    @Override
    public void onEvent(EventData event) {
        try {
            if (!handleContext.getRunning().get()) {
                throw new InterruptedException();
            }

            EventToken eventToken = event.getEventToken();
            curTokenIdx = 0;
            if (eventToken instanceof BatchEventToken) {
                compressor.init(event);
                BatchEventToken batchEventToken = (BatchEventToken) event.getEventToken();
                AtomicInteger offset = new AtomicInteger(0);
                batchEventToken.getTokens().forEach(t -> {
                    // 检测到事务压缩开始
                    if (t.getType() == BEGIN && t.isUseCompression()) {
                        compressor.prepare(offset.get(), t.getCompressionType(), curTokenIdx, t.getCompressionLevel());
                    }

                    // 如果在压缩过程中
                    if (compressor.isPrepared()) {
                        // compressor.setEndPosOffset(compressor.getEndPosOffset() - 4);
                        if (t.getType() != HEARTBEAT) {
                            // 4是checksum长度，压缩中的事件不需要
                            t.setLength(t.getLength() - 4);
                            // 压缩中的事件该字段没有意义,统一为0还能保证重建时事件大小与重建前保持一致
                            t.setNextPosition(0);
                        }
                    }

                    // t.setNextPosition(t.getNextPosition() + compressor.getEndPosOffset());
                    processSingleEventToken(event, t, offset);
                    curTokenIdx++;
                });
                // 清理未压缩的原始tokens
                compressor.cleanEventTokens();
            } else if (eventToken instanceof SingleEventToken) {
                processSingleEventToken(event, (SingleEventToken) event.getEventToken(), new AtomicInteger(0));
            } else {
                throw new PolardbxException("unsupported event token " + eventToken.getClass().getName());
            }

        } catch (Throwable t) {
            PolardbxException exception = new PolardbxException("error occurred when build event data.", t);
            handleContext.setException(exception);
            throw exception;
        }
    }

    private void processSingleEventToken(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        SingleEventToken.Type type = eventToken.getType();
        if (type == BEGIN) {
            buildBegin(eventData, eventToken, offset);
        } else if (type == DML) {
            buildDml(eventData, eventToken, offset);
        } else if (type == ROWSQUERY) {
            buildRowsQuery(eventData, eventToken, offset);
        } else if (type == COMMIT) {
            buildCommit(eventData, eventToken, offset);
            // 压缩事务,若compressor未准备好说明未开启压缩或者事务巨大
            if (compressor.isPrepared()) {
                compressor.compress(eventToken, curTokenIdx);
                offset.set(compressor.getCompressionBegin());
            }
        } else if (type == TSO) {
            buildTso(eventData, eventToken, offset);
        } else if (type == HEARTBEAT) {
            //do nothing
        } else if (type == TRANSACTION_PAYLOAD) {
            buildTransactionPayload(eventData, eventToken);
        } else {
            throw new RuntimeException("invalid event token type " + type);
        }
    }

    private void buildBegin(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        // 压缩中的event不需要更新checksum
        Pair<byte[], Integer> begin = makeBegin(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getNextPosition(), eventData.getAutoExpandByteArray(), offset.get(), !compressor.isPrepared());
        if (!compressor.isPrepared()) {
            EventGenerator.updateChecksum(begin.getLeft(), offset.get(), begin.getRight());
        }
        eventToken.checkLength(begin.getRight());
        eventToken.setOffset(offset.getAndAdd(begin.getRight()));
    }

    private void buildDml(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        byte[] data = eventToken.getData();
        EventGenerator.updateServerId(data, eventToken.getServerId());
        EventGenerator.updatePos(data, eventToken.getNextPosition());
        // DML在一个事务中且开启压缩，此时将dml的token data也写到大的data buffer中做缓存，待之后压缩使用
        if (compressor.isPrepared()) {
//            if (eventToken.getTableId() != null && !eventToken.getTableId().isEmpty()) {
//                compressor.updateTableIdMap(eventToken.getTableIdMapKey().get(0), eventToken.getTableId().get(0));
//            }
            EventGenerator.updateEventSize(data, data.length - 4);
            ByteArray byteArray = eventData.getAutoExpandByteArray();
            byteArray.setPos(offset.get());
            // 压缩事务内事件可以忽略checksum
            byteArray.write(data, data.length - 4);
            offset.addAndGet(data.length - 4);
        } else {
            eventToken.setUseTokenData(true);
            EventGenerator.updateChecksum(data, 0, data.length);
            eventToken.checkLength(data.length);
        }
    }

    private void buildTransactionPayload(EventData eventData, SingleEventToken eventToken) {
        byte[] data = eventToken.getData();
        EventGenerator.updateServerId(data, eventToken.getServerId());
        EventGenerator.updatePos(data, eventToken.getNextPosition());
        EventGenerator.updateChecksum(data, 0, data.length);
        eventToken.setUseTokenData(true);
        eventToken.checkLength(data.length);
    }

    private void buildRowsQuery(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        Pair<byte[], Integer> rowsQueryEvent = makeMarkEvent(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getRowsQuery(), eventToken.getNextPosition(), eventData.getAutoExpandByteArray(), offset.get(),
            !compressor.isPrepared());

        // 压缩中的event不需要更新checksum
        if (!compressor.isPrepared()) {
            EventGenerator.updateChecksum(rowsQueryEvent.getLeft(), offset.get(), rowsQueryEvent.getRight());
        }
        eventToken.checkLength(rowsQueryEvent.getRight());
        eventToken.setOffset(offset.getAndAdd(rowsQueryEvent.getRight()));
    }

    private void buildCommit(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        final Pair<byte[], Integer> commit = makeCommit(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getXid(), eventToken.getNextPosition(), eventData.getAutoExpandByteArray(), offset.get(),
            !compressor.isPrepared());

        // 压缩中的event不需要更新checksum
        if (!compressor.isPrepared()) {
            EventGenerator.updateChecksum(commit.getLeft(), offset.get(), commit.getRight());
        }
        eventToken.checkLength(commit.getRight());
        eventToken.setOffset(offset.getAndAdd(commit.getRight()));
    }

    private void buildTso(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        final Pair<byte[], Integer> tsoEvent = makeMarkEvent(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getCts(), eventToken.getNextPosition(), eventData.getAutoExpandByteArray(), offset.get(),
            !compressor.isPrepared());

        // 压缩中的event不需要更新checksum
        if (!compressor.isPrepared()) {
            EventGenerator.updateChecksum(tsoEvent.getLeft(), offset.get(), tsoEvent.getRight());
        }
        eventToken.checkLength(tsoEvent.getRight());
        eventToken.setOffset(offset.getAndAdd(tsoEvent.getRight()));
    }

    @Override
    public void onStart() {
        log.info("{} started", getClass().getSimpleName());
    }

    @Override
    public void onShutdown() {
        log.info("{} shutdown", getClass().getSimpleName());
    }
}
