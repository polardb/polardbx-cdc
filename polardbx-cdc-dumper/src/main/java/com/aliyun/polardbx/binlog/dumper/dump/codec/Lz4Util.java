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
package com.aliyun.polardbx.binlog.dumper.dump.codec;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.apache.commons.lang.RandomStringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * created by ziyang.lb
 **/
public class Lz4Util {

    private static final LZ4Factory FACTORY;

    static {
        FACTORY = LZ4Factory.fastestInstance();
    }

    public static byte[] compress(byte[] data) {
        final int decompressedLength = data.length;
        LZ4Compressor compressor = FACTORY.fastCompressor();
        int maxCompressedLength = compressor.maxCompressedLength(decompressedLength);
        byte[] compressed = new byte[maxCompressedLength];
        int compressedLength = compressor.compress(data, 0, decompressedLength, compressed, 0, maxCompressedLength);
        return Arrays.copyOf(compressed, compressedLength);
    }

    public static byte[] decompress(byte[] data, int decompressedLength) {
        LZ4FastDecompressor decompressor = FACTORY.fastDecompressor();
        byte[] restored = new byte[decompressedLength];
        decompressor.decompress(data, 0, restored, 0, decompressedLength);
        return restored;
    }

    public static void main(String args[]) throws IOException {
        int count = 1024 * 1024 * 100;
        String str = RandomStringUtils.randomAlphabetic(count);
        byte[] input = str.getBytes();

        System.out.println("input size " + input.length);
        byte[] compressed = compress(input);
        System.out.println("decompress size " + count);
        System.out.println("compress size " + compressed.length);

        byte[] decompressed = decompress(compressed, count);
        System.out.println(Arrays.equals(input, decompressed));

        File file = new File("/Users/lubiao/Downloads/binlog.000001");
        FileInputStream fis = new FileInputStream(file);
        long size = fis.getChannel().size();
        ByteBuffer bb = ByteBuffer.allocate((int) size);
        fis.getChannel().read(bb);

        long start = System.currentTimeMillis();
        System.out.println(bb.array().length);
        byte[] bytes = compress(bb.array());
        System.out.println(bytes.length);
        System.out.println(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        decompress(bytes, bb.array().length);
        System.out.println(System.currentTimeMillis() - start);
    }
}
