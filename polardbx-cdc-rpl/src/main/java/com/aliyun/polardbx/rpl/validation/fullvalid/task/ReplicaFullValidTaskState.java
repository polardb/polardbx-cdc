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
public enum ReplicaFullValidTaskState {
    READY("READY"),
    RUNNING("RUNNING"),
    PAUSED("PAUSED"),
    ERROR("ERROR"),
    FINISHED("FINISHED");

    private final String stateName;

    ReplicaFullValidTaskState(String stateName) {
        this.stateName = stateName;
    }

    @Override
    public String toString() {
        return stateName;
    }
}
