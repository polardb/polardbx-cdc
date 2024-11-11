/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.experiment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * created by ziyang.lb
 **/
public class StringVsLongWithPriorityQueueTest {

    @Test
    public void testPerformance() {

        PriorityQueue<Long> queue1 = new PriorityQueue<>();
        PriorityQueue<String> queue2 = new PriorityQueue<>();
        PriorityQueue<String> queue3 = new PriorityQueue<>();

        List<Long> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        List<String> list3 = new ArrayList<>();

        long seed = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            list1.add(seed);
            list2.add(String.valueOf(seed));
            list3.add(UUID.randomUUID().toString());
            seed++;
        }

        long start = System.currentTimeMillis();
        list1.forEach(i -> queue1.offer(i));
        System.out.println(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        list2.forEach(i -> queue2.offer(i));
        System.out.println(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        list3.forEach(i -> queue3.offer(i));
        System.out.println(System.currentTimeMillis() - start);
    }
}
