/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.task.ISubVersionChangeCallback;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumperSubVersionChangeCallback implements ISubVersionChangeCallback {

    private final DumperController dumperController;

    public DumperSubVersionChangeCallback(DumperController dumperController) {
        this.dumperController = dumperController;
    }

    @SneakyThrows
    @Override
    public void onSubVersionChange(long oldSubVersion, long newSubVersion, TaskRuntimeConfig taskRuntimeConfig) {

        synchronized (DumperSubVersionChangeCallback.class) {
            // wait for dumper controller running
            while (!dumperController.isRunning()) {
                log.warn("receive sub version change event, but dumper controller is not running, "
                    + "ignore this change, from {} to {}.", oldSubVersion, newSubVersion);
                CommonUtils.sleep(10);
            }

            log.info("receive sub version change event, from {} to {}, prepare to reload dumper controller.",
                oldSubVersion, newSubVersion);
            if (dumperController.getTaskRuntimeConfig().getType() == TaskType.DumperX) {
                dumperController.reloadForMultiStream(taskRuntimeConfig);
            } else {
                dumperController.reloadForSingleStream(taskRuntimeConfig);
            }
        }
    }

}
