/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import lombok.Data;

/**
 * created by ziyang.lb
 **/
@Data
public class EventData {
    private EventToken eventToken;
    private byte[] data;

    public EventData(int eventDataBufferSize) {
        //会被反复使用
        data = new byte[eventDataBufferSize];
    }

    public void clear() {
        eventToken = null;
    }
}
