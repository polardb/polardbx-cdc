/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode
public class ColumnarNodeInfo {
    public static final ColumnarNodeInfo EMPTY = new ColumnarNodeInfo(null, null, null);

    private final String ip;
    private final String port;
    private final String name;

    public static ColumnarNodeInfo build(String ipPort, String leader) {
        Preconditions.checkNotNull(ipPort);
        final String[] split = ipPort.split(":");
        return new ColumnarNodeInfo(split[0], split[1], leader);
    }
}
