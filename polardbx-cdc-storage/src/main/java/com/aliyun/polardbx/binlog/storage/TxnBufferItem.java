/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
@Data
@Builder
public class TxnBufferItem {
    private String traceId;
    private String rowsQuery;
    private int eventType;
    private byte[] payload;
    private String schema;
    private String table;
    private int hashKey;
    private List<byte[]> primaryKey;

    //可选项
    private String binlogFile;
    private long binlogPosition;
    private String originTraceId;

    public int size() {
        if (payload != null) {
            return payload.length;
        }

        throw new IllegalStateException("payload is null");
    }
}
