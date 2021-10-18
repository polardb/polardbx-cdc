/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.canal.binlog;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

/**
 * Declaration a binary-log fetcher. It extends from <code>LogBuffer</code>.
 *
 * <pre>
 * LogFetcher fetcher = new SomeLogFetcher();
 * ...
 *
 * while (fetcher.fetch())
 * {
 *     LogEvent event;
 *     do
 *     {
 *         event = decoder.decode(fetcher, context);
 *
 *         // process log event.
 *     }
 *     while (event != null);
 * }
 * // no more binlog.
 * fetcher.close();
 * </pre>
 *
 * @author <a>Changyuan.lh</a>
 * @version 1.0
 */
public abstract class LogFetcher extends LogBuffer implements Closeable {

    /**
     * Default initial capacity.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 8192;

    /**
     * Default growth factor.
     */
    public static final float DEFAULT_GROWTH_FACTOR = 2.0f;

    /**
     * Binlog file header size
     */
    public static final int BIN_LOG_HEADER_SIZE = 4;

    protected final float factor;

    public LogFetcher() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_GROWTH_FACTOR);
    }

    public LogFetcher(final int initialCapacity) {
        this(initialCapacity, DEFAULT_GROWTH_FACTOR);
    }

    public LogFetcher(final int initialCapacity, final float growthFactor) {
        this.buffer = new byte[initialCapacity];
        this.factor = growthFactor;
    }

    /**
     * Increases the capacity of this <tt>LogFetcher</tt> instance, if necessary, to ensure that it can hold at least
     * the number of elements specified by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     */
    protected final void ensureCapacity(final int minCapacity) {
        final int oldCapacity = buffer.length;

        if (minCapacity > oldCapacity) {
            int newCapacity = (int) (oldCapacity * factor);
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }

            buffer = Arrays.copyOf(buffer, newCapacity);
        }
    }

    /**
     * Fetches the next frame of binary-log, and fill it in buffer.
     */
    public abstract boolean fetch() throws IOException;

    /**
     * {@inheritDoc}
     *
     * @see Closeable#close()
     */
    public abstract void close() throws IOException;
}
