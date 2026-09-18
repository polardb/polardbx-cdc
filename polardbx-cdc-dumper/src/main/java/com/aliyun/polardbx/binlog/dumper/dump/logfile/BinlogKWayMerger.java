/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnTag;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnMessageReceiver;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcClient;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Lists;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_KWAY_PARTITION_ID_CHECK_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_KWAY_SOURCE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.MERGER_STAGE_LOOP_ERROR;
import static com.aliyun.polardbx.binlog.util.CommonUtils.getActualTso;
import static com.aliyun.polardbx.binlog.util.CommonUtils.parseStreamSeq;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogKWayMerger {
    private static final Long SLEEP_TIME = 10L;

    private final String taskName;
    private final String streamName;
    private final List<Pair<String, String>> targetTaskAddress;
    private final TxnMessageReceiver receiver;
    private final Map<String, TxnStreamRpcClient> rpcClients;
    private final Map<String, BinlogXMergeSource> mergeSources;
    private final BinlogXMergeController mergeController;
    private final ExecutionConfig executionConfig;
    private final AtomicBoolean firstFlag;
    private final ExecutorService executorService;
    private volatile TxnMergedToken latestFormatDescToken;
    private volatile boolean running;
    private BinlogXMergeItem lastMergeItem;
    private volatile Throwable error;

    public BinlogKWayMerger(String taskName, String streamName, List<Pair<String, String>> targetTaskAddress,
                            TxnMessageReceiver receiver, ExecutionConfig executionConfig, int flowControlWindowSize) {
        this.taskName = taskName;
        this.streamName = streamName;
        this.targetTaskAddress = targetTaskAddress;
        this.receiver = receiver;
        this.executionConfig = executionConfig;
        this.rpcClients = new HashMap<>();
        this.mergeSources = new ConcurrentHashMap<>();
        this.firstFlag = new AtomicBoolean(true);
        this.mergeController = new BinlogXMergeController();
        this.executorService = Executors.newCachedThreadPool(r -> new Thread(r, "binlog_stream_fetcher"));
        this.initRpcClient(flowControlWindowSize);
    }

    public void connect() {
        running = true;
        rpcClients.values().forEach(TxnStreamRpcClient::connect);
    }

    public void disconnect() {
        running = false;
        rpcClients.values().forEach(TxnStreamRpcClient::disconnect);
        executorService.shutdownNow();
    }

    public void setMetrics(StreamMetrics metrics) {
        metrics.setKwaySourceQueueSizeSupplier(() -> {
            Map<String, Integer> map = new HashMap<>();
            mergeSources.forEach((k, v) -> map.put(v.getDispatcherName(), v.getQeueuSize()));
            return map;
        });
    }

    public void dump(String startTso) {
        for (Map.Entry<String, TxnStreamRpcClient> entry : rpcClients.entrySet()) {
            executorService.submit(() -> {
                try {
                    int streamSeq = parseStreamSeq(streamName);
                    DumpRequest dumpRequest = DumpRequest.newBuilder()
                        .setDumperName(taskName)
                        .setTso(startTso)
                        .setStreamSeq(streamSeq)
                        .setVersion(executionConfig.getRuntimeVersion())
                        .setSubVersion(executionConfig.getSubRuntimeVersion()).build();
                    entry.getValue().dump(dumpRequest);
                } catch (Throwable e) {
                    error = new PolardbxException("dump from task error, target address :" + entry.getKey(), e);
                }
            });
        }

        while (running) {
            try {
                // 暂时采取一种简单粗暴的方式，只要发现异常，对所有链路进行重启
                if (error != null) {
                    throw error;
                }

                boolean skip = false;
                if (mergeSources.isEmpty()) {
                    try {
                        CommonUtils.sleep(SLEEP_TIME);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                if (lastMergeItem != null) {
                    BinlogXMergeItem mergeItem = lastMergeItem.getMergeSource().poll();
                    if (mergeItem == null) {
                        skip = true;
                    } else {
                        mergeController.push(mergeItem);
                    }
                } else {
                    for (Map.Entry<String, BinlogXMergeSource> entry : mergeSources.entrySet()) {
                        if (mergeController.contains(entry.getKey())) {
                            continue;
                        }

                        BinlogXMergeItem mergeItem = entry.getValue().poll();
                        if (mergeItem == null) {
                            skip = true;
                        } else {
                            mergeController.push(mergeItem);
                        }
                    }
                }

                if (skip) {
                    continue;
                }

                BinlogXMergeItem minItem = mergeController.pop();
                if (minItem == null) {
                    continue;
                }

                // 对TxnType为FORMAT_DESC类型的事务不做顺序验证，直接透传给下游
                if (lastMergeItem != null && minItem.compareTo(lastMergeItem) < 0
                    && minItem.getTxnToken().getType() != TxnType.FORMAT_DESC) {
                    log.error("detected disorderly tso，current tso is {}, last tso is {}",
                        minItem.getTso(), lastMergeItem.getTso());
                    throw new PolardbxException(
                        "detected disorderly tso，current tso is " + minItem.getTso()
                            + ",last tso is " + lastMergeItem.getTso());
                }

                send(minItem);
            } catch (Throwable t) {
                MonitorManager.getInstance().triggerAlarm(MERGER_STAGE_LOOP_ERROR, ExceptionUtils.getStackTrace(t));
                log.error("fatal error in k-way merger loop, the merger thread will exit", t);
                throw new PolardbxException("fatal error", t);
            }
        }

        log.info("Binlog KWay Merger started ...");
    }

    protected void initRpcClient(int flowControlWindowSize) {
        targetTaskAddress.forEach(address -> {
            // merge source
            BinlogXMergeSource mergeSource = new BinlogXMergeSource(address.getKey(), address.getValue());
            mergeSources.put(address.getValue(), mergeSource);

            // rpc client
            NettyChannelBuilder channelBuilder = (NettyChannelBuilder) ManagedChannelBuilder
                .forTarget(address.getValue()).usePlaintext();
            TxnStreamRpcClient rpcClient =
                new TxnStreamRpcClient(channelBuilder, new BinlogXMergeSourceReceiver(mergeSource), false,
                    0, flowControlWindowSize);
            rpcClients.put(address.getValue(), rpcClient);
        });
    }

    protected void send(BinlogXMergeItem mergeItem) throws InterruptedException {
        TxnMergedToken currentToken = mergeItem.txnToken;
        TxnMergedToken lastToken = lastMergeItem != null ? lastMergeItem.txnToken : null;

        if (currentToken.getType() == TxnType.FORMAT_DESC) {
            latestFormatDescToken = currentToken;
            return;
        }

        if (firstFlag.compareAndSet(true, false)) {
            sendTag(latestFormatDescToken.getTso(), latestFormatDescToken);
        }

        String currentPureTso = mergeItem.getPureTso();
        String lastPureTso = lastMergeItem != null ? lastMergeItem.getPureTso() : "";
        if (!StringUtils.equals(currentPureTso, lastPureTso)) {
            if (lastToken != null && lastToken.getType() == TxnType.DML) {
                sendEnd();
            }

            if (currentToken.getType() == TxnType.DML) {
                sendBegin(currentPureTso, currentToken);
                sendData(mergeItem.getMessage());
            } else {
                trySendTag(currentPureTso, lastPureTso, currentToken);
            }
        } else {
            if (currentToken.getType() == TxnType.DML) {
                sendData(mergeItem.getMessage());
            } else {
                trySendTag(currentPureTso, lastPureTso, currentToken);
            }
        }

        lastMergeItem = mergeItem;
    }

    protected void trySendTag(String currentPureTso, String lastPureTso, TxnMergedToken token)
        throws InterruptedException {
        String currentActualTso = getActualTso(currentPureTso);
        String lastActualTso = StringUtils.isNotBlank(lastPureTso) ? getActualTso(lastPureTso) : "";
        if (!StringUtils.equals(currentActualTso, lastActualTso)) {
            sendTag(currentPureTso, token);
        }
    }

    protected void sendBegin(String pureTso, TxnMergedToken token)
        throws InterruptedException {
        TxnBegin txnBegin = TxnBegin.newBuilder().setTxnMergedToken(token.toBuilder().setTso(pureTso).build()).build();
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.BEGIN).setTxnBegin(txnBegin).build();
        receiver.onReceived(Lists.newArrayList(message));
    }

    protected void sendData(TxnMessage messageInput) throws InterruptedException {
        TxnMessage message =
            TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(messageInput.getTxnData()).build();
        receiver.onReceived(Lists.newArrayList(message));
    }

    protected void sendEnd() throws InterruptedException {
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.END).build();
        receiver.onReceived(Lists.newArrayList(message));
    }

    protected void sendTag(String pureTso, TxnMergedToken token) throws InterruptedException {
        TxnTag txnTag = TxnTag.newBuilder().setTxnMergedToken(token.toBuilder().setTso(pureTso)).build();
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
        receiver.onReceived(Lists.newArrayList(message));
    }

    protected static class BinlogXMergeSource {
        private final String dispatcherName;
        private final String target;
        private final ArrayBlockingQueue<TxnMessage> queue;

        public BinlogXMergeSource(String dispatcherName, String target) {
            this.dispatcherName = dispatcherName;
            this.target = target;
            this.queue = new ArrayBlockingQueue<>(DynamicApplicationConfig.getInt(BINLOGX_KWAY_SOURCE_QUEUE_SIZE));
        }

        public void push(TxnMessage message) throws InterruptedException {
            this.queue.put(message);
        }

        public BinlogXMergeItem poll() throws InterruptedException {
            TxnMessage message = this.queue.poll(1, TimeUnit.MILLISECONDS);
            return message == null ? null : new BinlogXMergeItem(target, message, this);
        }

        public String getDispatcherName() {
            return dispatcherName;
        }

        public int getQeueuSize() {
            return this.queue.size();
        }
    }

    protected static class BinlogXMergeSourceReceiver implements TxnMessageReceiver {
        private final BinlogXMergeSource mergeSource;

        public BinlogXMergeSourceReceiver(BinlogXMergeSource mergeSource) {
            this.mergeSource = mergeSource;
        }

        @Override
        public void onReceived(List<TxnMessage> messages) throws InterruptedException {
            for (TxnMessage message : messages) {
                mergeSource.push(message);
            }
        }
    }

    protected static class BinlogXMergeItem implements Comparable<BinlogXMergeItem> {
        /**
         * 也就是DispatcherName，目前与container ID绑定
         */
        private final String sourceId;
        private final BinlogXMergeSource mergeSource;
        private final TxnMessage message;
        private final TxnMergedToken txnToken;
        private final String tso;
        private final String pureTso;
        private final String traceId;
        private final String firstPartitionId;

        public BinlogXMergeItem(String sourceId, TxnMessage message, BinlogXMergeSource mergeSource) {
            this.sourceId = sourceId;
            this.message = message;
            this.mergeSource = mergeSource;

            if (message.getType() == MessageType.TAG) {
                this.txnToken = message.getTxnTag().getTxnMergedToken();
            } else if (message.getType() == MessageType.WHOLE) {
                this.txnToken = message.getTxnBegin().getTxnMergedToken();
            } else {
                throw new PolardbxException("unsupported message type " + message.getType());
            }
            this.tso = txnToken.getTso();

            // Decompose TSO into (pureTso, traceId) for deterministic comparison.
            // DML TSO format: pureTso_traceId_subSeq; non-DML TSO: just pureTso.
            String[] parts = tso.split("_");
            this.pureTso = parts[0];
            this.traceId = parts.length > 1 ? parts[1] : "";

            // Extract first TxnItem's partitionId for DML messages.
            // WriteBuffer flushes on each TABLE_MAP, so all TxnItems in one message share the same partitionId.
            if (message.getType() == MessageType.WHOLE
                && message.getTxnData().getTxnItemsCount() > 0) {
                this.firstPartitionId = message.getTxnData().getTxnItems(0).getPartitionId();
                if (DynamicApplicationConfig.getBoolean(BINLOGX_KWAY_PARTITION_ID_CHECK_ENABLED, false)) {
                    checkPartitionIdConsistency();
                }
            } else {
                this.firstPartitionId = null;
            }
        }

        private void checkPartitionIdConsistency() {
            List<TxnItem> items = message.getTxnData().getTxnItemsList();
            for (int i = 1; i < items.size(); i++) {
                String pid = items.get(i).getPartitionId();
                if (!StringUtils.equals(firstPartitionId, pid)) {
                    throw new PolardbxException(
                        "inconsistent partitionId in TxnItems, tso=" + tso
                            + ", firstPartitionId=" + firstPartitionId
                            + ", items[" + i + "].partitionId=" + pid);
                }
            }
        }

        public String getSourceId() {
            return sourceId;
        }

        public TxnMessage getMessage() {
            return message;
        }

        public BinlogXMergeSource getMergeSource() {
            return mergeSource;
        }

        public TxnMergedToken getTxnToken() {
            return txnToken;
        }

        public String getTso() {
            return tso;
        }

        public String getPureTso() {
            return pureTso;
        }

        /**
         * Compare by (pureTso, traceId, partitionId) to match upstream TxnBuffer.mergeTwoSortList ordering.
         * subSeq is Dispatcher-local and must NOT participate in cross-Dispatcher ordering.
         */
        @Override
        public int compareTo(BinlogXMergeItem o) {
            int ret = pureTso.compareTo(o.pureTso);
            if (ret == 0) {
                ret = traceId.compareTo(o.traceId);
            }
            if (ret == 0 && firstPartitionId != null && o.firstPartitionId != null) {
                ret = firstPartitionId.compareTo(o.firstPartitionId);
            }
            if (ret == 0) {
                ret = sourceId.compareTo(o.sourceId);
            }
            return ret;
        }
    }

    @Slf4j
    protected static class BinlogXMergeController {
        private final PriorityQueue<BinlogXMergeItem> priorityQueue = new PriorityQueue<>();
        private final Set<String> sourceIds = new HashSet<>();

        public boolean contains(String sourceId) {
            return sourceIds.contains(sourceId);
        }

        public void push(BinlogXMergeItem item) {
            if (log.isDebugEnabled()) {
                log.debug("push item {}", item);
            }

            if (sourceIds.contains(item.getSourceId())) {
                throw new PolardbxException("should not push duplicated item for source " + item.getSourceId());
            }

            priorityQueue.offer(item);
            sourceIds.add(item.getSourceId());
        }

        public BinlogXMergeItem pop() {
            if (log.isDebugEnabled()) {
                log.debug("pop item...");
            }

            BinlogXMergeItem item = priorityQueue.poll();
            if (log.isDebugEnabled()) {
                log.debug("pop item {}", item);
            }

            if (item == null) {
                log.error("no item exist.");
                return null;
            }

            sourceIds.remove(item.getSourceId());
            return item;
        }
    }
}
