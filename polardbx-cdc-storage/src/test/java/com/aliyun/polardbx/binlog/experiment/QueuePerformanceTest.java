/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.experiment;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * created by ziyang.lb
 **/
@Ignore
public class QueuePerformanceTest {

    @Test
    public void testPress() {
        ArrayBlockingQueue<String> arrayBlockingQueue = new ArrayBlockingQueue<>(65536);
        LinkedBlockingDeque<String> linkedBlockingDeque = new LinkedBlockingDeque<>(65536);
        ConcurrentLinkedDeque<String> concurrentLinkedDeque = new ConcurrentLinkedDeque<>();
        // PriorityBlockingQueue<String> priorityBlockingQueue = new
        // PriorityBlockingQueue<>(65536);

        press(arrayBlockingQueue);
        press(linkedBlockingDeque);
        press(concurrentLinkedDeque);
        // press(priorityBlockingQueue);
    }

    private void press(Queue<String> queue) {
        int count = 1000000;
        List<String> seedList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            seedList.add(UUID.randomUUID().toString());
        }

        Thread t1 = new Thread(() -> {
            try {
                seedList.forEach(s -> {
                    while (true) {
                        boolean result = queue.offer(s);
                        if (result) {
                            break;
                        }
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                int cnt = 0;
                while (cnt < count) {
                    String s = queue.poll();
                    if (s == null) {
                        continue;
                    }
                    cnt++;
                }
                long endTime = System.currentTimeMillis();
                System.out.println(queue.getClass().getName() + " 消耗时间 ： " + (endTime - startTime) + "毫秒");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        try {
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
