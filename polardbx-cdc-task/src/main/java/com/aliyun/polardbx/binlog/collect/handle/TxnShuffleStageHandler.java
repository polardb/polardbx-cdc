/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.collect.handle;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.MergeMetrics;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.WorkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class TxnShuffleStageHandler implements WorkHandler<MessageEvent>, LifecycleAware {

    private static final Logger logger = LoggerFactory.getLogger(TxnShuffleStageHandler.class);

    private final HandleContext handleContext;
    private final Storage storage;
    private final boolean isMergeNoTsoXa;

    public TxnShuffleStageHandler(HandleContext handleContext, Storage storage, boolean isMergeNoTsoXa) {
        this.handleContext = handleContext;
        this.storage = storage;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
    }

    @Override
    public void onEvent(MessageEvent event) {
        try {
            TxnToken token = event.getToken();
            if (token.getType() == TxnType.FORMAT_DESC) {
                logger.info("receive a format_desc token with tso {} ", token.getTso());
            } else if (token.getType() == TxnType.META_DDL) {
                logger.info("receive a ddl token with tso {}", token.getTso());
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.META_DDL_PRIVATE) {
                logger.info("receive a private ddl token with tso {}", token.getTso());
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.META_SCALE) {
                logger.info("receive a meta_scale token with tso {}", token.getTso());
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.DML) {
                processDmlToken(event, token);
            } else if (token.getType() == TxnType.META_HEARTBEAT) {
                processMetaToken(event, token);
            } else {
                throw new PolardbxException("invalid txn token type: " + token.getType());
            }

            MergeMetrics.get()
                .setDelayTimeOnCollect(
                    System.currentTimeMillis() - CommonUtils.getTsoPhysicalTime(token.getTso(), TimeUnit.MILLISECONDS));
        } catch (Throwable t) {
            CollectException exception = new CollectException("error occurred when do txn merge.", t);
            handleContext.setException(exception);
            throw exception;
        }
    }

    @Override
    public void onStart() {
        logger.info("{} started", getClass().getSimpleName());
    }

    @Override
    public void onShutdown() {
        logger.info("{} shutdown", getClass().getSimpleName());
    }

    private void processMetaToken(MessageEvent event, TxnToken token) {
        if (token.getXaTxn() && token.getTsoTransaction()) {
            event.setMerged(true);
        } else {
            throw new PolardbxException("Meta TxnToken must be XaTxn and with tso transaction policy.");
        }
    }

    private void processDmlToken(MessageEvent event, TxnToken token) {
        if (token.getXaTxn() && (token.getTsoTransaction() || isMergeNoTsoXa)) {
            if (token.getAllPartiesCount() > 0) {
                TxnToken output = txnMerge(token);
                event.setMerged(true);
                event.setToken(output);
            } else {
                throw new PolardbxException("Received XA token, but it`s allParties is empty, the token is :" + token);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("1pc token " + token);
            }
        }
    }

    private TxnToken txnMerge(TxnToken in) {
        boolean exist = this.storage.exist(new TxnKey(in.getTxnId(), in.getPartitionId()));
        if (!exist) {
            throw new PolardbxException("can`t find TxnBuffer for TxnToken :" + in);
        }

        TxnBuffer baseBuffer = this.storage.fetch(new TxnKey(in.getTxnId(), in.getPartitionId()));
        if (in.getAllPartiesCount() == 1) {
            return in;
        } else {
            in.getAllPartiesList()
                .stream()
                .filter(p -> !p.equals(in.getPartitionId()))
                .collect(Collectors.toList())
                .forEach(p -> {
                    TxnBuffer buffer = this.storage.fetch(new TxnKey(in.getTxnId(), p));
                    if (buffer == null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("txn buffer is not found for key {}.", new TxnKey(in.getTxnId(), p));
                        }
                        throw new PolardbxException("can`t find TxnBuffer for TxnToken :" + p);
                    }
                    if (!buffer.isCompleted()) {
                        throw new PolardbxException(
                            "Illegal buffer state, buffer is not complete yet, but the heartbeat window has expired.");
                    }

                    assert Objects.requireNonNull(buffer).isCompleted();
                    baseBuffer.merge(buffer);
                });
            return in.toBuilder().setTxnSize(baseBuffer.itemSize()).build();
        }
    }
}
