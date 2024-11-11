/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.MarkCommandEnum;
import com.aliyun.polardbx.binlog.MarkType;
import lombok.Data;

@Data
public class MarkInfo {
    private static final String SEP = "::";
    private MarkType markType;
    private String tso;
    private MarkCommandEnum command;
    private boolean valid = false;

    public MarkInfo(String queryLogInfo) {
        if (!queryLogInfo.startsWith(MarkType.CTS.name())) {
            return;
        }
        int f = queryLogInfo.indexOf(SEP);
        if (f < 0) {
            return;
        }
        markType = MarkType.valueOf(queryLogInfo.substring(0, f));
        int s = queryLogInfo.indexOf(SEP, f + 2);
        if (s > 0) {
            tso = queryLogInfo.substring(f + 2, s);
            command = MarkCommandEnum.valueOf(queryLogInfo.substring(s + 2));
        } else {
            tso = queryLogInfo.substring(f + 2);
        }
        valid = true;
    }

    public MarkInfo(MarkType markType, String tso) {
        this.markType = markType;
        this.tso = tso;
    }

    public MarkInfo(MarkType markType, String tso, MarkCommandEnum command) {
        this.markType = markType;
        this.tso = tso;
        this.command = command;
    }

    public String buildQueryLog() {
        String tso = markType + SEP + this.tso + SEP + command;
        return tso;
    }
}
