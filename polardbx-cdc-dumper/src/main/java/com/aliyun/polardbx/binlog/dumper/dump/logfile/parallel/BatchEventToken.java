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

    public boolean hasCapacity(SingleEventToken eventToken, int eventDataBufferSize, int eventDataMaxSize) {
        if (notDmlEventSize + eventToken.getLength() > eventDataBufferSize) {
            return false;
        }
        return dmlEventSize + notDmlEventSize + eventToken.getLength() <= eventDataMaxSize;
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
