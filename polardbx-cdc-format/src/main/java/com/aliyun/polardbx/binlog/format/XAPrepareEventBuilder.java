/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

public class XAPrepareEventBuilder extends BinlogBuilder {

    private boolean onPhase;
    /**
     * -1 means that the XID is null
     */
    private int formatId;
    /**
     * value from 1 through 64
     */
    private int gtridLength;
    /**
     * value from 1 through 64
     */
    private int bqualLength;
    private byte[] data;

    public XAPrepareEventBuilder(int timestamp, int serverId, boolean onPhase, int formatId, int gtridLength,
                                 int bqualLength, byte[] data) {
        super(timestamp, BinlogEventType.XA_PREPARE_LOG_EVENT.getType(), serverId);
        this.onPhase = onPhase;
        this.formatId = formatId;
        this.gtridLength = gtridLength;
        this.bqualLength = bqualLength;
        this.data = data;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, onPhase ? 1 : 0, INT8);
        numberToBytes(outputData, formatId, INT32);
        numberToBytes(outputData, gtridLength, INT32);
        numberToBytes(outputData, bqualLength, INT32);
        writeBytes(outputData, data);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
