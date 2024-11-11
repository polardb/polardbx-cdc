/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import org.junit.Assert;
import org.junit.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.ByteUtil;

public class RocksDBSeekTest {

    @Test
    public void testSeek() throws RocksDBException {
        RocksDB.loadLibrary();
        WriteOptions writeOptions = new WriteOptions();
        writeOptions.disableWAL();

        try (final Options options = new Options().setCreateIfMissing(true)) {
            try (final RocksDB db = RocksDB.open(options, "/tmp/Rocksdb")) {
                db.put(ByteUtil.bytes("a3"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a5"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a7"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a8"), ByteUtil.bytes("xx"));
                db.put(ByteUtil.bytes("a9"), ByteUtil.bytes("xx"));

                RocksIterator iterator = db.newIterator();
                iterator.seek(ByteUtil.bytes("a4"));
                if (iterator.isValid()) {
                    Assert.assertEquals("a5", new String(iterator.key()));
                }

                iterator.seek(ByteUtil.bytes("a5"));
                if (iterator.isValid()) {
                    Assert.assertEquals("a5", new String(iterator.key()));
                }
            }
        }
    }
}
