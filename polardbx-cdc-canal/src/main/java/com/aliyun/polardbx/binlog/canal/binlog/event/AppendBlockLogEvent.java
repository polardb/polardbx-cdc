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
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

/**
 * Append_block_log_event.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class AppendBlockLogEvent extends LogEvent {

    /* AB = "Append Block" */
    public static final int AB_FILE_ID_OFFSET = 0;
    private final LogBuffer blockBuf;
    private final int blockLen;
    private final long fileId;

    public AppendBlockLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        final int postHeaderLen = descriptionEvent.postHeaderLen[header.type - 1];
        final int totalHeaderLen = commonHeaderLen + postHeaderLen;

        buffer.position(commonHeaderLen + AB_FILE_ID_OFFSET);
        fileId = buffer.getUint32();

        buffer.position(postHeaderLen);
        blockLen = buffer.limit() - totalHeaderLen;
        blockBuf = buffer.duplicate(blockLen);
    }

    public final long getFileId() {
        return fileId;
    }

    public final LogBuffer getBuffer() {
        return blockBuf;
    }

    public final byte[] getData() {
        return blockBuf.getData();
    }
}
