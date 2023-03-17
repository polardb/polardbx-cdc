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
