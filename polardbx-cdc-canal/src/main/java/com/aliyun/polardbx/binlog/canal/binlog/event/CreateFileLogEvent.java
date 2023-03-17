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
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;

/**
 * Create_file_log_event.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class CreateFileLogEvent extends LoadLogEvent {

    /* CF = "Create File" */
    public static final int CF_FILE_ID_OFFSET = 0;
    public static final int CF_DATA_OFFSET = FormatDescriptionLogEvent.CREATE_FILE_HEADER_LEN;
    protected LogBuffer blockBuf;
    protected int blockLen;
    protected long fileId;
    protected boolean initedFromOld;

    public CreateFileLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);

        final int headerLen = descriptionEvent.commonHeaderLen;
        final int loadHeaderLen = descriptionEvent.postHeaderLen[LOAD_EVENT - 1];
        final int createFileHeaderLen = descriptionEvent.postHeaderLen[CREATE_FILE_EVENT - 1];

        copyLogEvent(buffer,
            ((header.type == LOAD_EVENT) ? (loadHeaderLen + headerLen) :
                (headerLen + loadHeaderLen + createFileHeaderLen)),
            descriptionEvent);

        if (descriptionEvent.binlogVersion != 1) {
            fileId = buffer.getUint32(headerLen + loadHeaderLen + CF_FILE_ID_OFFSET);
            /*
             * Note that it's ok to use get_data_size() below, because it is computed with values we have already read
             * from this event (because we called copy_log_event()); we are not using slave's format info to decode
             * master's format, we are really using master's format info. Anyway, both formats should be identical
             * (except the common_header_len) as these Load events are not changed between 4.0 and 5.0 (as logging of
             * LOAD DATA INFILE does not use Load_log_event in 5.0).
             */
            blockLen = buffer.limit() - buffer.position();
            blockBuf = buffer.duplicate(blockLen);
        } else {
            initedFromOld = true;
        }
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
