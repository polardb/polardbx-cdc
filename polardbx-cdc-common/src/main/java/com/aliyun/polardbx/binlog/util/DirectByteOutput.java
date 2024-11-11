/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.protobuf.ByteOutput;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * created by ziyang.lb
 **/
public class DirectByteOutput extends ByteOutput {
    private byte[] bytes;

    public DirectByteOutput() {
        super();
    }

    public static byte[] unsafeFetch(ByteString byteString) {
        try {
            DirectByteOutput output = new DirectByteOutput();
            UnsafeByteOperations.unsafeWriteTo(byteString, output);
            return output.getBytes();
        } catch (IOException e) {
            throw new PolardbxException("fetch bytes from byte string error!!");
        }
    }

    private byte[] getBytes() {
        return bytes;
    }

    @Override
    public void write(byte value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeLazy(byte[] value, int offset, int length) throws IOException {
        this.bytes = value;
    }

    @Override
    public void write(ByteBuffer value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeLazy(ByteBuffer value) throws IOException {
        throw new UnsupportedOperationException();
    }
}
