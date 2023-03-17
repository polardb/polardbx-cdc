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
package com.aliyun.polardbx.rpl.extractor.cdc.buffer;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamPipe {

    PipedInputStream reader;
    PipedOutputStream writer;
    private AtomicBoolean first = new AtomicBoolean(true);

    public StreamPipe() throws IOException {
        reader = new PipedInputStream(1024 * 8);
        writer = new PipedOutputStream();
        reader.connect(writer);
    }

    public int read(byte[] buffer, int offset, int limit) throws IOException {
        return reader.read(buffer, offset, limit);
    }

    public void write(byte[] data) throws IOException {
        writer.write(data);
    }

    public void close() throws IOException {
        writer.close();
    }
}
