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
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_CLEAN_BUFFER_SIZE;

/**
 * Created by ziyang.lb
 **/
public class LogEventStorage implements Storage {

    private static final Logger logger = LoggerFactory.getLogger(LogEventStorage.class);
    private static final int BUFFER_SIZE = DynamicApplicationConfig.getInt(STORAGE_CLEAN_BUFFER_SIZE);
    private static final int DEFAULT_WORKER_COUNT = 4;
    private static final int FLUSH_INTERVAL = 2000;//毫秒

    private final ThreadPoolExecutor cleanBoss;
    private final CleanWorker[] cleanWorkers;
    private final ScheduledExecutorService cleanTimer;
    private final LoadingCache<String, SubCache> txnCache;
    private final TxnKey[] deleteBuffer;
    private final AtomicInteger deleteBufferPointer;
    private final AtomicLong lastDeleteTime;
    private final Repository repository;
    private final int cleanWorkerCount;
    private volatile boolean running;

    public LogEventStorage(Repository repository) {
        this(repository, DEFAULT_WORKER_COUNT);
    }

    public LogEventStorage(Repository repository, int cleanWorkerCount) {
        this.cleanBoss = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), r -> new Thread(r, "Storage-cleaner-boss-thread"),
            new ThreadPoolExecutor.CallerRunsPolicy());

        this.cleanWorkerCount = cleanWorkerCount;
        this.cleanWorkers = new CleanWorker[this.cleanWorkerCount];
        for (int i = 0; i < this.cleanWorkerCount; i++) {
            cleanWorkers[i] = new CleanWorker("Storage-cleaner-worker-thread-" + i);
        }

        this.cleanTimer = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "Storage-cleaner-timer-thread");
            t.setDaemon(true);
            return t;
        });

        // 按照PartitionId对缓存进行分段，最大化避免线程间的锁竞争和减少单个缓存的数据量，以提升性能
        this.txnCache = CacheBuilder.newBuilder().build(new CacheLoader<String, SubCache>() {
            @Override
            public SubCache load(String partitionId) {
                if (logger.isDebugEnabled()) {
                    logger.debug("create sub cache for partition {}.", partitionId);
                }
                return new SubCache(partitionId);
            }
        });
        this.deleteBuffer = new TxnKey[BUFFER_SIZE];
        this.deleteBufferPointer = new AtomicInteger(0);
        this.lastDeleteTime = new AtomicLong(0);
        this.repository = repository;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        repository.open();
        for (int i = 0; i < DEFAULT_WORKER_COUNT; i++) {
            cleanWorkers[i].start();
        }
        cleanTimer.scheduleAtFixedRate(() -> {
            try {
                checkDeleteBuffer();
            } catch (Throwable e) {
                logger.error("clean timer process error!", e);
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
        logger.info("log event storage started.");
    }

    @SneakyThrows
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        cleanTimer.shutdownNow();
        for (int i = 0; i < DEFAULT_WORKER_COUNT; i++) {
            cleanWorkers[i].interrupt();
        }
        cleanBoss.shutdownNow();
        txnCache.invalidateAll();
        repository.close();
        logger.info("log event storage stopped.");
    }

    @Override
    public TxnBuffer create(TxnKey key) throws AlreadyExistException {
        check();

        SubCache cache = txnCache.getUnchecked(key.getPartitionGroupId());
        TxnBuffer buffer = cache.getUnchecked(key);

        // mark失败，说明是在进行重复创建，so，抛出异常。这是一个乐观锁控制机制，在我们的场景中用乐观锁足够了，
        // 因为正常情况下不会出现并发访问的情况，没必要进行悲观锁控制，靠乐观锁进行"重复检查"和"极端情况下的并发控制"
        if (!buffer.markStart()) {
            throw new AlreadyExistException("Txn Buffer has already exist for key " + key);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("txn buffer is created for key " + key);
        }

        return buffer;
    }

    @Override
    public TxnBuffer fetch(TxnKey key) {
        check();
        SubCache cache = txnCache.getUnchecked(key.getPartitionGroupId());
        return cache.getIfPresent(key);
    }

    @Override
    public void delete(TxnKey key) {
        check();
        SubCache cache = txnCache.getUnchecked(key.getPartitionGroupId());
        cache.invalidate(key);

        if (logger.isDebugEnabled()) {
            logger.debug("txn buffer is deleted for key " + key);
        }
    }

    @Override
    public void deleteAsync(TxnKey key) {
        synchronized (this) {
            check();
            deleteBuffer[deleteBufferPointer.get()] = key;
            if (deleteBufferPointer.incrementAndGet() == deleteBuffer.length) {
                flushDeleteBuffer();
            }
        }
        StorageMetrics.get().getCleanerQueuedSize().set(getCleanerQueuedSize());
    }

    @Override
    public boolean exist(TxnKey key) {
        check();
        SubCache cache = txnCache.getUnchecked(key.getPartitionGroupId());
        return cache.getIfPresent(key) != null;
    }

    @Override
    public long getCleanerQueuedSize() {
        return cleanBoss.getQueue().size();
    }

    private void check() {
        if (!running) {
            throw new PolardbxException("Log event storage is not running!");
        }
    }

    private void checkDeleteBuffer() {
        if (System.currentTimeMillis() - lastDeleteTime.get() > FLUSH_INTERVAL && deleteBufferPointer.get() > 0) {
            synchronized (this) {
                if (System.currentTimeMillis() - lastDeleteTime.get() > FLUSH_INTERVAL
                    && deleteBufferPointer.get() > 0) {
                    flushDeleteBuffer();
                }
            }
        }
    }

    private void flushDeleteBuffer() {
        final TxnKey[] array = Arrays.copyOf(deleteBuffer, deleteBufferPointer.get());
        cleanBoss.execute(() -> {
            if (!running) {
                return;
            }
            try {
                List<TxnKey> list = Arrays.asList(array);
                list.stream().collect(Collectors.groupingBy(TxnKey::getPartitionGroupId)).entrySet().forEach(entry -> {
                    int index = Math.abs(entry.getKey().hashCode() % DEFAULT_WORKER_COUNT);
                    try {
                        cleanWorkers[index].put(entry);
                    } catch (InterruptedException e) {
                    }
                });
            } catch (Throwable t) {
                logger.error("Async delete failed", t);
            }
        });
        deleteBufferPointer.set(0);
        lastDeleteTime.set(System.currentTimeMillis());
    }

    class SubCache implements LoadingCache<TxnKey, TxnBuffer> {
        final ReadWriteLock readWriteLock;
        final String partitionId;
        final LoadingCache<TxnKey, TxnBuffer> loadingCache;

        SubCache(String partitionId) {
            this.readWriteLock = new ReentrantReadWriteLock();
            this.partitionId = partitionId;
            this.loadingCache =
                CacheBuilder.newBuilder().removalListener((RemovalListener<TxnKey, TxnBuffer>) notification -> {
                    notification.getValue().close();
                    if (logger.isDebugEnabled()) {
                        logger.debug("txn buffer is removed for key : " + notification.getKey());
                    }
                }).build(new CacheLoader<TxnKey, TxnBuffer>() {

                    @Override
                    public TxnBuffer load(TxnKey key) {
                        long startTime = System.nanoTime();
                        TxnBuffer buffer = new TxnBuffer(key, LogEventStorage.this.repository);
                        long endTime = System.nanoTime();
                        StorageMetrics.get().getTxnCreateCostTime().getAndAdd(endTime - startTime);
                        StorageMetrics.get().getTxnCreateCount().incrementAndGet();
                        return buffer;
                    }
                });
        }

        @Override
        public TxnBuffer get(TxnKey key) throws ExecutionException {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public TxnBuffer getUnchecked(TxnKey key) {
            try {
                readWriteLock.readLock().lock();
                return loadingCache.getUnchecked(key);
            } finally {
                readWriteLock.readLock().unlock();
            }
        }

        @Override
        public ImmutableMap<TxnKey, TxnBuffer> getAll(Iterable<? extends TxnKey> keys) throws ExecutionException {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public TxnBuffer apply(TxnKey key) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public void refresh(TxnKey key) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public ConcurrentMap<TxnKey, TxnBuffer> asMap() {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public TxnBuffer getIfPresent(Object key) {
            try {
                readWriteLock.readLock().lock();
                return loadingCache.getIfPresent(key);
            } finally {
                readWriteLock.readLock().unlock();
            }
        }

        @Override
        public TxnBuffer get(TxnKey key, Callable<? extends TxnBuffer> loader) throws ExecutionException {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public ImmutableMap<TxnKey, TxnBuffer> getAllPresent(Iterable<?> keys) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public void put(TxnKey key, TxnBuffer value) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public void putAll(Map<? extends TxnKey, ? extends TxnBuffer> m) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public void invalidate(Object key) {
            try {
                readWriteLock.writeLock().lock();
                loadingCache.invalidate(key);
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }

        @Override
        public void invalidateAll(Iterable<?> keys) {
            try {
                readWriteLock.writeLock().lock();
                loadingCache.invalidateAll(keys);
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }

        @Override
        public void invalidateAll() {
            try {
                readWriteLock.writeLock().lock();
                loadingCache.invalidateAll();
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }

        @Override
        public long size() {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public CacheStats stats() {
            throw new UnsupportedOperationException("unsupported operation");
        }

        @Override
        public void cleanUp() {
            throw new UnsupportedOperationException("unsupported operation");
        }
    }

    class CleanWorker extends Thread {
        private final LinkedBlockingQueue<Map.Entry<String, List<TxnKey>>> queue;

        public CleanWorker(String name) {
            super(name);
            this.queue = new LinkedBlockingQueue<>(Integer.MAX_VALUE);
        }

        public void put(Map.Entry<String, List<TxnKey>> item) throws InterruptedException {
            queue.put(item);
        }

        @Override
        public void run() {
            Map.Entry<String, List<TxnKey>> item;
            while (running) {
                try {
                    item = queue.take();
                    SubCache subCache = LogEventStorage.this.txnCache.get(item.getKey());
                    subCache.invalidateAll(item.getValue());
                } catch (Throwable t) {
                    logger.error("something goes wrong when do cleaning in storage.", t);
                }
            }
        }
    }
}
