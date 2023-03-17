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
package com.aliyun.polardbx.binlog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * created by ziyang.lb
 **/
public class StringVsLongTest {

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
