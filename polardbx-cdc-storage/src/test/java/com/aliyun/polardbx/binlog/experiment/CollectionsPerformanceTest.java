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
package com.aliyun.polardbx.binlog.experiment;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * created by ziyang.lb
 **/
@Ignore
public class CollectionsPerformanceTest {

    @Test
    public void testPerformance() {
        HashMap hashMap = new HashMap();

        TreeMap treeMap = new TreeMap();

        SortedMap sortedMap = Collections.synchronizedSortedMap(new TreeMap<>());

        ConcurrentSkipListMap skipListMap = new ConcurrentSkipListMap();

        PriorityQueue<String> priorityQueue = new PriorityQueue<>();

        PriorityBlockingQueue<String> priorityBlockingQueue = new PriorityBlockingQueue<>();

        int threadCount = 10;
        int pressDataCount = 100000;
        testMap(hashMap, 1, pressDataCount);
        testMap(treeMap, 1, pressDataCount);
        testMap(sortedMap, threadCount, pressDataCount);
        testMap(skipListMap, threadCount, pressDataCount);
        testList(threadCount, pressDataCount);
        testQueue(priorityQueue, 1, pressDataCount);
        testQueue(priorityBlockingQueue, 1, pressDataCount);
    }

    private void testMap(Map<String, String> map, int threadCount, int pressDataCount) {
        MapPressThread[] threads = new MapPressThread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new MapPressThread(map, pressDataCount);
        }

        long start = System.currentTimeMillis();
        Arrays.stream(threads).forEach(t -> t.start());
        Arrays.stream(threads).forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        long end = System.currentTimeMillis();

        Long totalTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).sum();
        Double avgTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).average().getAsDouble();
        System.out.println(map.getClass().getName());
        System.out.println("total time: " + totalTime);
        System.out.println("avg time:" + avgTime);
        System.out.println("wait time:" + (end - start));
        System.out.println("===================================");
    }

    private void testList(int threadCount, int pressDataCount) {
        ListPressThread[] threads = new ListPressThread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new ListPressThread(new ArrayList<>(), pressDataCount / threadCount);
        }

        long start = System.currentTimeMillis();
        Arrays.stream(threads).forEach(t -> t.start());
        Arrays.stream(threads).forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        List<List<String>> lists = new ArrayList<>();
        Arrays.stream(threads).forEach(t -> lists.add(t.getTestList()));
        mergeSort(lists);
        long end = System.currentTimeMillis();

        Long totalTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).sum();
        Double avgTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).average().getAsDouble();

        System.out.println(threads[0].getClass().getName());
        System.out.println("total time: " + totalTime);
        System.out.println("avg time:" + avgTime);
        System.out.println("wait time:" + (end - start));
        System.out.println("===================================");
    }

    private void testQueue(Queue<String> queue, int threadCount, int pressDataCount) {
        QueuePressThread[] threads = new QueuePressThread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new QueuePressThread(queue, pressDataCount);
        }

        long start = System.currentTimeMillis();
        Arrays.stream(threads).forEach(t -> t.start());
        Arrays.stream(threads).forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        long end = System.currentTimeMillis();

        Long totalTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).sum();
        Double avgTime = Arrays.stream(threads).mapToLong(t -> t.getConsumeTime()).average().getAsDouble();
        System.out.println(queue.getClass().getName());
        System.out.println("total time: " + totalTime);
        System.out.println("avg time:" + avgTime);
        System.out.println("wait time:" + (end - start));
        System.out.println("===================================");
    }

    private List<String> mergeSort(List<List<String>> lists) {
        int length = lists.size();
        List<String> result = new ArrayList<>(0);
        for (int i = 0; i < length; i++) {
            if (i == 0) {
                result = lists.get(0);
            } else {
                List<String> temp = lists.get(i);
                result = mergeTwoSortList(result, temp);
            }
        }
        return result;
    }

    public static List<String> mergeTwoSortList(List<String> aList, List<String> bList) {
        int aLength = aList.size(), bLength = bList.size();
        List<String> mergeList = new ArrayList();
        int i = 0, j = 0;
        while (aLength > i && bLength > j) {
            if (aList.get(i).compareTo(bList.get(j)) > 0) {
                mergeList.add(i + j, bList.get(j));
                j++;
            } else {
                mergeList.add(i + j, aList.get(i));
                i++;
            }
        }
        // blist元素已排好序， alist还有剩余元素
        while (aLength > i) {
            mergeList.add(i + j, aList.get(i));
            i++;
        }
        // alist元素已排好序， blist还有剩余元素
        while (bLength > j) {
            mergeList.add(i + j, bList.get(j));
            j++;
        }
        return mergeList;

    }

    class MapPressThread extends Thread {

        private List<String> list = new ArrayList<>();
        private Map<String, String> testMap;
        private int pressDataCount;
        private long consumeTime;

        MapPressThread(Map<String, String> testMap, int pressDataCount) {
            this.pressDataCount = pressDataCount;
            for (int i = 0; i < pressDataCount; i++) {
                list.add(UUID.randomUUID().toString());
            }
            this.testMap = testMap;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            list.stream().forEach(i -> testMap.put(i, i));
            long endTime = System.currentTimeMillis();
            consumeTime = endTime - startTime;
        }

        public long getConsumeTime() {
            return consumeTime;
        }
    }

    class ListPressThread extends Thread {

        private List<String> list = new ArrayList<>();
        private List<String> testList;
        private int pressDataCount;
        private long consumeTime;

        ListPressThread(List<String> testList, int pressDataCount) {
            this.pressDataCount = pressDataCount;
            this.testList = testList;

            Random r = new Random(1);
            int seed = r.nextInt(1000000);
            for (int i = 0; i < pressDataCount; i++) {
                testList.add(String.valueOf(seed));
                seed++;
            }
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            list.stream().forEach(i -> testList.add(i));
            long endTime = System.currentTimeMillis();
            consumeTime = endTime - startTime;
        }

        public long getConsumeTime() {
            return consumeTime;
        }

        public List<String> getTestList() {
            return testList;
        }
    }

    class QueuePressThread extends Thread {

        private List<String> list = new ArrayList<>();
        private Queue<String> testQueue;
        private int pressDataCount;
        private long consumeTime;

        QueuePressThread(Queue testQueue, int pressDataCount) {
            this.pressDataCount = pressDataCount;
            for (int i = 0; i < pressDataCount; i++) {
                list.add(UUID.randomUUID().toString());
            }
            this.testQueue = testQueue;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            list.stream().forEach(i -> testQueue.offer(i));
            long endTime = System.currentTimeMillis();
            consumeTime = endTime - startTime;
        }

        public long getConsumeTime() {
            return consumeTime;
        }
    }
}
