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

package com.aliyun.polardbx.binlog.dumper;

import java.util.concurrent.TimeUnit;

import com.aliyun.polardbx.binlog.dumper.dump.util.ByteArray;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @Author Timmy
 * @Description
 * @Date 2021/7/28
 */
@Slf4j
public class CommonTest {
    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .expireAfterAccess(120, TimeUnit.SECONDS)
        .build(new CacheLoader<String, String>() {
            @Override
            public String load(String s) {
                System.out.println("load " + s);
                return s.toUpperCase();
            }
        });

    @Test
    public void test1() {
        String s = "000000001b01000000240000009c000000000062696e6c6f672e303030303032f62e5aba";
        byte[] data = ByteBufUtil.decodeHexDump(s);
        ByteArray ba = new ByteArray(data);
        log.info("timestamp {}", ba.readLong(4));
        log.info("event type {}", ba.read());
        log.info("server-id {}", ba.readLong(4));
        log.info("event-size {}", ba.readLong(4));
        log.info("log pos {}", ba.readLong(4));
        log.info("flags {}", ba.readInteger(2));

    }

    @Test
    public void test2() {
        CACHE.invalidate("a");
        CACHE.invalidate("b");
    }
}
