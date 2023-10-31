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

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * created by ziyang.lb
 **/
@Ignore
public class RocksDBPerformanceTest {

    private static final String PATH = "/tmp/RocksDB";

    @Before
    public void before() {
        FileUtils.deleteQuietly(new File(PATH));
    }

    // 1.通过测试，是否禁用WAL对性能提升没有显著差别
    @Test
    public void testPerformance() throws Exception {
        RocksDB.loadLibrary();
        int count = 1000000;
        WriteOptions writeOptions = new WriteOptions();
        writeOptions.disableWAL();

        /**
         * NotDisableWAL</br>
         * build time :785 </br>
         * put time :7863 </br>
         * get time :9697 </br>
         * delete time :7686 </br>
         */
        testSingThread(OrderType.DisOrdered, count, writeOptions);
        /**
         * NotDisableWAL</br>
         * build time :61 </br>
         * put time :6514 </br>
         * get time :1792 </br>
         * delete time :6256 </br>
         */
        testSingThread(OrderType.Ordered, count, writeOptions);
        /**
         * NotDisableWAL</br>
         * build time :659 </br>
         * put time :3577 </br>
         * get time :1550 </br>
         * delete time :3948 </br>
         */
        testMultiThread(OrderType.DisOrdered, 100000, 10, writeOptions);
        /**
         * NotDisableWAL</br>
         * build time :56 </br>
         * put time :3015 </br>
         * get time :476 </br>
         * delete time :2977 </br>
         */
        testMultiThread(OrderType.Ordered, 100000, 10, writeOptions);
    }

    private void testMultiThread(OrderType orderType, int countPerThread, int threadSize,
                                 WriteOptions writeOptions) throws Exception {
        try (final Options options = new Options().setCreateIfMissing(true)) {
            try (final RocksDB db = RocksDB.open(options, PATH)) {
                long seed = System.currentTimeMillis();
                List<List<String>> list = new ArrayList<>();

                final ExecutorService executor = Executors.newCachedThreadPool();

                // build time
                long start = System.currentTimeMillis();
                for (int i = 0; i < threadSize; i++) {
                    List<String> temp = new ArrayList<>();
                    for (int j = 0; j < countPerThread; j++) {
                        if (OrderType.Ordered == orderType) {
                            temp.add(String.valueOf(seed++));
                        } else {
                            temp.add(UUID.randomUUID().toString());
                        }
                    }
                    list.add(temp);
                }
                long end = System.currentTimeMillis();
                System.out.println("build time :" + (end - start));

                // put
                start = System.currentTimeMillis();
                final CountDownLatch latch1 = new CountDownLatch(threadSize);
                list.forEach(l -> {
                    executor.submit(() -> {
                        try {
                            doPut(db, l, writeOptions);
                        } catch (RocksDBException e) {
                            e.printStackTrace();
                        }
                        latch1.countDown();
                    });
                });
                latch1.await();
                end = System.currentTimeMillis();
                System.out.println("put time :" + (end - start));

                // get
                start = System.currentTimeMillis();
                final CountDownLatch latch2 = new CountDownLatch(threadSize);
                list.forEach(l -> {
                    executor.submit(() -> {
                        try {
                            doGet(db, l);
                        } catch (RocksDBException e) {
                            e.printStackTrace();
                        }
                        latch2.countDown();
                    });
                });
                latch2.await();
                end = System.currentTimeMillis();
                System.out.println("get time :" + (end - start));

                // delete
                start = System.currentTimeMillis();
                final CountDownLatch latch3 = new CountDownLatch(threadSize);
                list.forEach(l -> {
                    executor.submit(() -> {
                        try {
                            doDelete(db, l, writeOptions);
                        } catch (RocksDBException e) {
                            e.printStackTrace();
                        }
                        latch3.countDown();
                    });
                });
                latch3.await();
                end = System.currentTimeMillis();
                System.out.println("delete time :" + (end - start));
            }
        }
    }

    private void testSingThread(OrderType orderType, int count, WriteOptions writeOptions) throws RocksDBException {
        try (final Options options = new Options().setCreateIfMissing(true)) {
            try (final RocksDB db = RocksDB.open(options, PATH)) {
                long seed = System.currentTimeMillis();
                List<String> list = new ArrayList<>();

                // build time
                long start = System.currentTimeMillis();
                for (int i = 0; i < count; i++) {
                    if (OrderType.Ordered == orderType) {
                        list.add(String.valueOf(seed++));
                    } else {
                        list.add(UUID.randomUUID().toString());
                    }
                }
                long end = System.currentTimeMillis();
                System.out.println("build time :" + (end - start));

                // put
                start = System.currentTimeMillis();
                doPut(db, list, writeOptions);
                end = System.currentTimeMillis();
                System.out.println("put time :" + (end - start));

                // get
                start = System.currentTimeMillis();
                doGet(db, list);
                end = System.currentTimeMillis();
                System.out.println("get time :" + (end - start));

                // delete
                start = System.currentTimeMillis();
                doDelete(db, list, writeOptions);
                end = System.currentTimeMillis();
                System.out.println("delete time :" + (end - start));
            }
        }
    }

    private void doPut(RocksDB db, List<String> list, WriteOptions writeOptions) throws RocksDBException {
        int count = list.size();
        for (int i = 0; i < count; i++) {
            db.put(writeOptions,
                list.get(i).getBytes(),
                "11111111111111111111111111111111111111111111111111".getBytes());
        }
    }

    private void doGet(RocksDB db, List<String> list) throws RocksDBException {
        int count = list.size();
        for (int i = 0; i < count; i++) {
            db.get(list.get(i).getBytes());
        }
    }

    private void doDelete(RocksDB db, List<String> list, WriteOptions writeOptions) throws RocksDBException {
        int count = list.size();
        for (int i = 0; i < count; i++) {
            db.delete(writeOptions, list.get(i).getBytes());
        }
    }

    enum OrderType {
        Ordered, DisOrdered
    }
}
