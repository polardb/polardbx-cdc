/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid;

import lombok.ToString;

/**
 * @author yudong
 * @since 2023/9/15 14:25
 **/
@ToString
public enum ReplicaFullValidSnapshotStrategy {
    TSO("TSO"),
    DIRECT("DIRECT");

    private final String label;

    ReplicaFullValidSnapshotStrategy(String label) {
        this.label = label;
    }

    public static ReplicaFullValidSnapshotStrategy from(String label) {
        for (ReplicaFullValidSnapshotStrategy i : ReplicaFullValidSnapshotStrategy.values()) {
            if (i.label.equalsIgnoreCase(label)) {
                return i;
            }
        }
        return DIRECT;
    }
}
