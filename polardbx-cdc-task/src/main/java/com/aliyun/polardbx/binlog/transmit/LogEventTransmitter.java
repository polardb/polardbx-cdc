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

package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.MergeMetrics;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnEnd;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnTag;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL;

/**
 * Created by ziyang.lb
 **/
public class LogEventTransmitter implements Transmitter {

    private final static Logger logger = LoggerFactory.getLogger(LogEventTransmitter.class);
    private final static int CHUNK_MEM_UNIT = 1024;// memsize的单位，默认为1kb大小
    private final static int FLUSH_TIME_THRESHOLD = 500;//ms

    private final TaskType taskType;
    private final Storage storage;
    private final ArrayBlockingQueue<TxnToken> queue;
    private final ChunkMode chunkMode;
    private final int chunkItemSize;
    private final int maxMessageSize;
    private final boolean dryRun;
    private MessageChunk messageChunk;
    private volatile TxnToken latestFormatDescToken;
    private volatile boolean running;

    public LogEventTransmitter(TaskType taskType, int bufferSize, Storage storage, ChunkMode chunkMode,
                               int chunkItemSize, int maxMessageSize, boolean dryRun) {
        this.taskType = taskType;
        this.storage = storage;
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        this.chunkMode = chunkMode;
        this.chunkItemSize = chunkItemSize;
        this.maxMessageSize = maxMessageSize;
        this.dryRun = dryRun;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        logger.info("log event transmitter started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        logger.info("log event transmitter stopped.");
    }

    @Override
    public boolean checkTSO(String startTso, TxnOutputStream<DumpReply> outputStream,
                            boolean keepWaiting) throws InterruptedException {
        if (StringUtils.isBlank(startTso)) {
            return true;
        }

        long count = 0;
        while (true) {
            TxnToken token = queue.peek();
            if (token != null) {
                boolean result = token.getTso().compareTo(startTso) <= 0;
                if (!result) {
                    logger.info("can`t find correct tso for dump request, the assigned startTso is : " + startTso
                        + ", the first token in queue is :" + token);
                }
                return result;
            }
            if (count == 200 && !keepWaiting) {
                return false;
            }
            outputStream.checkState();
            CommonUtils.sleep(10);
            count++;
        }
    }

    @Override
    public void dump(String startTso, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        final AtomicBoolean first = new AtomicBoolean(true);
        while (running) {
            if (dryRun) {
                TxnToken txnToken = queue.poll(500, TimeUnit.MILLISECONDS);
                if (txnToken != null) {
                    clearCache(txnToken);
                }
                continue;
            }

            // 增加反压控制判断
            if (!outputStream.tryWait()) {
                continue;
            }

            TxnToken txnToken = null;
            if (latestFormatDescToken == null) {
                logger.warn("Latest format_desc token is not ready, will try later.");
                CommonUtils.sleep(1000);
            } else {
                // 新注册上来的客户端，发送的首个Token必须是Format_Desc
                if (first.compareAndSet(true, false)) {
                    txnToken = latestFormatDescToken;
                } else {
                    txnToken = queue.poll(500, TimeUnit.MILLISECONDS);
                }
            }

            // 1. 尽早发现流是否已经出现了异常
            // 2. 检测messageChunk是否达到了发送条件，不能因为queue中没有数据，而导致messageChunk中的数据一直发送不出去
            if (txnToken == null) {
                outputStream.checkState();
                checkIfFlushChunk(null, outputStream, false);
                continue;
            }

            if (txnToken.getType() != TxnType.FORMAT_DESC &&
                ((taskType == TaskType.Final && txnToken.getTso().compareTo(startTso) <= 0) ||
                    (taskType == TaskType.Relay && txnToken.getTso().compareTo(startTso) < 0))) {
                logger.info("Received Token`s tso {} is equal or lower than startTso {} , will skip.",
                    txnToken.getTso(), startTso);
                checkIfFlushChunk(null, outputStream, false);
                clearCache(txnToken);
                continue;
            }

            if (txnToken.getType() == TxnType.DML) {
                TxnKey txnKey = new TxnKey(txnToken.getTxnId(), txnToken.getPartitionId());
                TxnBuffer buffer = storage.fetch(txnKey);

                if (buffer == null) {
                    throw new PolardbxException(
                        "TxnBuffer is not found for txn key: " + txnKey + ", with the token is " + txnToken);
                }
                if (!buffer.isCompleted()) {
                    throw new PolardbxException(
                        "TxnBuffer is not completed for txn key:" + txnKey + ", with the token is " + txnToken);
                }
                if (buffer.itemSize() <= 0) {
                    throw new PolardbxException(
                        "TxnBuffer is empty for txn key:" + txnKey + ", with the token is " + txnToken);
                }

                if (buffer.memSize() > maxMessageSize) {
                    logger.info(
                        "TxnBuffer size {} is greater than maxMessageSize {}, will send in split mode, with TxnToken is {}",
                        buffer.memSize(), maxMessageSize, txnToken);

                    // 先把前序缓冲token进行flush，保证顺序
                    checkIfFlushChunk(null, outputStream, true);// 先把前序缓冲token进行flush，保证顺序
                    sendBegin(txnToken, outputStream);
                    sendData(buffer, outputStream);
                    sendEnd(outputStream);
                    clearCache(txnToken);
                    MergeMetrics.get().incrementSingleTransmitCount();
                } else {
                    checkIfFlushChunk(new ImmutablePair<>(txnToken, buffer), outputStream, false);
                }
            } else {
                // 先把前序缓冲token进行flush，保证顺序
                checkIfFlushChunk(null, outputStream, true);
                sendTag(txnToken, outputStream);
            }
        }
    }

    @Override
    public void transmit(TxnToken txnToken) {
        try {
            MergeMetrics.get().setTransmitQueuedSize(queue.size());
            MergeMetrics.get()
                .setDelayTimeOnTransmit(System.currentTimeMillis()
                    - CommonUtils.getTsoPhysicalTime(txnToken.getTso(), TimeUnit.MILLISECONDS));

            if (txnToken.getType() == TxnType.FORMAT_DESC) {
                logger.info("received a format_desc txn token in log event transmitter, tso is :" + txnToken.getTso());
                latestFormatDescToken = txnToken;
                return;
            }

            queue.put(txnToken);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void checkIfFlushChunk(Pair<TxnToken, TxnBuffer> tokenAndBuffer, TxnOutputStream<DumpReply> outputStream,
                                   boolean forceSend) {
        if (messageChunk == null) {
            messageChunk = new MessageChunk(chunkMode, chunkItemSize);
        }

        if (forceSend) {
            sendChunk(outputStream);
            return;
        }

        if (tokenAndBuffer != null) {
            TxnBuffer buffer = tokenAndBuffer.getRight();
            long newMemSize = messageChunk.getTotalMemSize() + buffer.memSize();
            if (newMemSize > maxMessageSize) {
                sendChunk(outputStream);
            }

            messageChunk.addTxnToken(tokenAndBuffer.getLeft());
            messageChunk.addMemSize(buffer.memSize());
        }

        if ((chunkMode == ChunkMode.ITEMSIZE && messageChunk.getTokens().size() == chunkItemSize) ||
            (chunkMode == ChunkMode.MEMSIZE && messageChunk.getTotalMemSize() >= chunkItemSize * CHUNK_MEM_UNIT)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Flush message chunk triggered by item size threshold.");
            }
            sendChunk(outputStream);
            return;
        }

        // 如果超过了flush阈值，messageChunk的size还未达到messageItemSize指定的个数，则不能无休止等待
        if (FLUSH_TIME_THRESHOLD < DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL)
            && System.currentTimeMillis() - messageChunk.getStartTimeMills() >= FLUSH_TIME_THRESHOLD) {
            if (logger.isDebugEnabled()) {
                logger.debug("Flush message chunk triggered by time threshold.");
            }
            sendChunk(outputStream);
        }
    }

    private void sendChunk(TxnOutputStream<DumpReply> outputStream) {
        if (isMessageChunkEmpty()) {
            return;
        }

        List<TxnMessage> messages = new ArrayList<>();
        for (TxnToken token : messageChunk.getTokens()) {
            // make begin
            TxnBegin txnBegin;
            if (taskType == TaskType.Relay) {
                txnBegin = TxnBegin.newBuilder().setTxnToken(token).build();
            } else {
                txnBegin = TxnBegin.newBuilder().setTxnMergedToken(buildTxnMergedToken(token)).build();
            }

            // make data
            List<TxnItem> items = new ArrayList<>();
            TxnBuffer buffer = storage.fetch(new TxnKey(token.getTxnId(), token.getPartitionId()));
            Iterator<TxnItemRef> iterator = buffer.iterator();
            while (iterator.hasNext()) {
                TxnItemRef txnItemRef = iterator.next();
                items.add(TxnItem.newBuilder()
                    .setTraceId(txnItemRef.getTraceId())
                    .setEventType(txnItemRef.getEventType())
                    .setPayload(txnItemRef.getByteStringPayload())
                    .build());
                txnItemRef.clearPayload();//尽快释放内存空间，防止内存溢出
            }
            TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();

            // make end
            TxnEnd end = TxnEnd.newBuilder().build();

            // add list
            messages.add(TxnMessage.newBuilder()
                .setType(MessageType.WHOLE)
                .setTxnBegin(txnBegin)
                .setTxnData(txnData)
                .setTxnEnd(end)
                .build());

            // clear cache
            clearCache(token);
        }

        DumpReply dumpReply = DumpReply.newBuilder().addAllTxnMessage(messages).build();
        if (logger.isDebugEnabled()) {
            logger.debug("DumpReply packet size is " + dumpReply.getSerializedSize());
        }
        outputStream.onNext(dumpReply);
        MergeMetrics.get().addChunkTransmitCount(messageChunk.getTokens().size());
        messageChunk.clear();
    }

    private void sendBegin(TxnToken token, TxnOutputStream<DumpReply> outputStream) {
        TxnBegin txnBegin;
        if (taskType == TaskType.Relay) {
            txnBegin = TxnBegin.newBuilder().setTxnToken(token).build();
        } else {
            txnBegin = TxnBegin.newBuilder().setTxnMergedToken(buildTxnMergedToken(token)).build();
        }
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.BEGIN).setTxnBegin(txnBegin).build();
        outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
    }

    private void sendData(TxnBuffer buffer, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        int memSize = 0;
        List<TxnItem> items = new ArrayList<>();

        Iterator<TxnItemRef> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            TxnItemRef txnItemRef = iterator.next();
            ByteString payload = txnItemRef.getByteStringPayload();
            memSize += payload.size();

            items.add(TxnItem.newBuilder()
                .setTraceId(txnItemRef.getTraceId())
                .setEventType(txnItemRef.getEventType())
                .setPayload(payload)
                .build());

            if ((chunkMode == ChunkMode.MEMSIZE && memSize >= chunkItemSize * CHUNK_MEM_UNIT) ||
                (chunkMode == ChunkMode.ITEMSIZE && items.size() == chunkItemSize) ||
                memSize > maxMessageSize) {
                sendData(items, outputStream);
                items.clear();
                memSize = 0;
            }
            txnItemRef.clearPayload();//尽快释放内存空间，防止堆内存溢出
        }

        if (!items.isEmpty()) {
            sendData(items, outputStream);
        }
    }

    private void sendData(List<TxnItem> items, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        while (true) {
            if (outputStream.tryWait()) {
                TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();
                TxnMessage message = TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(txnData).build();
                outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
                break;
            }
        }
    }

    private void sendEnd(TxnOutputStream<DumpReply> outputStream) {
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.END).build();
        outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
    }

    private void sendTag(TxnToken token, TxnOutputStream<DumpReply> outputStream) {
        TxnTag txnTag;
        if (taskType == TaskType.Relay) {
            txnTag = TxnTag.newBuilder().setTxnToken(token).build();
        } else {
            txnTag = TxnTag.newBuilder().setTxnMergedToken(buildTxnMergedToken(token)).build();
        }

        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
        outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
    }

    private TxnMergedToken buildTxnMergedToken(TxnToken token) {
        return TxnMergedToken.newBuilder()
            .setType(token.getType())
            .setBeginSchema(token.getBeginSchema())
            .setTso(token.getTso())
            .setPayload(token.getPayload())
            .build();
    }

    private void clearCache(TxnToken token) {
        // 如果allPartiesCount > 0，说明这是一个事务合并后的delegate token，需要把所有分片的缓存都清空
        if (token.getAllPartiesCount() > 0) {
            token.getAllPartiesList().forEach(p -> {
                TxnKey key = new TxnKey(token.getTxnId(), p);
                storage.deleteAsync(key);
            });
        } else {
            TxnKey key = new TxnKey(token.getTxnId(), token.getPartitionId());
            storage.deleteAsync(key);
        }
    }

    private boolean isMessageChunkEmpty() {
        return messageChunk == null || messageChunk.getTokens().isEmpty();
    }
}
