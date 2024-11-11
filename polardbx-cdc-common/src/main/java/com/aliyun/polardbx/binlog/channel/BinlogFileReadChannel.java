/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.channel;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            log.error("read from binlog file error", e);
            throw new IOException("Read from file channel error!");
        }
    }

    public int read(ByteBuffer dst) throws IOException {
        try {
            return (int) parse(read.invoke(channel, dst));
        } catch (Exception e) {
            log.error("read from binlog file error", e);
            throw new IOException("Read from file channel error!");
        }
    }

    public long position() throws IOException {
        try {
            return parse(getPos.invoke(channel));
        } catch (Exception e) {
            log.error("invoke position method error", e);
            throw new IOException("Get position of file channel error!");
        }
    }

    public void position(long newPos) throws IOException {
        try {
            setPos.invoke(channel, newPos);
        } catch (Exception e) {
            log.error("invoke position method error", e);
            throw new IOException("Set position of file channel error!");
        }
    }

    public long size() throws IOException {
        try {
            return parse(size.invoke(channel));
        } catch (Exception e) {
            log.error("invoke size method error", e);
            throw new IOException("Get size of file channel error!");
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
            log.error("invoke close method error", e);
            throw new IOException("Close file channel error!");
        }
    }

    private long parse(Object o) {
        return NumberUtils.createLong(String.valueOf(o));
    }
}
