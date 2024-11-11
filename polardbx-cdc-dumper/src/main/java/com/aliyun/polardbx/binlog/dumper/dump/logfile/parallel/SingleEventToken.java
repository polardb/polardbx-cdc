/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * created by ziyang.lb
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SingleEventToken extends EventToken {
    private String tso;
    private long tsoTimeSecond;
    private long nextPosition;
    private Type type;
    private long serverId;
    private long xid;
    private byte[] data;
    private String rowsQuery;
    private String cts;
    private boolean forceFlush;
    private int length;
    private boolean useTokenData;
    private int offset;
    private Boolean checkServerId;

    public enum Type {
        /**
         *
         */
        BEGIN,
        /**
         *
         */
        COMMIT,
        /**
         *
         */
        ROWSQUERY,
        /**
         *
         */
        DML,
        /**
         *
         */
        TSO,
        /**
         *
         */
        HEARTBEAT
    }

    public void checkLength(int length) {
        if (this.length != length) {
            throw new PolardbxException(
                "invalide length value ,this value is " + this.length + " , that value is " + length);
        }
    }

    @Override
    public String toString() {
        return "SingleEventToken{" +
            "tso='" + tso + '\'' +
            ", tsoTimeSecond=" + tsoTimeSecond +
            ", nextPosition=" + nextPosition +
            ", type=" + type +
            ", serverId=" + serverId +
            ", xid=" + xid +
            ", rowsQuery='" + rowsQuery + '\'' +
            ", cts='" + cts + '\'' +
            ", forceFlush=" + forceFlush +
            ", length=" + length +
            ", useTokenData=" + useTokenData +
            ", offset=" + offset +
            ", checkServerId=" + checkServerId +
            '}';
    }
}
