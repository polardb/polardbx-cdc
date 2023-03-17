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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RepoUnit {
    RocksDB rocksDB;
    String persistPath;
    Options options;
    WriteOptions writeOptions;
    ReadOptions readOptions;
    boolean disableWal;
    boolean checkNull;
    boolean cleanFileWhenClose;

    public RepoUnit(String persistPath, boolean disableWal, boolean checkNull, boolean cleanFileWhenClose) {
        this.persistPath = persistPath;
        this.disableWal = disableWal;
        this.checkNull = checkNull;
        this.cleanFileWhenClose = cleanFileWhenClose;
    }

    public void put(byte[] key, byte[] value) throws RocksDBException {
        checkKeyNotNull(key);
        checkValueNotNull(value);
        rocksDB.put(writeOptions, key, value);
    }

    public List<byte[]> multiGet(List<byte[]> keys) throws RocksDBException {
        return rocksDB.multiGetAsList(keys);
    }

    public byte[] get(byte[] key) throws RocksDBException {
        byte[] value = rocksDB.get(readOptions, key);
        checkValueNotNull(value);
        return value;
    }

    public void delete(byte[] key) throws RocksDBException {
        checkKeyNotNull(key);
        rocksDB.delete(writeOptions, key);
    }

    public void deleteRange(byte[] beginKey, byte[] endKey) throws RocksDBException {
        checkKeyNotNull(beginKey);
        checkKeyNotNull(endKey);
        rocksDB.deleteRange(writeOptions, beginKey, endKey);
    }

    public boolean exists(byte[] key) throws RocksDBException {
        byte[] value = rocksDB.get(readOptions, key);
        return value != null && value.length > 0;
    }

    public LinkedList<Pair<byte[], byte[]>> getRange(byte[] key, int count, long maxByteSize) {
        LinkedList<Pair<byte[], byte[]>> result = new LinkedList<>();
        try (RocksIterator iterator = rocksDB.newIterator(readOptions)) {
            int i = 0;
            long byteSize = 0;
            for (iterator.seek(key); iterator.isValid() && i < count; iterator.next()) {
                Pair<byte[], byte[]> pair = Pair.of(iterator.key(), iterator.value());
                result.add(pair);
                byteSize += pair.getValue().length;
                if (byteSize >= maxByteSize && i > 1) {
                    break;
                }
                i++;
            }
        }

        return result;
    }

    public void compactRange(byte[] begin, byte[] end) throws RocksDBException {
        rocksDB.compactRange(begin, end);
    }

    public List<Pair<byte[], byte[]>> getRange(byte[] beginKey, byte[] endKey, int count) {
        ReadOptions readOptions = new ReadOptions(this.readOptions);
        readOptions.setIterateLowerBound(new Slice(beginKey));
        readOptions.setIterateUpperBound(new Slice(endKey));

        List<Pair<byte[], byte[]>> result = new ArrayList<>();
        try (RocksIterator iterator = rocksDB.newIterator(readOptions)) {
            int i = 0;
            for (iterator.seek(beginKey); iterator.isValid() && i < count; iterator.next()) {
                Pair<byte[], byte[]> pair = Pair.of(iterator.key(), iterator.value());
                result.add(pair);
                i++;
            }
        }

        return result;
    }

    public RocksIterator getIterator(byte[] beginKey, byte[] endKey) {
        ReadOptions readOptions = new ReadOptions(this.readOptions);
        readOptions.setIterateLowerBound(new Slice(beginKey));
        readOptions.setIterateUpperBound(new Slice(endKey));
        return rocksDB.newIterator(readOptions);
    }

    public byte[] getMaxKey() {
        try (RocksIterator iterator = rocksDB.newIterator(readOptions)) {
            iterator.seekToLast();
            if (iterator.isValid()) {
                return iterator.key();
            } else {
                return null;
            }
        }
    }

    public Pair<byte[], byte[]> seekPre(byte[] target, Pair<byte[], byte[]> boundPair) {
        ReadOptions ro = new ReadOptions(readOptions);
        if (boundPair != null) {
            ro.setIterateLowerBound(new Slice(boundPair.getKey()));
            ro.setIterateUpperBound(new Slice(boundPair.getValue()));
        }
        try (RocksIterator iterator = rocksDB.newIterator(ro)) {
            iterator.seekForPrev(target);
            if (iterator.isValid()) {
                if (Arrays.equals(target, iterator.key())) {
                    iterator.prev();
                    if (iterator.isValid()) {
                        return Pair.of(iterator.key(), iterator.value());
                    }
                } else {
                    return Pair.of(iterator.key(), iterator.value());
                }
            }
            return null;
        }
    }

    void checkKeyNotNull(byte[] key) {
        if (!checkNull) {
            return;
        }
        if (key == null || key.length == 0) {
            throw new PolardbxException("key is null or empty.");
        }
    }

    void checkValueNotNull(byte[] value) {
        if (!checkNull) {
            return;
        }
        if (value == null || value.length == 0) {
            throw new PolardbxException("value is null or empty.");
        }
    }

    public void open() throws Throwable {
        try {
            options = RocksDBOptionUtil.buildOptions();
            writeOptions = new WriteOptions();
            writeOptions.setDisableWAL(disableWal);
            readOptions = new ReadOptions();
            readOptions.setIgnoreRangeDeletions(true);

            FileUtils.forceMkdir(new File(persistPath));
            rocksDB = RocksDB.open(options, persistPath);

            log.info("Repo Unit is opened with path : " + persistPath);
        } catch (Throwable t) {
            log.error("Repo Unit is open failed with path : " + persistPath, t);
            throw t;
        }
    }

    public void close() throws Throwable {
        try {
            if (options != null) {
                options.close();
            }
            if (writeOptions != null) {
                writeOptions.close();
            }
            if (readOptions != null) {
                readOptions.close();
            }
            if (rocksDB != null) {
                rocksDB.close();
            }

            if (cleanFileWhenClose) {
                File path = new File(persistPath);
                if (path.exists()) {
                    FileUtils.deleteDirectory(path);
                }
            }
            log.info("Repo Unit is closed with path : " + persistPath);
        } catch (Throwable t) {
            log.error("Repo Unit is closing failed with path : " + persistPath, t);
            throw t;
        }
    }
}
