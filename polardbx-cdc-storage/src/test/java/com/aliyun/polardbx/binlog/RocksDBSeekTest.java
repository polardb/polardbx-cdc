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

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.ByteUtil;

/**
 *
 **/
public class RocksDBSeekTest {

    public static void main(String[] args) {
        RocksDB.loadLibrary();
        WriteOptions writeOptions = new WriteOptions();
        writeOptions.disableWAL();

        try (final Options options = new Options().setCreateIfMissing(true)) {
            try (final RocksDB db = RocksDB.open(options, "/Users/lubiao/Documents/Rocksdb")) {
                db.put(ByteUtil.bytes("a3"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a5"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a7"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a8"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a9"), ByteUtil.bytes("xx"));

                RocksIterator iterator = db.newIterator();
                iterator.seek(ByteUtil.bytes("a4"));
                if (iterator.isValid()) {
                    System.out.println(new String(iterator.key()));
                }
            }
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }
}
