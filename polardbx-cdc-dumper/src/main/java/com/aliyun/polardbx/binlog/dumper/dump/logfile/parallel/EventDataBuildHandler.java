/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.WorkHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.BEGIN;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.COMMIT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.DML;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.HEARTBEAT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.ROWSQUERY;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TSO;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeBegin;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeCommit;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeMarkEvent;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeRowsQuery;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class EventDataBuildHandler implements WorkHandler<EventData>, LifecycleAware {

    private final HandleContext handleContext;

    public EventDataBuildHandler(HandleContext handleContext) {
        this.handleContext = handleContext;
    }

    @Override
    public void onEvent(EventData event) {
        try {
            if (!handleContext.getRunning().get()) {
                throw new InterruptedException();
            }

            EventToken eventToken = event.getEventToken();
            if (eventToken instanceof BatchEventToken) {
                BatchEventToken batchEventToken = (BatchEventToken) event.getEventToken();
                AtomicInteger offset = new AtomicInteger(0);
                batchEventToken.getTokens().forEach(t -> {
                    processSingleEventToken(event, t, offset);
                });
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
            buildDml(eventData, eventToken);
        } else if (type == ROWSQUERY) {
            buildRowsQuery(eventData, eventToken, offset);
        } else if (type == COMMIT) {
            buildCommit(eventData, eventToken, offset);
        } else if (type == TSO) {
            buildTso(eventData, eventToken, offset);
        } else if (type == HEARTBEAT) {
            //do nothing
        } else {
            throw new RuntimeException("invalid event token type " + type);
        }
    }

    private void buildBegin(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        Pair<byte[], Integer> begin = makeBegin(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getNextPosition(), eventData.getData(), offset.get());
        EventGenerator.updateChecksum(begin.getLeft(), offset.get(), begin.getRight());
        eventToken.checkLength(begin.getRight());
        eventToken.setOffset(offset.getAndAdd(begin.getRight()));
    }

    private void buildDml(EventData eventData, SingleEventToken eventToken) {
        byte[] data = eventToken.getData();
        EventGenerator.updateServerId(data, eventToken.getServerId());
        EventGenerator.updatePos(data, eventToken.getNextPosition());
        EventGenerator.updateChecksum(data, 0, data.length);
        eventToken.setUseTokenData(true);
        eventToken.checkLength(data.length);
    }

    private void buildRowsQuery(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        Pair<byte[], Integer> rowsQueryEvent = makeRowsQuery(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getRowsQuery(), eventToken.getNextPosition(), eventData.getData(), offset.get());
        EventGenerator.updateChecksum(rowsQueryEvent.getLeft(), offset.get(), rowsQueryEvent.getRight());
        eventToken.checkLength(rowsQueryEvent.getRight());
        eventToken.setOffset(offset.getAndAdd(rowsQueryEvent.getRight()));
    }

    private void buildCommit(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        final Pair<byte[], Integer> commit = makeCommit(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getXid(), eventToken.getNextPosition(), eventData.getData(), offset.get());
        EventGenerator.updateChecksum(commit.getLeft(), offset.get(), commit.getRight());
        eventToken.checkLength(commit.getRight());
        eventToken.setOffset(offset.getAndAdd(commit.getRight()));
    }

    private void buildTso(EventData eventData, SingleEventToken eventToken, AtomicInteger offset) {
        final Pair<byte[], Integer> tsoEvent = makeMarkEvent(eventToken.getTsoTimeSecond(), eventToken.getServerId(),
            eventToken.getCts(), eventToken.getNextPosition(), eventData.getData(), offset.get());
        EventGenerator.updateChecksum(tsoEvent.getLeft(), offset.get(), tsoEvent.getRight());
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
