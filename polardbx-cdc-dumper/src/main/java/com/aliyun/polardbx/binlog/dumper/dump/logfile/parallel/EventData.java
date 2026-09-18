/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandByteArray;
import lombok.Data;

/**
 * created by ziyang.lb
 **/
@Data
public class EventData {
    private EventToken eventToken;
    /**
     * 写入byte[]数组时.每次都要通过autoExpandByteArray写入
     */
    private AutoExpandByteArray autoExpandByteArray;

    public EventData(int eventDataBufferSize) {
        // 会被反复使用
        byte[] data = new byte[eventDataBufferSize];
        autoExpandByteArray = new AutoExpandByteArray(data);
    }

    public byte[] getData() {
        return autoExpandByteArray.getData();
    }

    public void clear() {
        eventToken = null;
    }
}
