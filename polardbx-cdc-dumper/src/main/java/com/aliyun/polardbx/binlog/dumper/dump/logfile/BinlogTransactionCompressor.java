/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.BatchEventToken;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.EventData;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.github.luben.zstd.Zstd;
import com.aliyun.polardbx.binlog.enums.CompressionType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TRANSACTION_PAYLOAD;

/**
 * @author zm
 */
@Slf4j
public class BinlogTransactionCompressor {
    @Setter
    private EventData data;
    @Setter
    private CompressionType compressionType;
    @Getter
    private int compressionBegin;
    @Setter
    private int compressionEnd;
    @Getter
    private final List<Integer> compressionBeginTokenIdxs = new ArrayList<>();
    @Getter
    private final List<Integer> compressionEndTokenIdxs = new ArrayList<>();
    @Getter
    private int compressionSize;
    @Getter
    private int uncompressedSize;
    @Getter
    private boolean prepared;
    private int compressionLevel = 1;
    private long compressBeginTime;

    public void init(EventData data) {
        this.data = data;
    }

    public void prepare(int compressionBegin, CompressionType type, int startTokenIdx, int compressionLevel) {
        this.compressionBegin = compressionBegin;
        this.compressionType = type;
        this.compressionLevel = compressionLevel;
        compressionBeginTokenIdxs.add(startTokenIdx);
        prepared = true;
    }

    /**
     * 压缩(begin,end)区间的数据到token的data属性中。
     * 在压缩完成后，原data在(begin,end)区间的数据将被清理。
     */
    public void compress(SingleEventToken commitToken, int endTokenIdx) {
        // 初始化
        compressBeginTime = System.currentTimeMillis();
        compressionEnd = commitToken.getOffset() + commitToken.getLength();
        compressionEndTokenIdxs.add(endTokenIdx);
        SingleEventToken binlogTransactionPayloadToken = commitToken;
        // 将table id放入token中，以在最终写入binlog时更新binlog的最大table id
        // 也方便检查table id是不是上一个文件时build写入的
        binlogTransactionPayloadToken.setCompressionLevel(compressionLevel);
        binlogTransactionPayloadToken.setCompressionType(compressionType);

        binlogTransactionPayloadToken.setType(TRANSACTION_PAYLOAD);
        binlogTransactionPayloadToken.setUseTokenData(true);
        uncompressedSize = compressionEnd - compressionBegin;

        // 将范围内的数据压缩
        byte[] compressionData = compress(data.getData(), compressionBegin, compressionEnd);
        compressionSize = compressionData.length;

        // 计算event size，并据此设置nextPos以及创建tokenData
        int eventSize = getEventSize(compressionType, compressionSize, uncompressedSize);
        binlogTransactionPayloadToken.setLength(eventSize);
        byte[] tokenData = new byte[eventSize];

        // 构建整个event，包括payload(压缩后数据)
        Pair<byte[], Integer>
            dataAndLength = EventGenerator.makeTransactionPayload(
            binlogTransactionPayloadToken.getTsoTimeSecond(), binlogTransactionPayloadToken.getServerId(),
            binlogTransactionPayloadToken.getNextPosition(), tokenData, compressionData,
            compressionType, compressionSize, uncompressedSize
        );

        // 更新checksum
        EventGenerator.updateChecksum(dataAndLength.getLeft(), 0, dataAndLength.getRight());
        binlogTransactionPayloadToken.setData(dataAndLength.getLeft());

        // 这之后的token的nextPos都会有偏移
        // endPosOffset += binlogTransactionPayloadToken.getNextPosition() - originEndPos;

        // 清理压缩前数据
        updateCompressionStatistics();
        cleanEventData();

        // 还原配置
        compressionType = CompressionType.NONE;
        prepared = false;
    }

    public byte[] compress(byte[] data, int begin, int end) {
        if (compressionType == CompressionType.NONE) {
            return Arrays.copyOfRange(data, begin, end);
        } else if (compressionType == CompressionType.ZSTD) {
            byte[] rangeToCompress = Arrays.copyOfRange(data, begin, end);
            return Zstd.compress(rangeToCompress, compressionLevel);
        } else {
            throw new UnsupportedOperationException("unsupported compression type: " + compressionType);
        }
    }

    /**
     * 删除原始未压缩的数据
     */
    private void cleanEventData() {
        Arrays.fill(data.getData(), compressionBegin, compressionEnd, (byte) 0);
    }

    /**
     * 删除原始未压缩的tokens
     */
    public void cleanEventTokens() {
        // 清理token
        BatchEventToken batchEventToken = (BatchEventToken) data.getEventToken();
        List<SingleEventToken> tokens = batchEventToken.getTokens();

        if (compressionBeginTokenIdxs.size() != compressionEndTokenIdxs.size()) {
            log.error(
                "The number of begin events does not match the number of commit events! begin events number: {}, commit event number: {}.",
                compressionBeginTokenIdxs.size(), compressionEndTokenIdxs.size());
            throw new RuntimeException(
                "Failed in clean uncompressed tokens: begin commit number does not match.");
        }
        // 最后一个是TransactionBinlog token 保留。(sublist 是exclusive的)
        for (int i = compressionBeginTokenIdxs.size() - 1; i >= 0; i--) {
            tokens.subList(compressionBeginTokenIdxs.get(i), compressionEndTokenIdxs.get(i)).clear();
        }
        compressionBeginTokenIdxs.clear();
        compressionEndTokenIdxs.clear();
    }

    /**
     * 计算event size
     *
     * @return int, event 大小
     */
    public static int getEventSize(CompressionType compressionType, int compressionSize, int uncompressedSize) {
        return getAllHeaderLength(compressionType, compressionSize, uncompressedSize) + compressionSize + 4;
    }

    /**
     * 计算除了payload，checksum外的header的总长度
     *
     * @return int
     */
    public static int getAllHeaderLength(CompressionType compressionType, int compressionSize, int uncompressedSize) {
        return EventGenerator.TRANSACTION_BINLOG_HEADER_FIXED_LEN
            + ByteArray.netLengthSize(compressionType.getValue())
            + ByteArray.netLengthSize(compressionSize)
            + ByteArray.netLengthSize(uncompressedSize);
    }

    /**
     * 尽量避免使用该函数，使用LogDecoder进行解压
     */
    public static byte[] unCompress(byte[] data) {
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(19);
        // compression type
        // skip compression filed info, see BinlogTransactionCompressorTest.java
        byteArray.skip(2);
        int typeValue = byteArray.read();
        CompressionType type = CompressionType.fromValue(typeValue);
        // uncompressed size
        byteArray.skip(2);
        long uncompressedSize = byteArray.readLenenc();
        // compression size
        byteArray.skip(2);
        long compressionSize = byteArray.readLenenc();
        // skip end mask
        byteArray.skip(1);

        byte[] compressedData = new byte[(int) compressionSize];
        byte[] decompressedData = new byte[(int) uncompressedSize];
        byteArray.read(compressedData);

        if (type == CompressionType.ZSTD) {
            Zstd.decompress(decompressedData, compressedData);
        } else if (type == CompressionType.NONE) {
            decompressedData = compressedData;
        } else {
            throw new RuntimeException("Unknown Compression Type" + type);
        }
        return decompressedData;
    }

    private void updateCompressionStatistics() {
        CompressionStatistics.getCompressionOutBytes().addAndGet(compressionSize);
        CompressionStatistics.getCompressionEvents().addAndGet(1);
        long decompressedEvents = 0;
        for (int i = 0; i < compressionBeginTokenIdxs.size(); i++) {
            decompressedEvents += compressionEndTokenIdxs.get(i) - compressionBeginTokenIdxs.get(i) + 1;
        }
        CompressionStatistics.getDeCompressedEvents().addAndGet(decompressedEvents);
        // 23: header size + check sum size
        CompressionStatistics.getCompressionInBytes().addAndGet(uncompressedSize + decompressedEvents * 23);
        CompressionStatistics.getCompressionTimeCost().addAndGet(System.currentTimeMillis() - compressBeginTime);
    }
}
