/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * Created by ziyang.lb
 * gcn log event for dn 8.0
 */
public class GcnLogEvent extends LogEvent {

    static final int ENCODED_FLAG_LEN = 1;
    static final int ENCODED_GCN_LEN = 8;
    static final int POST_HEADER_LENGTH = ENCODED_FLAG_LEN + ENCODED_GCN_LEN;

    private final long gcn;
    private final int flag;

    public GcnLogEvent(LogHeader header, LogBuffer buffer,
                       FormatDescriptionLogEvent descriptionEvent) throws IOException {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        /*
         * We test if the event's length is sensible, and if so we compute data_len. We
         * cannot rely on QUERY_HEADER_LEN here as it would not be format-tolerant. We
         * use QUERY_HEADER_MINIMAL_LEN which is the same for 3.23, 4.0 & 5.0.
         */
        if (buffer.limit() < (commonHeaderLen + POST_HEADER_LENGTH)) {
            throw new IOException("gcn event length is too short.");
        }
        buffer.position(commonHeaderLen);
        flag = buffer.getInt8();
        gcn = buffer.getLong64();
    }

    public int getFlag() {
        return flag;
    }

    public long getGcn() {
        return gcn;
    }

    @Override
    public String info() {
        return MessageFormat.format("gcn : {0,number,#}", gcn);
    }
}
