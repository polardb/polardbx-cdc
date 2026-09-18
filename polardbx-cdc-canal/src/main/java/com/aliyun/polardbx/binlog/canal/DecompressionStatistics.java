/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zm
 */
public class DecompressionStatistics {
    @Getter
    private final static AtomicLong compressionInBytes = new AtomicLong(0);
    @Getter
    private final static AtomicLong compressionOutBytes = new AtomicLong(0);
    /**
     * 被压缩的事件数
     */
    @Getter
    private final static AtomicLong deCompressedEvents = new AtomicLong(0);
    @Getter
    private final static AtomicLong compressionEvents = new AtomicLong(0);
    @Getter
    private final static AtomicDouble compressionRatio = new AtomicDouble(0);
    @Getter
    private final static AtomicLong timeCost = new AtomicLong(0);
    @Setter
    @Getter
    private static Long lastCompressionPos = 0L;
    @Setter
    @Getter
    private static String lastCompressionFile = "";

    private static void updateCompressionRatio() {
        if (compressionInBytes.get() == 0) {
            return;
        }
        compressionRatio.set(compressionOutBytes.get() * 1.0 / compressionInBytes.get());
    }

    private static void reset() {
        compressionInBytes.set(0);
        compressionOutBytes.set(0);
        compressionEvents.set(0);
        deCompressedEvents.set(0);
        timeCost.set(0);
        lastCompressionPos = 0L;
        lastCompressionFile = "";
        updateCompressionRatio();
    }

    public static void updateDecompressionStatistics(long compressionEventSize, long decompressedEventCount,
                                                     long decompressedEventSize, long decompressTimeCost,
                                                     LogPosition position) {
        lastCompressionPos = position.getPosition();
        lastCompressionFile = position.getFileName();
        compressionInBytes.addAndGet(compressionEventSize);
        compressionOutBytes.addAndGet(decompressedEventSize);
        timeCost.addAndGet(decompressTimeCost);
        deCompressedEvents.addAndGet(decompressedEventCount);
        compressionEvents.addAndGet(1);
    }

    public static String getDecompressionInfo() {
        updateCompressionRatio();
        String res = "CompressionStatistics{" +
            "decompressionInBytes=" + compressionInBytes.get() +
            ", decompressionOutBytes=" + compressionOutBytes.get() +
            ", compressionRatio=" + compressionRatio.get() +
            ", compressionEvents=" + compressionEvents.get() +
            ", deCompressedEvents=" + deCompressedEvents.get() +
            ", decompressionTimeCost=" + timeCost.get() +
            ", lastCompressionPos=" + lastCompressionPos +
            ", lastCompressionFile='" + lastCompressionFile +
            '}';
        reset();
        return res;
    }
}
