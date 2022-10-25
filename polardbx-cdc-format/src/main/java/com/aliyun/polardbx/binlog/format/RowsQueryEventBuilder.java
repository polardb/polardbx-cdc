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
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

public class RowsQueryEventBuilder extends BinlogBuilder {

    private String text;

    public RowsQueryEventBuilder(int timestamp, int serverId, String text) {
        super(timestamp, BinlogEventType.ROWS_QUERY_LOG_EVENT.getType(), serverId);
        this.text = text;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        byte[] data = text.getBytes(ISO_8859_1);
        numberToBytes(outputData, data.length, INT8);
        writeBytes(outputData, data);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
