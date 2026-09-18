/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.task.ISubVersionChangeCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class TaskSubVersionChangeCallback implements ISubVersionChangeCallback {
    final TaskController taskController;
    final Consumer<Void> exitCallback;

    public TaskSubVersionChangeCallback(TaskController taskController, Consumer<Void> exitCallback) {
        this.taskController = taskController;
        this.exitCallback = exitCallback;
    }

    @Override
    public void onSubVersionChange(long oldSubVersion, long newSubVersion, TaskRuntimeConfig taskRuntimeConfig) {
        if (taskRuntimeConfig.getType() == TaskType.Dispatcher
            && HashLevel.getCurrentHashLevel() != HashLevel.DATANODE) {
            log.warn("receive sub version change event, and hash level is not DATANODE,"
                + "ignore this change, from {} to {}, process is going down.", oldSubVersion, newSubVersion);
            exitCallback.accept(null);
        } else {
            taskController.reload(taskRuntimeConfig);
        }
    }
}
