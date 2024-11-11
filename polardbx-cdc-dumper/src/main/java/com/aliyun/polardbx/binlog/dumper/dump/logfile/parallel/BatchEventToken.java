/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.util.LinkedList;
import java.util.List;

/**
 * created by ziyang.lb
 **/
public class BatchEventToken extends EventToken {
    private long dmlEventSize;
    private long notDmlEventSize;
    private final List<SingleEventToken> tokens = new LinkedList<>();

    public boolean hasCapacity(SingleEventToken eventToken, int maxSlotSize, int maxSlotPayloadSize) {
        if (notDmlEventSize + eventToken.getLength() > maxSlotSize) {
            return false;
        }
        return dmlEventSize + notDmlEventSize + eventToken.getLength() <= maxSlotPayloadSize;
    }

    public void addToken(SingleEventToken eventToken) {
        switch (eventToken.getType()) {
        case BEGIN:
        case TSO:
        case COMMIT:
        case ROWSQUERY:
            notDmlEventSize += eventToken.getLength();
            break;
        case DML:
            dmlEventSize += eventToken.getLength();
            break;
        case HEARTBEAT:
            break;
        default:
            throw new PolardbxException("unsupported event token type " + eventToken.getType());
        }
        tokens.add(eventToken);
    }

    public List<SingleEventToken> getTokens() {
        return tokens;
    }
}
