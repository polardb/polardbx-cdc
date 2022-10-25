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
package com.aliyun.polardbx.binlog.canal.binlog;

import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [a,a,a,a,a] -> move step
 * p  e    b
 * <p>
 * <p>
 * boffset = p%count;
 * newb = Math.min(b + step, p);
 * newe = Math.min(e + step, newb+count);
 */
@Slf4j
public class AsyncInputStream extends FilterInputStream implements Runnable {

    private static final int DEFAULT_BUFFER_SIZE = 5 * 1024 * 1024;

    private static int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    protected volatile byte buf[];

    protected AtomicLong end = new AtomicLong(0);

    protected AtomicLong pos = new AtomicLong(0);

    protected volatile boolean exhaust = false;

    private Thread t;

    private volatile boolean close = false;

    public AsyncInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    public AsyncInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
        t = new Thread(this, "async-read-thread");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try {
            while (!close && !exhaust) {
                final long p = pos.get();
                final long e = end.get();
                final int len = buf.length;
                boolean exhaust = this.exhaust;
                // 计算出可用空间 真实长度-没有消费的数据
                final int avaliableSpace = (int) (p - e + len);
                if (avaliableSpace > 0) {
                    // 理论上新的end可以设置的最大值
                    final long newEnd = e + avaliableSpace;
                    final int oe = (int) (e % len);
                    final int one = (int) (newEnd % len);
                    int readLen = 0;
                    // b   p oe  one
                    if (one > oe) {
                        readLen = in.read(buf, oe, one - oe);
                    } else {
                        readLen = in.read(buf, oe, len - oe);
                        if (readLen == len - oe) {
                            // 读满了，继续读取
                            int newReadLen = in.read(buf, 0, one);
                            if (newReadLen != -1) {
                                readLen += newReadLen;
                            } else {
                                // 没数据了
                                exhaust = true;
                            }
                        }

                    }
                    if (readLen == -1) {
                        this.exhaust = true;
                        return;
                    }
                    end.set(e + readLen);
                    this.exhaust = exhaust;
                }
            }

        } catch (Exception ex) {
            log.error("read data error", ex);
            try {
                this.close();
            } catch (IOException e) {
            }
        }

    }

    /**
     * Check to make sure that underlying input stream has not been
     * nulled out due to close; if not return it;
     */
    private InputStream getInIfOpen() throws IOException {
        InputStream input = in;
        if (input == null) {
            throw new IOException("Stream closed");
        }
        return input;
    }

    /**
     * Check to make sure that buffer has not been nulled out due to
     * close; if not return it;
     */
    private byte[] getBufIfOpen() throws IOException {
        byte[] buffer = buf;
        if (buffer == null) {
            throw new IOException("Stream closed");
        }
        return buffer;
    }

    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a synchronized method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */

    private int next() {
        return (int) pos.getAndAdd(1);
    }

    private boolean isExhaust() {
        return exhaust && available() <= 0;
    }

    @Override
    public synchronized int read() throws IOException {
        if (isExhaust()) {
            return -1;
        }
        return getBufIfOpen()[next()] & 0xff;
    }

    private int read1(byte[] b, int off, int len) throws IOException {
        if (isExhaust()) {
            return -1;
        }
        getBufIfOpen();
        final int limit = this.buf.length;
        int totalRead = 0;
        while (len > 0 && !isExhaust()) {
            while (available() <= 0) {
                continue;
            }
            final int readLen = Math.min(len, available());
            final long _pos = this.pos.get();
            final int pos = (int) (_pos % buf.length);
            final int end = (int) (this.end.get() % buf.length);

            if (pos >= end) {
                int readLen1 = limit - pos;
                if (readLen1 < readLen) {
                    final int dstPos = off + totalRead;
                    System.arraycopy(this.buf, pos, b, dstPos, readLen1);
                    System.arraycopy(this.buf, 0, b, dstPos + readLen1, readLen - readLen1);
                } else {
                    System.arraycopy(this.buf, pos, b, off + totalRead, readLen);
                }
            } else {
                System.arraycopy(this.buf, pos, b, off + totalRead, readLen);
            }
            if (!this.pos.compareAndSet(_pos, _pos + readLen)) {
                throw new IOException("not support multi reader");
            }
            totalRead += readLen;
            len -= readLen;
        }

        return totalRead;
    }

    @Override
    public synchronized int read(byte b[], int off, int len)
        throws IOException {
        getBufIfOpen(); // Check for closed stream
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        return read1(b, off, len);
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        getBufIfOpen(); // Check for closed stream
        if (n <= 0) {
            return 0;
        }
        long totalSkip = 0;
        while (n > 0 && !isExhaust()) {
            long pos = this.pos.get();
            long end = this.end.get();
            long aval = end - pos;
            long rskip = Math.min(n, aval);
            if (!this.pos.compareAndSet(pos, pos + rskip)) {
                throw new IOException("not support multi read");
            }
            totalSkip += rskip;
            n -= rskip;
        }
        return totalSkip;
    }

    @Override
    public synchronized int available() {
        return (int) (end.get() - pos.get());
    }

    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void close() throws IOException {
        buf = null;
        this.in.close();
        this.close = true;
    }

}
