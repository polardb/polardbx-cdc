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
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.TransmitMetrics;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.PacketMode;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_DRY_RUN_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_DUMPING_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.buildDumpReply;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.buildTxnBegin;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.buildTxnMergedToken;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.checkAndGetSchemaName;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.checkAndGetTableName;
import static com.aliyun.polardbx.binlog.transmit.MessageBuilder.packetMode;
import static com.aliyun.polardbx.binlog.util.TxnTokenUtil.cleanTxnBuffer4Token;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * Created by ziyang.lb
 **/
public class LogEventTransmitter implements Transmitter {

    private static final Logger logger = LoggerFactory.getLogger(LogEventTransmitter.class);
    private static final int CHUNK_MEM_UNIT = 1024;// memsize的单位，默认为1kb大小
    private static final int FLUSH_TIME_THRESHOLD = 500;//ms

    private final TaskType taskType;
    private final Storage storage;
    private final ArrayBlockingQueue<MessageEvent> transmitQueue;
    private final ArrayBlockingQueue<DumpReply> dumpingQueue;
    private final AtomicLong transmitQueueSize;
    private final AtomicLong dumpingQueueSize;
    private final ChunkMode chunkMode;
    private final int chunkItemSize;
    private final int maxMessageSize;
    private final boolean dryRun;
    private final int dryRunMode;
    private final String startTso;
    private final AtomicReference<TxnToken> firstToken;
    private final ExecutorService executor;
    private MessageChunk messageChunk;
    private volatile TxnToken latestFormatDescToken;
    private volatile boolean running;

    public LogEventTransmitter(TaskType taskType, int transmitBufferSize, Storage storage, ChunkMode chunkMode,
                               int chunkItemSize, int maxMessageSize, boolean dryRun, String startTso) {
        this.taskType = taskType;
        this.storage = storage;
        this.transmitQueue = new ArrayBlockingQueue<>(transmitBufferSize);
        this.dumpingQueue = new ArrayBlockingQueue<>(DynamicApplicationConfig.getInt(TASK_TRANSMIT_DUMPING_QUEUE_SIZE));
        this.transmitQueueSize = new AtomicLong(0L);
        this.dumpingQueueSize = new AtomicLong(0L);
        this.chunkMode = chunkMode;
        this.chunkItemSize = chunkItemSize;
        this.maxMessageSize = maxMessageSize;
        this.dryRun = dryRun;
        this.dryRunMode = DynamicApplicationConfig.getInt(TASK_TRANSMIT_DRY_RUN_MODE);
        this.startTso = startTso;
        this.firstToken = new AtomicReference<>(null);
        this.executor = Executors.newFixedThreadPool(1, getThreadFactory("txn-packet-builder" + "-%d", false));
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor.submit(() -> {
            try {
                buildPacket();
            } catch (Throwable t) {
                logger.error("build packet error!", t);
            }
        });
        logger.info("log event transmitter started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.shutdownNow();
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
            TxnToken token = firstToken.get();
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
            TransmitMetrics.get().setDumpingQueueSize(dumpingQueueSize.get());
            if (dryRun && dryRunMode == 1) {
                pollFromDumpingQueue();
                continue;
            }

            outputStream.checkState();

            if (latestFormatDescToken == null) {
                logger.warn("Latest format_desc token is not ready, will try later.");
                CommonUtils.sleep(1000);
                continue;
            }

            // 新注册上来的客户端，发送的首个Token必须是Format_Desc
            if (first.compareAndSet(true, false)) {
                sendTag(latestFormatDescToken, outputStream);
            } else {
                DumpReply reply = pollFromDumpingQueue();
                if (reply != null) {
                    while (true) {
                        if (outputStream.tryWait()) {
                            outputStream.onNext(reply);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void transmit(MessageEvent messageEvent) {
        try {
            TxnToken txnToken = messageEvent.getToken();
            TransmitMetrics.get().setTransmitQueuedSize(transmitQueueSize.get());
            TransmitMetrics.get().setDelayTimeOnTransmit(System.currentTimeMillis() - messageEvent.getTsoTimestamp());

            if (txnToken.getType() == TxnType.FORMAT_DESC) {
                logger.info("received a format_desc txn token in log event transmitter, tso is :" + txnToken.getTso());
                latestFormatDescToken = txnToken;
                return;
            }

            firstToken.compareAndSet(null, txnToken);
            addToTransmitQueue(messageEvent);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void buildPacket() throws InterruptedException {
        while (running) {
            if (dryRun && dryRunMode == 0) {
                MessageEvent messageEvent = pollFromTransmitQueue();
                if (messageEvent != null) {
                    clearCache(messageEvent);
                }
                continue;
            }

            MessageEvent messageEvent = pollFromTransmitQueue();
            if (messageEvent == null) {
                // 检测messageChunk是否达到了发送条件，不能因为queue中没有数据，而导致messageChunk中的数据一直发送不出去
                checkIfFlushChunk(null, false);
                continue;
            }

            TxnToken txnToken = messageEvent.getToken();
            if (txnToken.getType() != TxnType.FORMAT_DESC &&
                ((taskType == TaskType.Final && txnToken.getTso().compareTo(startTso) <= 0) ||
                    (taskType == TaskType.Relay && txnToken.getTso().compareTo(startTso) < 0))) {
                logger.info("Received Token`s tso {} is equal or lower than startTso {} , will skip.",
                    txnToken.getTso(), startTso);
                checkIfFlushChunk(null, false);
                clearCache(messageEvent);
                continue;
            }

            if (txnToken.getType() == TxnType.DML) {
                if (messageEvent.isAlreadyBuild()) {
                    checkIfFlushChunk(messageEvent, false);
                } else {
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
                        checkIfFlushChunk(null, true);
                        sendBegin(txnToken);
                        sendData(buffer);
                        sendEnd();
                        clearCache(messageEvent);
                        TransmitMetrics.get().incrementSingleTransmitCount();
                    } else {
                        checkIfFlushChunk(messageEvent, false);
                    }
                }
            } else {
                // 先把前序缓冲token进行flush，保证顺序
                checkIfFlushChunk(null, true);
                sendTag(txnToken, null);
            }
        }
    }

    private void checkIfFlushChunk(MessageEvent messageEvent, boolean forceSend)
        throws InterruptedException {
        if (messageChunk == null) {
            messageChunk = new MessageChunk(chunkMode, chunkItemSize);
        }

        if (forceSend) {
            sendChunk();
            return;
        }

        if (messageEvent != null) {
            long newMemSize = messageChunk.getTotalMemSize() + messageEvent.getMemSize();
            if (newMemSize > maxMessageSize) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Flush message chunk caused by max message size threshold, chunk mem size [{}],"
                        + " message event mem size [{}].", messageChunk.getTotalMemSize(), messageEvent.getMemSize());
                }
                sendChunk();
            }

            messageChunk.addMessageEvent(messageEvent);
            messageChunk.addMemSize(messageEvent.getMemSize());
        }

        if ((chunkMode == ChunkMode.ITEMSIZE && messageChunk.getMessageEvents().size() == chunkItemSize) ||
            (chunkMode == ChunkMode.MEMSIZE && messageChunk.getTotalMemSize() >= chunkItemSize * CHUNK_MEM_UNIT)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Flush message chunk caused by item size threshold, chunk item size [{}], "
                    + "chunk mem size [{}].", messageChunk.getMessageEvents().size(), messageChunk.getTotalMemSize());
            }
            sendChunk();
            return;
        }

        // 如果超过了flush阈值，messageChunk的size还未达到messageItemSize指定的个数，则不能无休止等待
        if (FLUSH_TIME_THRESHOLD < DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL_MS)
            && System.currentTimeMillis() - messageChunk.getStartTimeMills() >= FLUSH_TIME_THRESHOLD) {
            if (logger.isDebugEnabled()) {
                logger.debug("Flush message chunk triggered by time threshold.");
            }
            sendChunk();
        }
    }

    private void sendChunk() throws InterruptedException {
        if (isMessageChunkEmpty()) {
            return;
        }

        DumpReply.Builder builder = DumpReply.newBuilder();
        for (MessageEvent messageEvent : messageChunk.getMessageEvents()) {
            if (messageEvent.isAlreadyBuild()) {
                addTxnMessage(builder, messageEvent);
            } else {
                TxnMessage message = MessageBuilder.buildTxnMessage(messageEvent.getToken(), taskType,
                    messageEvent.getTxnBuffers().get(0));
                if (packetMode == PacketMode.OBJECT) {
                    builder.addTxnMessage(message);
                } else {
                    builder.addTxnMessageBytes(message.toByteString());
                }
            }
            clearCache(messageEvent);
        }

        DumpReply dumpReply = builder.setPacketMode(packetMode).build();
        addToDumpingQueue(dumpReply);
        TransmitMetrics.get().addChunkTransmitCount(messageChunk.getMessageEvents().size());
        messageChunk.clear();
    }

    private void addTxnMessage(DumpReply.Builder builder, MessageEvent messageEvent) {
        if (packetMode == PacketMode.OBJECT) {
            builder.addTxnMessage(messageEvent.getTxnMessage());
        } else {
            builder.addTxnMessageBytes(messageEvent.getTxnMessageBytes());
        }
    }

    private void sendBegin(TxnToken token) throws InterruptedException {
        TxnMessage message = TxnMessage.newBuilder()
            .setType(MessageType.BEGIN)
            .setTxnBegin(buildTxnBegin(token, taskType))
            .build();
        addToDumpingQueue(buildDumpReply(message));
    }

    private void sendData(TxnBuffer buffer) throws InterruptedException {
        int memSize = 0;
        List<TxnItem> items = new ArrayList<>();

        Iterator<TxnItemRef> iterator = buffer.parallelRestoreIterator();
        while (iterator.hasNext()) {
            TxnItemRef txnItemRef = iterator.next();
            EventData eventData = txnItemRef.getEventData();
            ByteString payload = eventData.getPayload();
            memSize += payload.size();

            TxnItem txnItem = TxnItem.newBuilder()
                .setTraceId(txnItemRef.getTraceId())
                .setRowsQuery(eventData.getRowsQuery())
                .setEventType(txnItemRef.getEventType())
                .setPayload(payload)
                .setSchema(checkAndGetSchemaName(eventData))
                .setTable(checkAndGetTableName(eventData))
                .build();
            items.add(txnItem);

            if ((chunkMode == ChunkMode.MEMSIZE && memSize >= chunkItemSize * CHUNK_MEM_UNIT) ||
                (chunkMode == ChunkMode.ITEMSIZE && items.size() == chunkItemSize) ||
                memSize > maxMessageSize) {
                sendData(items);
                items.clear();
                memSize = 0;
            }
            txnItemRef.clearEventData();//尽快释放内存空间，防止堆内存溢出
        }

        if (!items.isEmpty()) {
            sendData(items);
        }
    }

    private void sendData(List<TxnItem> items)
        throws InterruptedException {
        TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(txnData).build();
        addToDumpingQueue(buildDumpReply(message));
    }

    private void sendEnd() throws InterruptedException {
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.END).build();
        addToDumpingQueue(buildDumpReply(message));
    }

    private void sendTag(TxnToken token, TxnOutputStream<DumpReply> txnOutputStream) throws InterruptedException {
        TxnTag txnTag;
        if (taskType == TaskType.Relay) {
            txnTag = TxnTag.newBuilder().setTxnToken(token).build();
        } else {
            txnTag = TxnTag.newBuilder().setTxnMergedToken(buildTxnMergedToken(token)).build();
        }

        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
        DumpReply dumpReply = buildDumpReply(message);
        if (txnOutputStream != null) {
            txnOutputStream.onNext(dumpReply);
        } else {
            addToDumpingQueue(dumpReply);
        }
    }

    private void addToDumpingQueue(DumpReply dumpReply) throws InterruptedException {
        dumpingQueue.put(dumpReply);
        dumpingQueueSize.incrementAndGet();
    }

    private DumpReply pollFromDumpingQueue() throws InterruptedException {
        DumpReply reply = dumpingQueue.poll(500, TimeUnit.MILLISECONDS);
        if (reply != null) {
            dumpingQueueSize.decrementAndGet();
        }
        return reply;
    }

    private void addToTransmitQueue(MessageEvent messageEvent) throws InterruptedException {
        transmitQueue.put(messageEvent);
        transmitQueueSize.incrementAndGet();
    }

    private MessageEvent pollFromTransmitQueue() throws InterruptedException {
        MessageEvent messageEvent = transmitQueue.poll(500, TimeUnit.MILLISECONDS);
        if (messageEvent != null) {
            transmitQueueSize.decrementAndGet();
        }
        return messageEvent;
    }

    private void clearCache(MessageEvent messageEvent) {
        cleanTxnBuffer4Token(messageEvent.getToken(), storage);
    }

    private boolean isMessageChunkEmpty() {
        return messageChunk == null || messageChunk.getMessageEvents().isEmpty();
    }
}
