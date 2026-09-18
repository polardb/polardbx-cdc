/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.google.common.util.concurrent.AtomicDouble;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zm
 */
public class CompressionStatistics {
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
    private final static AtomicLong compressionTimeCost = new AtomicLong(0);
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
        compressionTimeCost.set(0);
        lastCompressionPos = 0L;
        lastCompressionFile = "";
        updateCompressionRatio();
    }

    public static String getCompressionInfo() {
        updateCompressionRatio();
        String res = "CompressionStatistics{" +
            "compressionInBytes=" + compressionInBytes.get() +
            ", compressionOutBytes=" + compressionOutBytes.get() +
            ", compressionRatio=" + compressionRatio.get() +
            ", compressionEvents=" + compressionEvents.get() +
            ", deCompressedEvents=" + deCompressedEvents.get() +
            ", compressionTimeCost=" + compressionTimeCost.get() +
            ", lastCompressionPos=" + lastCompressionPos +
            ", lastCompressionFile='" + lastCompressionFile +
            '}';
        reset();
        return res;
    }
}
