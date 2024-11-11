/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DiskCache implements Cache {
    private static final int DEFAULT_SIZE = 8192;
    private volatile int writeOffset;
    private volatile int readOffset;
    private byte[] buff;
    private CacheManager cacheManager;
    private FileChannel fileChannel;
    private MappedByteBuffer mappedBufferWriter;
    private ByteBuffer mappedBufferReader;
    private InputStream in;
    private String fileName;
    private volatile boolean open = true;
    private volatile boolean finish = false;

    public DiskCache(CacheManager cacheManager, InputStream in, String fileName, long size) throws IOException {
        this.cacheManager = cacheManager;
        this.in = in;
        this.buff = new byte[DEFAULT_SIZE];
        this.fileName = fileName;
        File file = new File(fileName);
        File dir = new File(fileName).getParentFile();
        FileUtils.forceMkdir(dir);
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        mappedBufferWriter = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        mappedBufferReader = mappedBufferWriter.asReadOnlyBuffer();
        mappedBufferReader.flip();
    }

    @Override
    public void fetchData() throws IOException {
        int len = -1;

        while (open && (len = in.read(buff)) > -1) {
            if (len > 0) {
                mappedBufferWriter.put(buff, 0, len);
                mappedBufferReader.limit(mappedBufferWriter.position());
                writeOffset += len;
            }
        }
        this.finish = true;
    }

    @Override
    public void resetStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int skip(int n) {
        while (mappedBufferReader.remaining() < n) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
            }
        }
        mappedBufferReader.position(mappedBufferReader.position() + n);
        return n;
    }

    @Override
    public int read(byte[] data, int offset, int size) throws IOException {
        if (!mappedBufferReader.hasRemaining() && finish) {
            return -1;
        }
        size = Math.min(mappedBufferReader.remaining(), size);
        mappedBufferReader.get(data, offset, size);
        return size;
    }

    @Override
    public void close() throws IOException {
        this.open = false;
        if (in != null) {
            in.close();
        }

        if (mappedBufferWriter != null) {
            mappedBufferWriter.clear();
        }
        if (fileChannel != null) {
            fileChannel.close();
        }
        FileUtils.deleteQuietly(new File(fileName));
    }

    @Override
    public String toString() {
        return "DiskCache{" +
            "writeOffset=" + writeOffset +
            ", readOffset=" + readOffset +
            ", fileName='" + fileName + '\'' +
            ", open=" + open +
            ", finish=" + finish +
            '}';
    }
}
