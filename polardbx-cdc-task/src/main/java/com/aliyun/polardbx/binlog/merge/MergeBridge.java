/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * created by ziayng.lb
 **/
@Slf4j
public class MergeBridge {
    private final ArrayBlockingQueue<MergeItemChunk> queue = new ArrayBlockingQueue<>(16);
    private final PriorityQueue<SortItem> priorityQueue = new PriorityQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Consumer<TxnToken> consumer;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r ->
        new Thread(r, "binlog-merge-bridge-thread"));
    private volatile Throwable error;

    public MergeBridge(Consumer<TxnToken> consumer) {
        this.consumer = consumer;
    }

    public void push(MergeItemChunk chunk) {
        if (error != null) {
            throw new PolardbxException("something goes wrong in merge bridge", error);
        }
        try {
            queue.put(chunk);
        } catch (InterruptedException e) {
            throw new PolardbxException(e);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executorService.execute(() -> {
                try {
                    while (running.get()) {
                        MergeItemChunk chunk = queue.poll(1, TimeUnit.SECONDS);
                        if (chunk != null) {
                            sort2(chunk);
                        }
                    }
                } catch (Throwable e) {
                    log.error("meet fatal error in binlog merge bridge!", e);
                    error = e;
                }
            });
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            executorService.shutdownNow();
        }
    }

    //sort method 1
    private void sort1(MergeItemChunk chunk) {
        for (MergeItem mergeItem : chunk.items()) {
            priorityQueue.addAll(mergeItem.getAllTxnTokens().stream().map(t -> new SortItem(t, mergeItem.getSourceId()))
                .collect(Collectors.toList()));
        }

        while (priorityQueue.peek() != null) {
            consumer.accept(priorityQueue.poll().txnToken);
        }
        priorityQueue.clear();
    }

    //sort method 2
    private void sort2(MergeItemChunk chunk) {
        SortItem lastSortItem = null;
        SortController controller = new SortController();

        Map<String, Iterator<SortItem>> sortMap = new HashMap<>();
        chunk.items().forEach(i -> {
            if (sortMap.containsKey(i.getSourceId())) {
                throw new PolardbxException("duplicate source id for sortMap " + i.getSourceId());
            }
            sortMap.put(i.getSourceId(),
                i.getAllTxnTokens().stream().map(t -> new SortItem(t, i.getSourceId()))
                    .collect(Collectors.toList()).iterator());
        });

        while (true) {
            if (lastSortItem != null && sortMap.get(lastSortItem.sourceId).hasNext()) {
                SortItem item = sortMap.get(lastSortItem.sourceId).next();
                controller.push(item);
            } else {
                for (Map.Entry<String, Iterator<SortItem>> entry : sortMap.entrySet()) {
                    if (controller.contains(entry.getKey())) {
                        continue;
                    }
                    if (!entry.getValue().hasNext()) {
                        continue;
                    }

                    SortItem item = entry.getValue().next();
                    controller.push(item);
                }
            }

            lastSortItem = controller.pop();
            if (lastSortItem != null) {
                consumer.accept(lastSortItem.txnToken);
            } else {
                break;
            }
        }
    }

    @AllArgsConstructor
    static class SortItem implements Comparable<SortItem> {
        TxnToken txnToken;
        String sourceId;

        @Override
        public int compareTo(SortItem o) {
            return txnToken.getTso().compareTo(o.txnToken.getTso());
        }
    }

    static class SortController {
        private final Set<String> sortSet = new HashSet<>();
        private final PriorityQueue<SortItem> sortQueue = new PriorityQueue<>();

        public boolean contains(String sourceId) {
            return sortSet.contains(sourceId);
        }

        public void push(SortItem item) {
            if (sortSet.contains(item.sourceId)) {
                throw new PolardbxException("should not push duplicated item for source " + item.sourceId);
            }

            sortQueue.offer(item);
            sortSet.add(item.sourceId);
        }

        public SortItem pop() {
            SortItem item = sortQueue.poll();
            if (item == null) {
                return null;
            }

            sortSet.remove(item.sourceId);
            return item;
        }
    }
}
