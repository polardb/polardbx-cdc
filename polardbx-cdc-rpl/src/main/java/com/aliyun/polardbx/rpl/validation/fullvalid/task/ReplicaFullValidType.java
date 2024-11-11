/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

public enum ReplicaFullValidType {

    FULL_DATA,
    COUNT,
    SCHEMA;

    public static ReplicaFullValidTaskStage getFirstStage(ReplicaFullValidType type) {
        if (type == SCHEMA) {
            return ReplicaFullValidTaskStage.SCHEMA_CHECK;
        }
        return ReplicaFullValidTaskStage.INIT;
    }
}
