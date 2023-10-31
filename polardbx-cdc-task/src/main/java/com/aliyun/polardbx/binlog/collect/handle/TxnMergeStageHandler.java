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
package com.aliyun.polardbx.binlog.collect.handle;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.MergeMetrics;
import com.aliyun.polardbx.binlog.protocol.PacketMode;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.PersistAllChecker;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.WorkHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_COLLECT_BUILD_PACKET_SIZE_LIMIT;
import static com.aliyun.polardbx.binlog.domain.TaskType.Dispatcher;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.buildTxnMessage;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.packetMode;

/**
 * Created by ziyang.lb
 **/
public class TxnMergeStageHandler implements WorkHandler<MessageEvent>, LifecycleAware {

    private static final Logger logger = LoggerFactory.getLogger(TxnMergeStageHandler.class);

    private final HandleContext handleContext;
    private final Storage storage;
    private final boolean isMergeNoTsoXa;
    private final TaskType taskType;
    private final PersistAllChecker persistAllChecker;

    public TxnMergeStageHandler(HandleContext handleContext, Storage storage, boolean isMergeNoTsoXa,
                                TaskType taskType) {
        this.handleContext = handleContext;
        this.storage = storage;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
        this.taskType = taskType;
        this.persistAllChecker = new PersistAllChecker();
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
            } else if (token.getType() == TxnType.META_SCALE) {
                logger.info("receive a meta_scale token with tso {}", token.getTso());
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.DML) {
                processDmlToken(event, token);
            } else if (token.getType() == TxnType.META_HEARTBEAT) {
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.META_CONFIG_ENV_CHANGE) {
                logger.info("receive a meta_config_env_change token with tso {}", token.getTso());
                processMetaToken(event, token);
            } else if (token.getType() == TxnType.FLUSH_LOG) {
                logger.info("receive a flush_log token with tso {}", token.getTso());
                processFlushLogToken(event, token);
            } else {
                throw new PolardbxException("invalid txn token type: " + token.getType());
            }

            long tsoTimestamp = CommonUtils.getTsoPhysicalTime(token.getTso(), TimeUnit.MILLISECONDS);
            event.setTsoTimestamp(tsoTimestamp);
            MergeMetrics.get().setDelayTimeOnCollect(System.currentTimeMillis() - tsoTimestamp);
        } catch (Throwable t) {
            CollectException exception =
                new CollectException("error occurred when do txn merge, with token " + event.getToken(), t);
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

    private void processFlushLogToken(MessageEvent event, TxnToken token) {
        if (token.getXaTxn() && token.getTsoTransaction()) {
            event.setMerged(true);
        } else {
            throw new PolardbxException("FlushLog TxnToken must be XaTxn and with tso transaction policy.");
        }
    }

    private void processDmlToken(MessageEvent event, TxnToken token) {
        if (token.getXaTxn() && (token.getTsoTransaction() || isMergeNoTsoXa)) {
            if (token.getAllPartiesCount() > 0) {
                Pair<TxnToken, List<TxnBuffer>> pair = txnMerge(token);
                event.setMerged(true);
                event.setToken(pair.getLeft());
                event.setTxnBuffers(pair.getRight());
            } else {
                throw new PolardbxException("Received XA token, but it`s allParties is empty, the token is :" + token);
            }
        } else {
            TxnBuffer txnBuffer = fetchTxnBuffer(token, new TxnKey(token.getTxnId(), token.getPartitionId()));
            event.setTxnBuffers(Lists.newArrayList(txnBuffer));
            if (logger.isDebugEnabled()) {
                logger.debug("1pc token " + token);
            }
        }

        event.setMemSize(calcMemSize(event));
        if (packetMode == PacketMode.OBJECT) {
            event.setTxnMessage(tryBuildTxnMessageObject(event));
        } else if (packetMode == PacketMode.BYTES) {
            event.setTxnMessageBytes(tryBuildTxnMessageBytes(event));
        } else {
            throw new PolardbxException("unsupported packet mode " + packetMode);
        }
        event.getTxnBuffers().get(0).parallelRestoreIterator();
    }

    private long calcMemSize(MessageEvent event) {
        TxnBuffer txnBuffer = event.getTxnBuffers().get(0);
        return txnBuffer.memSize();
    }

    private TxnMessage tryBuildTxnMessageObject(MessageEvent messageEvent) {
        long threshold = DynamicApplicationConfig.getLong(TASK_COLLECT_BUILD_PACKET_SIZE_LIMIT);
        if (taskType != Dispatcher && messageEvent.getMemSize() <= threshold) {
            return buildTxnMessage(messageEvent.getToken(), taskType, messageEvent.getTxnBuffers().get(0));
        }
        return null;
    }

    private ByteString tryBuildTxnMessageBytes(MessageEvent messageEvent) {
        long threshold = DynamicApplicationConfig.getLong(TASK_COLLECT_BUILD_PACKET_SIZE_LIMIT);
        if (taskType != Dispatcher && messageEvent.getMemSize() <= threshold) {
            return buildTxnMessage(messageEvent.getToken(), taskType,
                messageEvent.getTxnBuffers().get(0)).toByteString();
        }
        return null;
    }

    private Pair<TxnToken, List<TxnBuffer>> txnMerge(TxnToken in) {
        TxnBuffer baseBuffer = fetchTxnBuffer(in, new TxnKey(in.getTxnId(), in.getPartitionId()));

        List<TxnBuffer> txnBuffers = new ArrayList<>();
        txnBuffers.add(baseBuffer);

        if (in.getAllPartiesCount() == 1) {
            // 如果只有一个party，需要单独对这个party对应的TxnBuffer中的traceid进行去重
            // 问：什么时候采用TSO策略的事务在此处只有一个party？
            // 答：某个TSO事务有两个分片参与提交，但其中一个只有GSI的event，该分片会被extractor忽略掉，此处只能拿到一个party
            baseBuffer.compressDuplicateTraceId();
            return Pair.of(in, txnBuffers);
        } else {
            in.getAllPartiesList()
                .stream()
                .filter(p -> !p.equals(in.getPartitionId()))
                .collect(Collectors.toList())
                .forEach(p -> {
                    TxnBuffer buffer = fetchTxnBuffer(in, new TxnKey(in.getTxnId(), p));
                    if (!buffer.isCompleted()) {
                        throw new PolardbxException(
                            "Illegal buffer state, buffer is not complete yet, but the heartbeat window has expired.");
                    }

                    assert Objects.requireNonNull(buffer).isCompleted();
                    baseBuffer.merge(buffer);
                    txnBuffers.add(buffer);
                });
            return Pair.of(in.toBuilder().setTxnSize(baseBuffer.itemSize()).build(), txnBuffers);
        }
    }

    private TxnBuffer fetchTxnBuffer(TxnToken token, TxnKey txnKey) {
        TxnBuffer buffer = this.storage.fetch(txnKey);
        if (buffer == null) {
            throw new PolardbxException("can`t find TxnBuffer for TxnToken :" + token);
        }

        persistAllChecker.checkWithCallback(buffer.isLargeTrans(), () -> {
            buffer.persist();
            return "txn buffer is persisted in " + TxnMergeStageHandler.class.getName() + ", with token " + token;
        });
        return buffer;
    }
}
