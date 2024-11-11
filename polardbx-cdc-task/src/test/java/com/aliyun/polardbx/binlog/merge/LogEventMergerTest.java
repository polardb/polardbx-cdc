/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class LogEventMergerTest extends BaseTest {

    // 单纯测merge算法的性能
    @Test
    @Ignore
    public void testPureMergePerformance() {
        int sourceCount = 4;
        int tokenCount = 1000000;

        LogEventMerger merger = generateMerger();
        List<MergeSource> sources = generateMergeSource(merger, sourceCount, tokenCount);

        merger.start();
        System.out.println("merger started.");
        waiting(merger, sources, tokenCount);
    }

    private void waiting(LogEventMerger merger, List<MergeSource> sources, int count) {
        while (true) {
            boolean flag = sources.stream().anyMatch(s -> s.getPassCount() == count);
            if (flag) {
                double durTimeSecond = (merger.getLatestPassTime() - merger.getStartTime()) / 1000f;
                long passCount = merger.getLatestPassCount();
                double tps = passCount / durTimeSecond;
                System.out.println("final tps is " + tps);
                System.out.println("final pass count is " + passCount);
                System.out.println("final duration time is " + durTimeSecond + "s");
                break;
            }

            try {
                System.out.println("latest pass count is " + merger.getLatestPassCount());
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private LogEventMerger generateMerger() {
        LogEventMerger merger = new LogEventMerger(TaskType.Relay, new Collector() {

            @Override
            public void start() {

            }

            @Override
            public void stop() {

            }

            @Override
            public void push(TxnToken token) {
                // do nothing
            }

            @Override
            public long getQueuedSize() {
                return 0;
            }

            @Override
            public void setCurrentHeartBeatWindow(HeartBeatWindow window) {

            }
        }, false, "", false, 1, null, null);
        return merger;
    }

    private List<MergeSource> generateMergeSource(LogEventMerger merger, int sourceCount, int tokenCount) {
        List<MergeSource> sources = new ArrayList<>();
        for (int i = 0; i < sourceCount; i++) {
            String sourceId = "S" + i;
            MergeSource source = new MergeSource(sourceId, generateQueue(tokenCount, sourceId), null);
            merger.addMergeSource(source);
            sources.add(source);
        }
        return sources;
    }

    private ArrayBlockingQueue<MergeItem> generateQueue(int tokenCount, String suffix) {
        long
            seed = System.currentTimeMillis();
        ArrayBlockingQueue<MergeItem> queue = new
            ArrayBlockingQueue<>(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            TxnToken t = TxnToken.newBuilder()
                .setTso(addZeroForNum(String.valueOf(seed), 32) + "-" + suffix)
                .build();
            queue.add(new MergeItem(suffix, t));
            seed++;
        }
        return queue;
    }

    private String addZeroForNum(String str, int strLength) {
        int strLen = str.length();
        StringBuffer sb = null;
        while (strLen < strLength) {
            sb = new StringBuffer();
            sb.append("0").append(str);// 左补0
            str = sb.toString();
            strLen = str.length();
        }
        return str;
    }
}
