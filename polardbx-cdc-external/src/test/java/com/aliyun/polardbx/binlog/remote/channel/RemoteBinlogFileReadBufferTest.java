/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.channel;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * @author yudong
 * @since 2022/9/30
 **/
@Slf4j
public class RemoteBinlogFileReadBufferTest extends BaseTest {

    @Test
    public void testReadWithArbitrarySizeBuffer() throws IOException {
        for (int i = 0; i < 100; i++) {
            CRC32 dstCrc = new CRC32();
            CRC32 srcCrc = new CRC32();
            ByteBuffer dst = ByteBuffer.allocate(1 + new Random().nextInt(1024));
            log.info("dst: {}", dst);
            byte[] data = new byte[1024 * 1024];
            new Random().nextBytes(data);
            InputStream in = new ByteArrayInputStream(data);
            srcCrc.update(data);
            RemoteBinlogFileReadBuffer readBuffer = new RemoteBinlogFileReadBuffer(in);

            int readSize;
            int totalReadSize = 0;
            while (totalReadSize < data.length) {
                dst.clear();
                readSize = readBuffer.read(dst);
                dst.flip();
                dstCrc.update(dst.array(), 0, dst.limit());
                totalReadSize += readSize;
            }

            Assert.assertEquals(totalReadSize, data.length);
            Assert.assertEquals(srcCrc.getValue(), dstCrc.getValue());

            // test EOF
            dst.clear();
            readSize = readBuffer.read(dst);
            Assert.assertEquals(readSize, -1);
            readSize = readBuffer.read(dst);
            Assert.assertEquals(readSize, -1);
        }
    }
}
