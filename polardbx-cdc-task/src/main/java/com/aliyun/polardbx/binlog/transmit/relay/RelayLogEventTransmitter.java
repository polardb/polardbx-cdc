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
package com.aliyun.polardbx.binlog.transmit.relay;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.TransmitMetrics;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnTag;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.BinlogUploadStatusEnum.IGNORE;
import static com.aliyun.polardbx.binlog.BinlogUploadStatusEnum.SUCCESS;
import static com.aliyun.polardbx.binlog.CommonUtils.parsePureTso;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_ROCKS_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_READ_BATCH_BYTE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_READ_BATCH_ITEM_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_READ_LOG_DETAIL_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TRANSMIT_WRITE_BATCH_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_WAIT_LATEST_TSO_TIMEOUT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMITTER_DRYRUN;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMITTER_DRYRUN_MODE;
import static com.aliyun.polardbx.binlog.Constants.VERSION_PATH_PREFIX;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil.getTableIdLength;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;
import static com.aliyun.polardbx.binlog.transmit.relay.Constants.MDC_STREAM_SEQ;
import static com.aliyun.polardbx.binlog.transmit.relay.Constants.RELAY_DATA_FORCE_CLEAN_FLAG;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildMinRelayKeyStr;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayKeyUtil.buildPrimaryKeyString;
import static com.aliyun.polardbx.binlog.transmit.relay.WriteItem.buildTxnMergedToken;
import static com.aliyun.polardbx.binlog.util.TxnTokenUtil.cleanTxnBuffer4Token;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayLogEventTransmitter implements Transmitter {
    private static final XStreamMapper X_STREAM_MAPPER =
        SpringContextHolder.getObject(XStreamMapper.class);
    private static final BinlogOssRecordMapper OSS_RECORD_MAPPER =
        SpringContextHolder.getObject(BinlogOssRecordMapper.class);

    private static final Logger TRANSMIT_READ_LOGGER = LoggerFactory.getLogger("transmitReadLogger");

    private final String taskName;
    private final Storage storage;
    private final String taskBasePath;
    private final String persistPath;
    private final Map<Integer, StoreEngine> storeEngineMap;
    private final Map<Integer, String> streamMaxTsoMap;
    private final WriteBuffer writeBuffer;
    private final AtomicBoolean running;
    private final int streamCount;
    private final long runtimeVersion;
    private final boolean dryRun;
    private final int dryRunMode;
    private final RelayLogEventCleaner hashLogEventCleaner;
    private final ParallelDataWriter parallelDataWriter;
    private String startTso = "";
    private final Map<String, String> recoverTsoMap;
    private volatile TxnToken latestFormatDescToken;

    public RelayLogEventTransmitter(Storage storage, long runtimeVersion, Map<String, String> recoverTsoMap) {
        String basePath = DynamicApplicationConfig.getString(BINLOG_X_ROCKS_BASE_PATH);

        this.taskName = DynamicApplicationConfig.getString(TASK_NAME);
        this.storage = storage;
        this.runtimeVersion = runtimeVersion;
        this.recoverTsoMap = recoverTsoMap;
        this.taskBasePath = basePath + File.separator + taskName + File.separator;
        this.persistPath = taskBasePath + VERSION_PATH_PREFIX + runtimeVersion;
        this.storeEngineMap = new HashMap<>();
        this.streamMaxTsoMap = new ConcurrentHashMap<>();
        this.writeBuffer = new WriteBuffer();
        this.dryRun = DynamicApplicationConfig.getBoolean(TASK_TRANSMITTER_DRYRUN);
        this.dryRunMode = DynamicApplicationConfig.getInt(TASK_TRANSMITTER_DRYRUN_MODE);
        this.running = new AtomicBoolean(false);
        this.streamCount = DynamicApplicationConfig.getInt(BINLOG_X_STREAM_COUNT);
        this.hashLogEventCleaner = new RelayLogEventCleaner(this);
        this.parallelDataWriter = new ParallelDataWriter(i -> {
            if (dryRun && dryRunMode == 1) {
                return;
            }

            String maxTso = streamMaxTsoMap.get(i.getStreamSeq());
            if (StringUtils.isNotBlank(maxTso) && i.getTxnToken().getTso().compareTo(maxTso) <= 0) {
                if (log.isDebugEnabled()) {
                    log.debug("skip key {} which is lower than init max tso {} for stream {}.", i.keyStr, maxTso,
                        i.streamSeq);
                }
                return;
            }

            StoreEngine r = storeEngineMap.get(i.getStreamSeq());
            try {
                r.append(i);
            } catch (Throwable e) {
                throw new PolardbxException("put failed for stream " + i.getStreamSeq(), e);
            }
        });
        this.init();
    }

    private void init() {
        try {
            RocksDB.loadLibrary();
            tryCleanDirectory();

            for (int i = 0; i < streamCount; i++) {
                StoreEngine storeEngine = StoreEngineManager.newInstance(persistPath, i);
                storeEngineMap.put(i, storeEngine);
                storeEngine.open();
            }
            buildStartTso();

            hashLogEventCleaner.setStoreEngines(storeEngineMap);
        } catch (Throwable e) {
            releaseResource();
            throw new PolardbxException("Open Repository failed.", e);
        }
    }

    private void buildStartTso() {
        String streamGroupName = DynamicApplicationConfig.getString(BINLOG_X_STREAM_GROUP_NAME);
        List<String> streamsList = X_STREAM_MAPPER.select(
            s -> s.where(XStreamDynamicSqlSupport.groupName, isEqualTo(streamGroupName))
                .orderBy(XStreamDynamicSqlSupport.streamName)).stream().map(
            XStream::getStreamName).collect(Collectors.toList());
        if (streamsList.size() != streamCount) {
            throw new PolardbxException("find mismatched stream count, configuration count is " + streamCount
                + ", count in binlog_x_stream table is " + streamsList.size());
        }

        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<?>> futureList = new ArrayList<>();
        streamsList.forEach(streamName -> {
            futureList.add(executor.submit(() -> setMaxTsoForStream(streamName)));
        });
        futureList.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        executor.shutdownNow();

        for (Map.Entry<Integer, String> entry : streamMaxTsoMap.entrySet()) {
            if (StringUtils.isBlank(entry.getValue())) {
                // 只要有一个为空，则所有为空
                startTso = "";
                break;
            } else {
                String tso = entry.getValue();
                if (StringUtils.isBlank(startTso)) {
                    startTso = tso;
                } else {
                    int c = startTso.compareTo(tso);
                    if (c > 0) {
                        startTso = tso;
                    }
                }
            }
        }

        storeEngineMap.get(0).setOriginStartTso(startTso);
        log.info("build start tso from store engine, start tso is [{}]", startTso);
    }

    private void setMaxTsoForStream(String streamName) {
        int streamSeq = Integer.parseInt(StringUtils.substringAfterLast(streamName, "_"));
        StoreEngine storeEngine = storeEngineMap.get(streamSeq);
        String maxTso = storeEngine.seekMaxTso();
        if (StringUtils.isBlank(maxTso)) {
            log.info("max tso for stream [{}] is empty, will try to get start tso by cursor.", streamSeq);
            String startTsoByCursor = getStartTsoByStream(streamName);
            streamMaxTsoMap.put(streamSeq, startTsoByCursor);
            log.info("max tso for stream [{}] is [{}], by cursor.", streamSeq, startTsoByCursor);
        } else {
            streamMaxTsoMap.put(streamSeq, maxTso);
            log.info("max tso for stream [{}] is [{}], by seek max tso.", streamSeq, maxTso);
        }
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            hashLogEventCleaner.start();
            parallelDataWriter.start();
            log.info("transmitter with hash mode is started.");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                parallelDataWriter.stop();
                releaseResource();
                hashLogEventCleaner.stop();
            } catch (Exception e) {
                throw new PolardbxException("Close Repository failed.", e);
            }
            log.info("transmitter with hash mode is stopped.");
        }
    }

    @Override
    public void transmit(MessageEvent messageEvent) {
        TxnToken txnToken = messageEvent.getToken();
        TransmitMetrics.get().setDelayTimeOnTransmit(System.currentTimeMillis() - messageEvent.getTsoTimestamp());

        if (dryRun && dryRunMode == 0) {
            cleanTxnBuffer4Token(messageEvent.getToken(), storage);
            return;
        }

        if (txnToken.getType() == TxnType.FORMAT_DESC) {
            log.info("received a format_desc txn token in log event transmitter, tso is :" + txnToken.getTso());
            latestFormatDescToken = txnToken;
            return;
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

            save(txnToken, buffer);
        } else {
            save(txnToken);
        }
    }

    @Override
    public boolean checkTSO(String startTSO, TxnOutputStream<DumpReply> outputStream, boolean keepWaiting) {
        return true;
    }

    @Override
    public void dump(String startTSO, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        final AtomicBoolean first = new AtomicBoolean(true);
        startTSO = StringUtils.isBlank(startTSO) ? ORIGIN_TSO : startTSO;

        final int streamSeq = outputStream.getStreamSeq();
        byte[] searchFromKey = RelayKeyUtil.buildMinRelayKey(startTSO);
        int transmitReadItemSize = DynamicApplicationConfig.getInt(BINLOG_X_TRANSMIT_READ_BATCH_ITEM_SIZE);
        long transmitReadByteSize = DynamicApplicationConfig.getLong(BINLOG_X_TRANSMIT_READ_BATCH_BYTE_SIZE);
        RelayDataReader relayDataReader = storeEngineMap.get(streamSeq).newRelayDataReader(searchFromKey);

        try {
            MDC.put(MDC_STREAM_SEQ, streamSeq + "");
            while (running.get()) {
                // 增加反压控制判断
                if (!outputStream.tryWait()) {
                    continue;
                }

                List<Pair<byte[], byte[]>> dataList = new ArrayList<>();
                if (latestFormatDescToken == null) {
                    log.warn("Latest format_desc token is not ready, will try later.");
                    CommonUtils.sleep(1000);
                } else {
                    // 新注册上来的客户端，发送的首个Token必须是Format_Desc
                    if (first.compareAndSet(true, false)) {
                        sendFormatDesc(latestFormatDescToken, outputStream);
                        continue;
                    } else {
                        dataList = relayDataReader.getData(transmitReadItemSize, transmitReadByteSize);
                    }
                }

                // 1. 尽早发现流是否已经出现了异常
                if (dataList.isEmpty()) {
                    outputStream.checkState();
                    CommonUtils.sleep(10);
                    continue;
                }

                for (Pair<byte[], byte[]> pair : dataList) {
                    try {
                        String tso = new String(pair.getKey());
                        if (parsePureTso(tso).compareTo(startTSO) <= 0) {
                            continue;
                        }

                        if (outputStream.tryWait()) {
                            TxnMessage message = TxnMessage.parseFrom(pair.getValue());
                            outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
                            logReadDetail(streamSeq, tso, message);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        throw new PolardbxException("send data failed", e);
                    }
                }

            }
        } finally {
            MDC.remove(MDC_STREAM_SEQ);
            if (relayDataReader != null) {
                relayDataReader.close();
            }
        }
    }

    // 本地rocksdb中查询不到start_tso信息，有两种情况：首次启动和发生了rebalance。这两种情况的处理方式是一样的，通过binlog_x_stream的
    // latest_cursor字段来获取对应流的latest tso作为start tso，dumper在启动之后会通过心跳把已经持久化到binlog文件的tso记录到latest_cursor
    // 字段。但这种方式存在一个DownSide：如果有某个DumperX因为某种原因迟迟不能启动，会导致Dispatcher一直启动失败，进而导致全局所有Stream都不能正常
    // 工作，影响SLA。针对这种情况，我们进行等待超时后的策略转换，如果从binlog_x_stream迟迟获取不到latest tso信息，则降级为从binlog_oss_record
    // 表获取checkpoint tso作为start tso，checkpoint tso相对于latest tso距离当前的时间会更久一些，也就是说要回溯更多的物理binlog。从概率上来说，
    // 获取到的start tso距离当前时间越久，物理binlog中找不到该tso的概率也会越大，但这个概率是比较可控的，大不了找不到位点报错，整个集群暂时不可用
    private String getStartTsoByStream(String streamName) {
        long start = System.currentTimeMillis();
        while (true) {
            Optional<XStream> optional = X_STREAM_MAPPER.selectOne(
                s -> s.where(XStreamDynamicSqlSupport.streamName, isEqualTo(streamName)));
            if (optional.isPresent()) {
                String cursorStr = optional.get().getLatestCursor();
                if (StringUtils.isNotBlank(cursorStr)) {
                    Cursor cursor = JSONObject.parseObject(optional.get().getLatestCursor(), Cursor.class);
                    if (cursor.getVersion() != null && cursor.getVersion() == runtimeVersion) {
                        log.info("successfully get latest tso {} for stream {}", cursor.getTso(), streamName);
                        return cursor.getTso() == null ? "" : cursor.getTso();
                    }
                }
            }

            try {
                long timeout = DynamicApplicationConfig.getLong(BINLOG_X_WAIT_LATEST_TSO_TIMEOUT);
                if (System.currentTimeMillis() - start > 1000 * timeout) {
                    log.warn("wait for latest tso timeout with stream " + streamName
                        + " , will switch to get checkpoint tso");
                    break;
                }
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

        String snapshotTso = getSnapshotTsoForStream(streamName);
        String recoverTso = getRecoverTsoForStream(streamName);
        return StringUtils.compare(recoverTso, snapshotTso) < 0 ? recoverTso : snapshotTso;
    }

    private String getSnapshotTsoForStream(String streamName) {
        String snapshotTso = null;
        BinlogOssRecord record = getCheckpointTsoFromBackup(streamName);
        if (record != null) {
            log.info("successfully get check point tso {} for stream {}", record.getLastTso(), streamName);
            snapshotTso = record.getLastTso();
        }
        return ObjectUtils.defaultIfNull(snapshotTso, "");
    }

    private String getRecoverTsoForStream(String streamName) {
        String recoverTso = null;
        if (recoverTsoMap != null) {
            recoverTso = recoverTsoMap.get(streamName);
            log.info("successfully get recover tso {} for stream {}", recoverTso, streamName);
        }
        return ObjectUtils.defaultIfNull(recoverTso, "");
    }

    BinlogOssRecord getCheckpointTsoFromBackup(String streamName) {
        List<BinlogOssRecord> list = OSS_RECORD_MAPPER.select(
            s -> s.where(BinlogOssRecordDynamicSqlSupport.streamId, isEqualTo(streamName))
                .orderBy(BinlogOssRecordDynamicSqlSupport.binlogFile.descending()));
        if (!list.isEmpty()) {
            BinlogOssRecord result = null;
            //获取最后一个没有空洞的、上传状态为SUCCESS或者IGNOGE的记录作为checkpoint
            for (BinlogOssRecord record : list) {
                Integer uploadStatus = record.getUploadStatus();
                boolean validStatus = uploadStatus == SUCCESS.getValue() || uploadStatus == IGNORE.getValue();
                if (StringUtils.isNotBlank(record.getLastTso()) && validStatus) {
                    if (result == null) {
                        result = record;
                    }
                } else {
                    result = null;
                }
            }
            return result;
        }
        return null;
    }

    private void logReadDetail(int streamSeq, String keyStr, TxnMessage txnMessage) {
        boolean logDetailEnable = DynamicApplicationConfig.getBoolean(BINLOG_X_TRANSMIT_READ_LOG_DETAIL_ENABLE);
        if (logDetailEnable) {
            if (txnMessage.getType() == MessageType.WHOLE) {
                List<TxnItem> txnItems = txnMessage.getTxnData().getTxnItemsList();
                txnItems.forEach(t -> {
                    List<ByteString> primaryKeyList = t.getPrimaryKeyList();
                    TRANSMIT_READ_LOGGER.info("type:dml | stream:{} | schema:{} | table:{} | save key:{} | hash key:{}"
                            + " | pk str:{} | event type:{} | event size:{}", streamSeq, t.getSchema(), t.getTable(),
                        keyStr, t.getHashKey(), buildPrimaryKeyString(primaryKeyList), t.getEventType(),
                        t.getPayload().size());
                });
            } else if (txnMessage.getType() == MessageType.TAG) {
                TRANSMIT_READ_LOGGER.info("type:broadcast | stream:{} | save key:{}", streamSeq, keyStr);
            }
        }
    }

    private void tryCleanDirectory() throws IOException {
        //如果当前版本对应的目录存在，直接使用即可
        //如果当前版本对应的目录不存在，则直接新建新的
        //如果有强制清理的指令，则强制清理
        if (!new File(persistPath).exists()) {
            FileUtils.forceMkdir(new File(persistPath));
            FileUtils.cleanDirectory(new File(persistPath));
            log.info("data is not existing, directory is created for path {}.", persistPath);
        } else {
            if (new File(persistPath + "/" + RELAY_DATA_FORCE_CLEAN_FLAG).exists()) {
                FileUtils.forceMkdir(new File(persistPath));
                FileUtils.cleanDirectory(new File(persistPath));
                log.info("force clean directory with path {}.", persistPath);
            } else {
                log.info("data is already existing, no need to create directory for path {}", persistPath);
            }
        }
        //清理旧版本数据
        File baseDir = new File(taskBasePath);
        File[] files = baseDir.listFiles(
            (dir, name) -> StringUtils.startsWith(name, VERSION_PATH_PREFIX) && !StringUtils
                .equals(name, VERSION_PATH_PREFIX + runtimeVersion));
        if (files != null) {
            Arrays.stream(files).forEach(f -> {
                try {
                    FileUtils.forceDelete(f);
                    log.info("relay data directory with old version {} is cleaned.", f.getAbsolutePath());
                } catch (IOException e) {
                    throw new PolardbxException("delete relay data directory failed, " + f.getName(), e);
                }
            });
        }
    }

    private void save(TxnToken token, TxnBuffer buffer) {
        writeBuffer.reset(token);
        String currentTraceId = null;
        Iterator<TxnItemRef> iterator = buffer.parallelRestoreIterator();

        while (iterator.hasNext()) {
            TxnItemRef txnItemRef = iterator.next();

            // build txnItem
            EventData eventData = txnItemRef.getEventData();
            List<ByteString> primaryKeyList = buildPrimaryKeys(txnItemRef);

            TxnItem txnItem = TxnItem.newBuilder()
                .setTraceId(txnItemRef.getTraceId())
                .setRowsQuery(eventData.getRowsQuery())
                .setEventType(txnItemRef.getEventType())
                .setPayload(eventData.getPayload())
                .setSchema(eventData.getSchemaName())
                .setTable(eventData.getTableName())
                .setHashKey(txnItemRef.getHashKey())
                .addAllPrimaryKey(primaryKeyList)
                .build();

            // check for trace id rotation
            if (!StringUtils.equals(currentTraceId, txnItemRef.getTraceId())) {
                if (txnItemRef.getEventType() != TABLE_MAP_EVENT) {
                    throw new PolardbxException("trace id rotation must happen with table map event! txn token is "
                        + token + ",  txn item is " + txnItem);
                }
                currentTraceId = txnItemRef.getTraceId();
            }

            // reset table map
            if (txnItemRef.getEventType() == TABLE_MAP_EVENT) {
                writeBuffer.putTableMapEvent(currentTraceId, txnItem);
            } else {
                writeBuffer.putRowEvent(currentTraceId, txnItem);
            }

            txnItemRef.clearEventData();//尽快释放内存空间，防止堆内存溢出
        }

        writeBuffer.flush();
        cleanTxnBuffer4Token(token, storage);
    }

    private List<ByteString> buildPrimaryKeys(TxnItemRef txnItemRef) {
        if (txnItemRef.getPrimaryKey() == null || txnItemRef.getPrimaryKey().isEmpty()) {
            return Lists.newArrayList();
        } else {
            List<ByteString> list = new ArrayList<>(txnItemRef.getPrimaryKey().size());
            txnItemRef.getPrimaryKey().forEach(k -> list.add(UnsafeByteOperations.unsafeWrap(k)));
            return list;
        }
    }

    private void save(TxnToken token) {
        if (token.getType() == TxnType.META_DDL) {
            HashConfig.tryReloadTableStreamMapping(token);
            HashLevel hashLevel = HashConfig.getHashLevel(token.getSchema(), token.getTable());

            // 对于库级别的ddl操作(建库和删除)，需要采用广播模式，原因如下：
            // 某个库的HashLevel是DATABASE，但是其中某张表的HashLevel是RECORD或者TABLE，需要保证对应流上有对应的库
            if (hashLevel != HashLevel.RECORD && StringUtils.isNotBlank(token.getTable())) {
                int streamSeq = HashConfig.getStreamSeq(token.getSchema(), token.getTable(), -1);
                parallelDataWriter.write(new WriteItem(streamSeq, token, null, null, null));
                return;
            }
        }
        for (int i = 0; i < storeEngineMap.size(); i++) {
            parallelDataWriter.write(new WriteItem(i, token, null, null, null));
        }
    }

    private void saveInSingleStreamMode(int streamSeq, TxnToken txnToken, String traceId, long subSeq,
                                        List<TxnItem> itemList) {
        parallelDataWriter.write(new WriteItem(streamSeq, txnToken, traceId, subSeq, itemList));
    }

    private void sendFormatDesc(TxnToken token, TxnOutputStream<DumpReply> outputStream) {
        String newTso = buildMinRelayKeyStr(token.getTso());
        TxnTag txnTag = TxnTag.newBuilder().setTxnMergedToken(buildTxnMergedToken(token, newTso)).build();
        TxnMessage message = TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
        outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
    }

    private void releaseResource() {
        storeEngineMap.values().forEach(u -> {
            try {
                u.close();
            } catch (Throwable throwable) {
                //do nothing
            }
        });
    }

    public String getStartTso() {
        return startTso;
    }

    private class WriteBuffer {
        private final Map<Integer, List<TxnItem>> bufferMap;
        private final List<TxnItem> currentTableMapTxnItems;
        private final int batchSize;
        private TxnToken txnToken;
        private String traceId;
        private AtomicLong subSeq;

        WriteBuffer() {
            this.bufferMap = new TreeMap<>();
            this.currentTableMapTxnItems = new ArrayList<>();
            this.batchSize = DynamicApplicationConfig.getInt(BINLOG_X_TRANSMIT_WRITE_BATCH_SIZE);
        }

        void putRowEvent(String traceId, TxnItem txnItem) {
            if (!StringUtils.equals(this.traceId, traceId)) {
                String message = String.format("meet different trace id for same write buffer, init trace id "
                        + "is %s, received trace id is %s, and current token is %s, and txn item is %s.",
                    this.traceId, traceId, txnToken, txnItem);
                throw new PolardbxException(message);
            }
            if (txnItem.getEventType() == TABLE_MAP_EVENT) {
                throw new PolardbxException("can`t push table map event to write buffer.");
            }

            // save txnItem
            int streamSeq = calcStreamSeq(txnItem);
            if (bufferMap.containsKey(streamSeq)) {
                //如果发生了event_type的变化，则需要flush一次
                List<TxnItem> list = bufferMap.get(streamSeq);
                if (!list.isEmpty() && list.get(list.size() - 1).getEventType() != TABLE_MAP_EVENT
                    && list.get(list.size() - 1).getEventType() != txnItem.getEventType()) {
                    flushOnce(streamSeq, list);
                    bufferMap.remove(streamSeq);
                }

                //如果list size大于配置的batch size，则需要flush一次，防止内存不够用
                if (list.size() > batchSize) {
                    flushOnce(streamSeq, list);
                    bufferMap.remove(streamSeq);
                }
            }

            bufferMap.computeIfAbsent(streamSeq, k -> {
                if (currentTableMapTxnItems.size() > 1) {
                    return currentTableMapTxnItems.stream().filter(m -> {
                        HashLevel hashLevel = HashConfig.getHashLevel(m.getSchema(), m.getTable());
                        if (hashLevel != HashLevel.RECORD) {
                            int streamSeqTmp = HashConfig.getStreamSeq(m.getSchema(), m.getTable(), -1);
                            return streamSeqTmp == k;
                        } else {
                            return true;
                        }
                    }).collect(Collectors.toList());
                } else {
                    return new ArrayList<>(currentTableMapTxnItems);
                }
            });
            bufferMap.get(streamSeq).add(txnItem);
        }

        void putTableMapEvent(String traceId, TxnItem tableMapTxnItem) {
            // 如果前序bufferMap不为空，对前序数据进行flush
            // 如果前序bufferMap为空，有两种可能：一种是该WriteBuffer被reset后首次收到table_map_event；另一种可能是收到了连续的table_map_event
            // 收到连续table_map_event的例子，参见：https://yuque.antfin-inc.com/coronadb/knddog/axy7ge#JdRXS
            if (!bufferMap.isEmpty()) {
                flush();
            }

            currentTableMapTxnItems.add(tableMapTxnItem);
            if (!StringUtils.equals(traceId, this.traceId)) {
                this.traceId = traceId;
                this.subSeq = new AtomicLong(0L);
            }
        }

        void reset(TxnToken txnToken) {
            this.txnToken = txnToken;
            this.bufferMap.clear();
            this.currentTableMapTxnItems.clear();
            this.traceId = null;
            this.subSeq = null;
        }

        void flush() {
            this.bufferMap.forEach(this::flushOnce);
            this.bufferMap.clear();
            this.currentTableMapTxnItems.clear();
        }

        void flushOnce(int k, List<TxnItem> v) {
            TxnItem txnItem = v.remove(v.size() - 1);
            byte[] payload = DirectByteOutput.unsafeFetch(txnItem.getPayload());
            rewriteFlag(payload);
            txnItem = txnItem.toBuilder().setPayload(UnsafeByteOperations.unsafeWrap(payload)).build();
            v.add(txnItem);
            saveInSingleStreamMode(k, txnToken, traceId, subSeq.getAndIncrement(), v);
        }

        void rewriteFlag(byte[] bytes) {
            int flag = 1;
            int tableIdLen = getTableIdLength();
            int pos = 19 + tableIdLen;
            for (int i = 0; i < 2; ++i) {
                byte b = ((byte) ((flag >> (i << 3)) & 0xff));
                bytes[pos++] = b;
            }
        }

        int calcStreamSeq(TxnItem txnItem) {
            if (StringUtils.isBlank(txnItem.getSchema())) {
                throw new PolardbxException("schema is null for txn item " + txnItem);
            }
            if (StringUtils.isBlank(txnItem.getTable())) {
                throw new PolardbxException("table is null for txn item" + txnItem);
            }
            return HashConfig.getStreamSeq(txnItem.getSchema(), txnItem.getTable(), txnItem.getHashKey());
        }
    }
}
