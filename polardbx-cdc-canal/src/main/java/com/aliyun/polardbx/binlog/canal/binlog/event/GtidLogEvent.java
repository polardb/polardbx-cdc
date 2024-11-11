/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * @author jianghang 2013-4-8 上午12:36:29
 * @version 1.0.3
 * @since mysql 5.6 / mariadb10
 */
public class GtidLogEvent extends LogEvent {

    // / Length of the commit_flag in event encoding
    public static final int ENCODED_FLAG_LENGTH = 1;
    // / Length of SID in event encoding
    public static final int ENCODED_SID_LENGTH = 16;

    private boolean commitFlag;
    private UUID sid;
    private long gno;

    public GtidLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        // final int postHeaderLen = descriptionEvent.postHeaderLen[header.type
        // - 1];

        buffer.position(commonHeaderLen);
        commitFlag = (buffer.getUint8() != 0); // ENCODED_FLAG_LENGTH

        byte[] bs = buffer.getData(ENCODED_SID_LENGTH);
        ByteBuffer bb = ByteBuffer.wrap(bs);
        long high = bb.getLong();
        long low = bb.getLong();
        sid = new UUID(high, low);

        gno = buffer.getLong64();

        // ignore gtid info read
        // sid.copy_from((uchar *)ptr_buffer);
        // ptr_buffer+= ENCODED_SID_LENGTH;
        //
        // // SIDNO is only generated if needed, in get_sidno().
        // spec.gtid.sidno= -1;
        //
        // spec.gtid.gno= uint8korr(ptr_buffer);
        // ptr_buffer+= ENCODED_GNO_LENGTH;
    }

    public boolean isCommitFlag() {
        return commitFlag;
    }

    public UUID getSid() {
        return sid;
    }

    public long getGno() {
        return gno;
    }

}
