/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.relay.Message;
import com.aliyun.polardbx.relay.MetaInfo;
import com.google.common.util.concurrent.RateLimiter;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import java.io.File;
import java.util.Arrays;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_RELAY_FILE_MAX_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_FILE_FLUSH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_SLOWDOWN_SPEED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_STOP_THRESHOLD;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.MAX_TSO;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayFileStoreEngine extends StoreEngineBase implements StoreEngine {
    private static final long RELAY_FILE_MAX_SIZE =
        DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_RELAY_FILE_MAX_SIZE);
    private static final long RELAY_FILE_FLUSH_INTERVAL =
        DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_FILE_FLUSH_INTERVAL_MS);
    private static final RelayFileCounter RELAY_FILE_COUNTER = new RelayFileCounter();

    private final RelayFileManager relayFileManager;
    private final Pair<byte[], byte[]> boundPair;
    private final RateLimiter rateLimiter;
    private RelayFile currentRelayFile;
    private WriteItem lastWriteItem;
    private boolean writeSlowDown;

    public RelayFileStoreEngine(RepoUnit metaRepoUnit, String persistPath, int streamSeq) {
        super(metaRepoUnit, streamSeq, persistPath);
        this.relayFileManager = new RelayFileManager(persistPath);
        this.boundPair = Pair.of(getMinMetaKeyBytes(), getMaxMetaKeyBytes());
        this.rateLimiter = RateLimiter.create(Integer.MAX_VALUE);
        this.writeSlowDown = false;
    }

    @Override
    public void appendInternal(WriteItem writeItem, byte[] data) {
        checkWriteSlowdown(data.length);
        checkWriteStop();
        tryFlush(writeItem);
        Message message = Message.newBuilder().setKey(UnsafeByteOperations.unsafeWrap(writeItem.getKey()))
            .setValue(UnsafeByteOperations.unsafeWrap(data)).build();
        currentRelayFile.writeData(message.toByteArray());
        if (currentRelayFile.writePointer() >= RELAY_FILE_MAX_SIZE) {
            currentRelayFile.close();
            currentRelayFile = relayFileManager.rotateRelayFile(currentRelayFile.getFile());
            RELAY_FILE_COUNTER.setCount(streamSeq, relayFileManager.listRelayFiles().size());
            metrics.getFileCount().set(RELAY_FILE_COUNTER.getCountByStream(streamSeq));
        }
        lastWriteItem = writeItem;
    }

    @Override
    public void open() {
        relayFileManager.init();
        RELAY_FILE_COUNTER.setCount(streamSeq, relayFileManager.listRelayFiles().size());
    }

    @Override
    public void close() {
        if (currentRelayFile != null) {
            currentRelayFile.close();
        }
    }

    @Override
    public void cleanInternal(String tso) {
        byte[] beginKey = getMetaKeyBytes(ORIGIN_TSO);
        byte[] endKey = getMetaKeyBytes(tso);
        Pair<byte[], byte[]> pair = metaRepoUnit.seekPre(endKey, boundPair);
        if (pair != null) {
            try {
                metaRepoUnit.put(ByteUtil.bytes(metaStreamMaxCleanTsoKey), ByteUtil.bytes(tso));
                MetaInfo metaInfo = MetaInfo.parseFrom(pair.getValue());
                relayFileManager.cleanRelayFilesBefore(metaInfo.getFileName());
                metaRepoUnit.deleteRange(beginKey, pair.getKey());
                RELAY_FILE_COUNTER.setCount(streamSeq, relayFileManager.listRelayFiles().size());
            } catch (InvalidProtocolBufferException | RocksDBException e) {
                throw new PolardbxException("relay data clean error for tso " + tso, e);
            }
        } else {
            log.warn("skip clean internal, because can not seek with key:{} between:{}", Arrays.toString(endKey),
                boundPair);
        }
    }

    @Override
    public String seekMaxTso() {
        Pair<String, MetaInfo> pair = getMetaInfo();
        if (relayFileManager.isEmpty() || pair == null) {
            relayFileManager.cleanAllRelayFiles();
            File file = relayFileManager.createFirstRelayFile();
            initMetaInfo(file.getName());
            currentRelayFile = relayFileManager.openAndSeekRelayFile(file.getName(), 0);
            return "";
        } else {
            MetaInfo metaInfo = pair.getValue();
            currentRelayFile = relayFileManager.openAndSeekRelayFile(metaInfo.getFileName(), metaInfo.getFilePos());
            return StringUtils.equals(getMinMetaKey(), pair.getKey()) ? "" :
                StringUtils.substringAfter(pair.getKey(), metaStreamTsoCheckPointPrefix);
        }
    }

    @Override
    public RelayDataReader newRelayDataReader(byte[] beginKey) {
        return new RelayFileDataReader(this, metrics, beginKey);
    }

    public MetaInfo searchCheckpointTso(String tso) {
        try {
            byte[] key = getMetaKeyBytes(tso);
            if (StringUtils.isBlank(tso) || StringUtils.equals(ORIGIN_TSO, tso)) {
                byte[] value = metaRepoUnit.get(key);
                return value == null || value.length == 0 ? null : MetaInfo.parseFrom(value);
            } else {
                Pair<byte[], byte[]> pair = metaRepoUnit.seekPre(key, boundPair);
                return pair == null ? null : MetaInfo.parseFrom(pair.getValue());
            }
        } catch (InvalidProtocolBufferException | RocksDBException e) {
            throw new PolardbxException("search tso checkpoint error , " + tso);
        }
    }

    RelayFileManager getRelayFileManager() {
        return relayFileManager;
    }

    String getCurrentWritingFile() {
        return currentRelayFile == null ? "" : currentRelayFile.getFileName();
    }

    private void tryFlush(WriteItem writeItem) {
        if (lastWriteItem != null && !StringUtils
            .equals(writeItem.getTxnToken().getTso(), lastWriteItem.getTxnToken().getTso())
            && System.currentTimeMillis() - currentRelayFile.getLastFlushTime() >= RELAY_FILE_FLUSH_INTERVAL) {
            currentRelayFile.flush();
            updateMetaInfo(lastWriteItem.getTxnToken().getTso());
        }
    }

    private void checkWriteSlowdown(int size) {
        int writeSlowDownThreshold = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD);
        if (RELAY_FILE_COUNTER.getTotalRelayFileCount() >= writeSlowDownThreshold) {
            if (!writeSlowDown) {
                int speed = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_SPEED);
                rateLimiter.setRate(speed);
                writeSlowDown = true;
                log.warn("trigger write slowdown with file count " + RELAY_FILE_COUNTER.getTotalRelayFileCount());
            }
        } else {
            if (writeSlowDown) {
                rateLimiter.setRate(Integer.MAX_VALUE);
                writeSlowDown = false;
                log.warn("cancel write slowdown with file count " + RELAY_FILE_COUNTER.getTotalRelayFileCount());
            }
        }
        rateLimiter.acquire(size);
    }

    @SneakyThrows
    private void checkWriteStop() {
        while (true) {
            int writeStopThreshold = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_STOP_THRESHOLD);
            if (RELAY_FILE_COUNTER.getTotalRelayFileCount() >= writeStopThreshold) {
                log.warn("trigger write stop with file count " + RELAY_FILE_COUNTER.getTotalRelayFileCount());
                Thread.sleep(1000);
            } else {
                break;
            }
        }
    }

    private void updateMetaInfo(String tso) {
        try {
            MetaInfo metaInfo = MetaInfo.newBuilder().setFileName(currentRelayFile.getFileName())
                .setFilePos(currentRelayFile.filePointer()).build();
            metaRepoUnit.put(getMetaKeyBytes(tso), metaInfo.toByteArray());
        } catch (RocksDBException e) {
            throw new PolardbxException("try flush error for stream " + streamSeq, e);
        }
    }

    private Pair<String, MetaInfo> getMetaInfo() {
        try {
            byte[] maxMetaKeyBytes = getMaxMetaKeyBytes();
            if (!metaRepoUnit.exists(maxMetaKeyBytes)) {
                return null;
            }
            Pair<byte[], byte[]> pair = metaRepoUnit.seekPre(maxMetaKeyBytes, boundPair);
            if (pair == null) {
                return null;
            } else {
                String key = new String(pair.getKey());
                if (!StringUtils.startsWith(key, metaStreamTsoCheckPointPrefix)) {
                    return null;
                }
                MetaInfo metaInfo = MetaInfo.parseFrom(pair.getValue());
                return Pair.of(key, metaInfo);
            }
        } catch (RocksDBException | InvalidProtocolBufferException e) {
            throw new PolardbxException("");
        }
    }

    private void initMetaInfo(String firstRelayFileName) {
        try {
            byte[] minMetaKeyBytes = getMinMetaKeyBytes();
            byte[] maxMetaKeyBytes = getMaxMetaKeyBytes();
            MetaInfo minMetaInfo = MetaInfo.newBuilder().setFileName(firstRelayFileName).setFilePos(0).build();
            MetaInfo maxMetaInfo = MetaInfo.newBuilder().setFileName(relayFileManager.getMaxFileName())
                .setFilePos(0).build();
            metaRepoUnit.put(minMetaKeyBytes, minMetaInfo.toByteArray());
            metaRepoUnit.put(maxMetaKeyBytes, maxMetaInfo.toByteArray());
        } catch (RocksDBException e) {
            throw new PolardbxException("init relay meta info failed!", e);
        }
    }

    private byte[] getMetaKeyBytes(String tso) {
        String metaKey = metaStreamTsoCheckPointPrefix + tso;
        return ByteUtil.bytes(metaKey);
    }

    private byte[] getMaxMetaKeyBytes() {
        return ByteUtil.bytes(getMaxMetaKey());
    }

    private byte[] getMinMetaKeyBytes() {
        return ByteUtil.bytes(getMinMetaKey());
    }

    private String getMaxMetaKey() {
        return metaStreamTsoCheckPointPrefix + MAX_TSO;
    }

    private String getMinMetaKey() {
        return metaStreamTsoCheckPointPrefix + ORIGIN_TSO;
    }
}
