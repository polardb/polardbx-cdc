/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.rpc.cdc.DumpStream;

import java.io.IOException;

public class StreamObserverFileLogFetcher extends StreamObserverLogFetcher {


    public StreamObserverFileLogFetcher() throws IOException {
        super();
    }

    @Override
    public void onNext(DumpStream dumpStream) {
        if (dumpStream.getIsHeartBeat()){
            // 防止下游超时异常
            pipe.updateReceiveTime();
            return;
        }
        super.onNext(dumpStream);
    }

    @Override
    public boolean fetch() throws IOException {
        if (limit == 0) {
            final int len = pipe.read(buffer, 0, buffer.length);
            if (len >= 0) {
                limit += len;
                position = 0;
                origin = 0;

                /* More binlog to fetch */
                return true;
            }
        } else if (origin == 0) {
            if (limit > buffer.length / 2) {
                ensureCapacity(buffer.length + limit);
            }
            final int len = pipe.read(buffer, limit, buffer.length - limit);
            if (len >= 0) {
                limit += len;

                /* More binlog to fetch */
                return true;
            }
        } else if (limit > 0) {
            if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
                int lenPosition = position + 4 + 1 + 4;
                long eventLen = ((long) (0xff & buffer[lenPosition++])) | ((long) (0xff & buffer[lenPosition++]) << 8)
                    | ((long) (0xff & buffer[lenPosition++]) << 16)
                    | ((long) (0xff & buffer[lenPosition++]) << 24);

                if (limit >= eventLen) {
                    return true;
                } else {
                    ensureCapacity((int) eventLen);
                }
            }

            System.arraycopy(buffer, origin, buffer, 0, limit);
            position -= origin;
            origin = 0;
            final int len = pipe.read(buffer, limit, buffer.length - limit);
            if (len >= 0) {
                limit += len;

                /* More binlog to fetch */
                return true;
            }
        } else {
            /* Should not happen. */
            throw new IllegalArgumentException("Unexcepted limit: " + limit);
        }

        /* Reach binlog file end */
        return false;
    }


}
