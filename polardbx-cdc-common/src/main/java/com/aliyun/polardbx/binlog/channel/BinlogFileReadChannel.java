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
package com.aliyun.polardbx.binlog.channel;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;

/**
 * @author chengjin
 */
public class BinlogFileReadChannel {
    private final Channel channel;
    private final Closeable closeable;

    private Method read;
    private Method readWithPos;
    private Method getPos;
    private Method setPos;
    private Method size;

    public BinlogFileReadChannel(Channel channel, Closeable closeable) {
        this.channel = channel;
        this.closeable = closeable;
        java.lang.reflect.Method[] methods;
        if (this.channel instanceof FileChannel) {
            methods = this.channel.getClass().getDeclaredMethods();
        } else {
            methods = this.channel.getClass().getSuperclass().getDeclaredMethods();
        }

        for (java.lang.reflect.Method m : methods) {
            if ("position".equalsIgnoreCase(m.getName())) {
                if (m.getParameterCount() > 0) {
                    setPos = m;
                } else {
                    getPos = m;
                }
            }
            if ("read".equalsIgnoreCase(m.getName())) {
                if (m.getParameterCount() == 2) {
                    readWithPos = m;
                } else if (m.getParameterCount() == 1) {
                    read = m;
                }
            }
            if ("size".equalsIgnoreCase(m.getName())) {
                size = m;
            }
        }
    }

    public int read(ByteBuffer dst, long position) throws IOException {
        try {
            return (int) parse(readWithPos.invoke(channel, dst, position));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public int read(ByteBuffer dst) throws IOException {
        try {
            return (int) parse(read.invoke(channel, dst));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public long position() {
        try {
            return parse(getPos.invoke(channel));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public long size() {
        try {
            return parse(size.invoke(channel));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private long parse(Object o) {
        return NumberUtils.createLong(String.valueOf(o));
    }

    public void position(long newPos) {
        try {
            setPos.invoke(channel, newPos);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public void close() throws IOException {
        try {
            if (channel != null) {
                channel.close();
            }
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
