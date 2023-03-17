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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * created by ziyang.lb
 **/
public class WritePerformanceTest {
    String fileName = "/Users/lubiao/Downloads/ptest.log";
    int bufferSize = 10485760;
    int length = 1048576;
    int fileSize = 1048576000;
    int count = fileSize / length;
    byte[] bytes = new byte[length];

    // 7728、7048
    @Test
    public void testWithByteBuffer1() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);

        long start = System.currentTimeMillis();
        for (int j = 0; j < 10; j++) {
            RandomAccessFile raf = new RandomAccessFile(fileName + j, "rw");
            for (int i = 0; i < 1000; i++) {
                byteBuffer.put(bytes);
                raf.write(byteBuffer.array());
                byteBuffer.clear();
            }
        }

        System.out.println(System.currentTimeMillis() - start);
    }

    //7553、7258
    @Test
    public void testWithByteBuffer2() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);

        long start = System.currentTimeMillis();
        for (int j = 0; j < 10; j++) {
            RandomAccessFile raf = new RandomAccessFile(fileName + j, "rw");
            FileChannel fileChannel = raf.getChannel();
            for (int i = 0; i < 1000; i++) {
                byteBuffer.put(bytes);
                byteBuffer.flip();
                fileChannel.write(byteBuffer);
                byteBuffer.clear();
            }
        }
        System.out.println(System.currentTimeMillis() - start);
    }

    //6628、6386
    @Test
    public void testWithDirectByteBuffer() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        long start = System.currentTimeMillis();
        for (int j = 0; j < 1; j++) {
            RandomAccessFile raf = new RandomAccessFile(fileName + j, "rw");
            FileChannel fileChannel = raf.getChannel();
            fileChannel.position(raf.length());

            for (int i = 0; i < count; i++) {
                if (byteBuffer.remaining() < bytes.length) {
                    byteBuffer.flip();
                    fileChannel.write(byteBuffer);
                    System.out.println(fileChannel.position());
                    System.out.println(raf.length());
                    if (fileChannel.position() != raf.length()) {
                        throw new RuntimeException("invalid position ");
                    }
                    System.out.println();
                    byteBuffer.clear();
                }
                byteBuffer.put(bytes);
            }
            if (byteBuffer.hasRemaining()) {
                byteBuffer.flip();
                fileChannel.write(byteBuffer);
                byteBuffer.clear();
            }
        }
        System.out.println(System.currentTimeMillis() - start);
    }

    @Test
    public void testWithMappedByteBuffer1() throws IOException {
        long start = System.currentTimeMillis();
        for (int j = 0; j < 1; j++) {
            RandomAccessFile raf = new RandomAccessFile(fileName + j, "rw");
            FileChannel fileChannel = raf.getChannel();
            MappedByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 1048576000L);
            for (int i = 0; i < 1000; i++) {
                byteBuffer.put(bytes);
            }
        }
        System.out.println(System.currentTimeMillis() - start);
    }

    @Test
    public void testWithMappedByteBuffer2() throws IOException {
        RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
        FileChannel fileChannel = raf.getChannel();
        MappedByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 1048576000L);
        long currentPosition = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            if (byteBuffer.remaining() < bytes.length) {
                byteBuffer.flip();
                byteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, currentPosition, 1048576000L);
            }
            byteBuffer.put(bytes);
            currentPosition += bytes.length;
        }
        byteBuffer.flip();
        byteBuffer.force();
        System.out.println(System.currentTimeMillis() - start);
    }

    @Test
    public void testWithMappedByteBuffer3() throws IOException {
        RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
        FileChannel fileChannel = raf.getChannel();
        MappedByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 1048576000L);
        long start = System.currentTimeMillis();
        byteBuffer.put(bytes);
        byteBuffer.flip();
        byteBuffer.force();
        System.out.println(System.currentTimeMillis() - start);
    }
}
