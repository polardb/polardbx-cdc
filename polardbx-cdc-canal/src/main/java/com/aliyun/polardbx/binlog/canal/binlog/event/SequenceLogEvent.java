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

package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * sqeuence log event
 */
public class SequenceLogEvent extends LogEvent {

    static final int ENCODED_SEQUENCE_TYPE_OFFSET = 0;
    static final int ENCODED_SEQUENCE_TYPE_LEN = 1;
    // 8 bytes length.
    static final int ENCODED_SEQUENCE_NUMBER_OFFSET = 1;
    static final int ENCODED_SEQUENCE_NUMBER_LEN = 8;

    static final int POST_HEADER_LENGTH = ENCODED_SEQUENCE_TYPE_LEN + ENCODED_SEQUENCE_NUMBER_LEN;

    private int sequenceType;
    private long sequenceNum;

    public SequenceLogEvent(LogHeader header, LogBuffer buffer,
                            FormatDescriptionLogEvent descriptionEvent) throws IOException {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        final int postHeaderLen = POST_HEADER_LENGTH;
        /*
         * We test if the event's length is sensible, and if so we compute data_len. We
         * cannot rely on QUERY_HEADER_LEN here as it would not be format-tolerant. We
         * use QUERY_HEADER_MINIMAL_LEN which is the same for 3.23, 4.0 & 5.0.
         */
        if (buffer.limit() < (commonHeaderLen + postHeaderLen)) {
            throw new IOException("sequence event length is too short.");
        }
        int dataLen = buffer.limit() - (commonHeaderLen + postHeaderLen);
        buffer.position(commonHeaderLen);
        sequenceType = buffer.getInt8();
        sequenceNum = buffer.getLong64();

    }

    public int getSequenceType() {
        return sequenceType;
    }

    public long getSequenceNum() {
        return sequenceNum;
    }

    public boolean isCommitSequence() {
        return sequenceType == ENUM_SEQUENCE_TYPE.COMMIT_SEQUENCE.ordinal();
    }

    public boolean isSnapSequence() {
        return sequenceType == ENUM_SEQUENCE_TYPE.SNAPSHOT_SEQUENCE.ordinal();
    }

    public boolean isHeartbeat() {
        return sequenceType == ENUM_SEQUENCE_TYPE.HEART_BEAT.ordinal();
    }

    @Override
    public String info() {
        ENUM_SEQUENCE_TYPE type = ENUM_SEQUENCE_TYPE.valueOf(sequenceType);
        return MessageFormat.format("{0} ts: {1,number,#}", type.name(), sequenceNum);
    }

    private static enum ENUM_SEQUENCE_TYPE {
        INVALID_SEQUENCE, SNAPSHOT_SEQUENCE, COMMIT_SEQUENCE, HEART_BEAT;

        static ENUM_SEQUENCE_TYPE valueOf(int typeValue) {
            for (ENUM_SEQUENCE_TYPE type : values()) {
                if (type.ordinal() == typeValue) {
                    return type;
                }
            }
            return null;
        }
    }
}
