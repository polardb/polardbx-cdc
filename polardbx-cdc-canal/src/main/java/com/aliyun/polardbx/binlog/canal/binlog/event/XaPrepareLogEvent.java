/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

import java.text.MessageFormat;

/**
 * @author agapple 2018年5月7日 下午7:05:39
 * @version 1.0.26
 * @since mysql 5.7
 */
public class XaPrepareLogEvent extends LogEvent {

    private static String _dig_vec_lower = "0123456789abcdefghijklmnopqrstuvwxyz";
    private boolean onePhase;
    private int formatId;
    private int gtridLength;
    private int bqualLength;
    private byte[] data;

    public XaPrepareLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.getCommonHeaderLen();
        final int postHeaderLen = descriptionEvent.getPostHeaderLen()[header.getType() - 1];

        int offset = commonHeaderLen + postHeaderLen;
        buffer.position(offset);

        onePhase = (buffer.getInt8() == 0x00 ? false : true);

        formatId = buffer.getInt32();
        gtridLength = buffer.getInt32();
        bqualLength = buffer.getInt32();

        int MY_XIDDATASIZE = 128;
        if (MY_XIDDATASIZE >= gtridLength + bqualLength && gtridLength >= 0 && gtridLength <= 64 && bqualLength >= 0
            && bqualLength <= 64) {
            data = buffer.getData(gtridLength + bqualLength);
        } else {
            formatId = -1;
            gtridLength = 0;
            bqualLength = 0;
        }
    }

    public boolean isOnePhase() {
        return onePhase;
    }

    public int getFormatId() {
        return formatId;
    }

    public int getGtridLength() {
        return gtridLength;
    }

    public int getBqualLength() {
        return bqualLength;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("X'");
        int i = 0;
        for (; i < gtridLength; i++) {
            sb.append(_dig_vec_lower.charAt(data[i] >> 4));
            sb.append(_dig_vec_lower.charAt(data[i] & 0x0f));
        }

        sb.append("',X''");
        for (; i < gtridLength + bqualLength; i++) {
            sb.append(_dig_vec_lower.charAt(data[i] >> 4));
            sb.append(_dig_vec_lower.charAt(data[i] & 0x0f));
        }
        sb.append("'");

        String suffix = "";

        if (onePhase) {
            suffix += " | one phase";
        }

        return MessageFormat.format("XA PREPARE {0},{1}", sb.toString(), formatId) + suffix;
    }
}
