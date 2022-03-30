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

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.domain.RpcParameter;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnMessageReceiver;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcClient;
import com.aliyun.polardbx.binlog.storage.AlreadyExistException;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
@SuppressWarnings("rawtypes")
public class RpcExtractor implements Extractor {

    private static final Logger logger = LoggerFactory.getLogger(RpcExtractor.class);

    private final String upstreamTaskName;                                    // 即：上游的taskName
    private final MergeSource mergeSource;
    private final Storage storage;
    private ExecutorService executor;
    private RpcParameter rpcParameter;
    private volatile boolean running;

    public RpcExtractor(String upstreamTaskName, MergeSource mergeSource, Storage storage) {
        this.upstreamTaskName = upstreamTaskName;
        this.mergeSource = mergeSource;
        this.storage = storage;
    }

    @Override
    public void start(String startTSO) {
        if (running) {
            return;
        }
        running = true;

        executor = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("rpc-dump-%d").build());
        executor.execute(() -> {
            final AtomicReference<String> latestCommitTso = new AtomicReference<>("");
            TxnStreamRpcClient rpcClient = null;

            while (running) {
                try {
                    final AtomicReference<TxnBuffer> txnBuffer = new AtomicReference<>();
                    final AtomicReference<TxnToken> txnToken = new AtomicReference<>();
                    final AtomicReference<TxnKey> txnKey = new AtomicReference<>();
                    final String initTSO = StringUtils.isNotBlank(latestCommitTso.get()) ? latestCommitTso
                        .get() : startTSO;

                    buildRpcParameter();
                    String target = rpcParameter.getAddress() + ":" + rpcParameter.getPort();
                    NettyChannelBuilder channelBuilder =
                        (NettyChannelBuilder) ManagedChannelBuilder.forTarget(target).usePlaintext();// TODO,参数需要进一步丰富
                    rpcClient = new TxnStreamRpcClient(channelBuilder, new TxnMessageReceiver() {

                        @Override
                        public void onReceived(List<TxnMessage> messages) throws InterruptedException {
                            for (TxnMessage message : messages) {
                                if (!running) {
                                    throw new InterruptedException();
                                }

                                if (message.getType() == MessageType.WHOLE) {
                                    processMessage(message, MessageType.BEGIN);
                                    processMessage(message, MessageType.DATA);
                                    processMessage(message, MessageType.END);
                                } else {
                                    processMessage(message, message.getType());
                                }
                            }
                        }

                        public void processMessage(TxnMessage message,
                                                   MessageType processType) throws InterruptedException {
                            try {
                                if (processType == MessageType.BEGIN) {
                                    assert txnToken.get() == null;
                                    assert txnBuffer.get() == null;
                                    assert txnKey.get() == null;

                                    // 只有type为BEGIN的时候，TxnMessage的TxnToken才有值，其它type情况下，token为空
                                    txnToken.set(message.getTxnBegin().getTxnToken());
                                    txnKey.set(new TxnKey(txnToken.get().getTxnId(), txnToken.get().getPartitionId()));
                                    txnBuffer.set(storage.create(txnKey.get()));
                                    checkTso(txnToken.get(), initTSO);
                                } else if (processType == MessageType.DATA) {
                                    txnBuffer.get().push(
                                        message.getTxnData().getTxnItemsList().stream()
                                            .map(i -> TxnBufferItem.builder().eventType(i.getEventType())
                                                .traceId(i.getTraceId()).byteStringPayload(i.getPayload())
                                                .rowsQuery(i.getRowsQuery()).schema(i.getSchema()).table(i.getTable())
                                                .build())
                                            .collect(Collectors.toList())
                                    );
                                } else if (processType == MessageType.END) {
                                    checkTxnItemSize(txnBuffer.get(), txnToken.get());
                                    mergeSource.push(txnToken.get());
                                    latestCommitTso.set(txnToken.get().getTso());
                                    txnBuffer.set(null);
                                    txnKey.set(null);
                                    txnToken.set(null);
                                } else if (processType == MessageType.TAG) {
                                    TxnToken tagToken = message.getTxnTag().getTxnToken();
                                    checkTso(tagToken, initTSO);
                                    mergeSource.push(tagToken, false);
                                } else {
                                    throw new PolardbxException("invalid txn message type: " + processType);
                                }
                            } catch (AlreadyExistException e) {
                                // Flag-1会做合法性校验，Flag-2会回滚Buffer，所以正常情况肯定不会出现该错误
                                // 一旦出现该错误，只能是触发了严重的bug，直接触发进程退出
                                logger.error("fatal error", e);
                                Runtime.getRuntime().halt(1);
                                throw new InterruptedException("interrupt for fatal error.");
                            } catch (Throwable t) {
                                // Flag-2，出现异常，对storage中保存的数据进行回滚
                                if (storage.exist(txnKey.get())) {
                                    storage.delete(txnKey.get());
                                }
                                throw t;
                            }
                        }
                    });

                    DumpRequest request = DumpRequest.newBuilder().setTso(initTSO).build();
                    rpcClient.connect();
                    rpcClient.dump(request);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    logger.error("rpc client dump error", t);
                    try {
                        CommonUtils.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                } finally {
                    if (rpcClient != null) {
                        rpcClient.disconnect();
                    }
                }
            }
        });

        logger.info("Rpc Extractor for merge source {} started.", mergeSource.getSourceId());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (executor != null) {
            try {
                executor.shutdownNow();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }

        logger.info("Rpc Extractor for merge source {} stopped.", mergeSource.getSourceId());
    }

    public void setRpcParameter(RpcParameter rpcParameter) {
        this.rpcParameter = rpcParameter;
    }

    private void buildRpcParameter() {
        assert rpcParameter != null;
        if (!rpcParameter.isDynamic()) {
            assert StringUtils.isNotBlank(rpcParameter.getAddress());
            assert rpcParameter.getPort() != null;
        } else {
            assert StringUtils.isNotBlank(rpcParameter.getTaskName());
            BinlogTaskConfigMapper mapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
            BinlogTaskConfig task = mapper.selectOne(s -> s.where(BinlogTaskConfigDynamicSqlSupport.taskName,
                IsEqualTo.of(() -> rpcParameter.getTaskName()))).get();
            rpcParameter.setAddress(task.getIp());
            rpcParameter.setPort(task.getPort());
        }
    }

    private void checkTso(TxnToken txnToken, String initTSO) {
        /*
         * Flag-1：tso合法性验证
         * 按照设计方案的约定，上游推送过来的事件的tso，必须大于DumpRequest中指定的startTso，如果发现小于等于startTSO的事件
         * 说明出现了bug，直接抛异常。FORMAT_DESC是一种特殊的Token，其tso没有实际意义，直接透传给下游即可，不做校验。
         */
        if (txnToken.getTso().compareTo(initTSO) <= 0 && txnToken.getType() != TxnType.FORMAT_DESC) {
            logger.error(
                "Received illegal message with tso {}，reason: the tso can`t be equal to or less than startTSO {}.",
                txnToken.getTso(),
                initTSO);
            throw new PolardbxException(String.format(
                "Received illegal message with tso %s，reason: the tso can`t be equal to or less than startTSO %s.",
                txnToken.getTso(),
                initTSO));
        }
    }

    private void checkTxnItemSize(TxnBuffer txnBuffer, TxnToken txnToken) {
        if (txnBuffer.itemSize() != txnToken.getTxnSize()) {
            throw new PolardbxException(String.format(
                "Size of TxnBuffer is different from that in the origin, received size is %s, origin size is %s, txn token is %s",
                txnBuffer.itemSize(),
                txnToken.getTxnSize(),
                txnToken));
        }
    }
}
