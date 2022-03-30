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

package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 *
 **/
public class LogEventMergerTest {

    @Test
    public void correctnessTest() {
        System.out.println(System.currentTimeMillis());
    }

    // 单纯测merge算法的性能
    @Test
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
        /*
         * for (int i = 0; i < sourceCount; i++) { String sourceId = "S" + i;
         * MergeSource source = new MergeSource(sourceId, generateQueue(tokenCount,
         * sourceId)); merger.addMergeSource(source); sources.add(source); }
         */
        return sources;
    }

    /*
     * private Queue<TxnToken> generateQueue(int tokenCount, String suffix) { long
     * seed = System.currentTimeMillis(); ArrayBlockingQueue<TxnToken> queue = new
     * ArrayBlockingQueue<>(tokenCount); for (int i = 0; i < tokenCount; i++) {
     * TxnToken t = new TxnToken(); t.setTso(addZeroForNum(String.valueOf(seed), 32)
     * + "-" + suffix); // t.setTso(seed + "-" + suffix); queue.add(t); seed++; }
     * return queue; }
     */

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
