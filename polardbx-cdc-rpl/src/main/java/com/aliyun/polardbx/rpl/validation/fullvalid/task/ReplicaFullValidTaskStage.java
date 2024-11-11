/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

/**
 * @author yudong
 * @since 2023/10/18 17:21
 **/
public enum ReplicaFullValidTaskStage {
    INIT("INIT"),
    CHECK("CHECK"),
    REPAIR("REPAIR"),
    SCHEMA_CHECK("SCHEMA_CHECK");

    private final String stageName;

    ReplicaFullValidTaskStage(String stageName) {
        this.stageName = stageName;
    }

    @Override
    public String toString() {
        return stageName;
    }

    public static ReplicaFullValidTaskStage nextStage(ReplicaFullValidTaskStage stage) {
        if (stage == INIT) {
            return CHECK;
        } else if (stage == CHECK) {
            // todo add repair
            return null;
        }
        return null;
    }
}
