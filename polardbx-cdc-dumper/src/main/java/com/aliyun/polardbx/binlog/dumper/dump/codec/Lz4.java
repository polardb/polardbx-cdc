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

import io.grpc.Codec;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * created by ziyang.lb
 **/
public class Lz4 implements Codec {

    @Override
    public String getMessageEncoding() {
        return "lz4";
    }

    @Override
    public OutputStream compress(OutputStream os) throws IOException {
        return new LZ4FrameOutputStream(os);
    }

    @Override
    public InputStream decompress(InputStream is) throws IOException {
        return new LZ4FrameInputStream(is);
    }
}
