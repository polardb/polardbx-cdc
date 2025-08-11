/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

public class StreamObserverFileLogFetcherTest {

    @Test(timeout = 10000L)
    public void testFileLogFetcher() throws Throwable {
        try(StreamObserverFileLogFetcher fetcher = new StreamObserverFileLogFetcher()){
            LogDecoder decoder = new LogDecoder();
            decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
            LogContext lc = new LogContext();
            lc.setLogPosition(new LogPosition(""));
            lc.setServerCharactorSet(new ServerCharactorSet());
            long count = 0;
            AtomicReference<Throwable> ref = new AtomicReference<>();
            Thread t = getThread(fetcher, ref);
            t.start();
            while (fetcher.fetch()){
                if (ref.get() != null){
                    throw ref.get();
                }
                LogEvent event = decoder.decode(fetcher.buffer(), lc);
                if (event != null){
                    count++;
                    if (event.getLogPos() >= 15355){
                        break;
                    }
                }
            }
            Assert.assertEquals(199, count);
        }
    }

    private static Thread getThread(StreamObserverFileLogFetcher fetcher, AtomicReference<Throwable> ref) {
        Thread t = new Thread(() -> {
            try(InputStream is = StreamObserverFileLogFetcherTest.class.getResourceAsStream("/split_binlog.001")){
                byte []cache = new byte[1024];
                int len;
                int totalSize = 0;
                is.skip(4);
                while ((len = is.read(cache)) != -1){
                    DumpStream dumpStream = DumpStream.newBuilder().setPayload(ByteString.copyFrom(cache, 0, len)).build();
                    fetcher.onNext(dumpStream);
                    totalSize += len;
                }
                System.out.println(totalSize);
            }catch (Throwable e){
                ref.set(e);
                throw new PolardbxException(e);
            }
        });
        t.setDaemon(true);
        return t;
    }
}
