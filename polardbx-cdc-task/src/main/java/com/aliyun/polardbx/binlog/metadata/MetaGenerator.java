/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metadata;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class MetaGenerator {

    private CdcInitCommand initCommand;

    public MetaGenerator() {
        initCommand = new BinlogInitCommand(CommonUtils.buildStartCmd());
    }

    public boolean exists() {
        return initCommand.exists();
    }

    public void tryStart() {
        if (initCommand.isSuccess()) {
            return;
        }
        initCommand.tryStart();
        while (true) {
            if (initCommand.isSuccess()) {
                return;
            }
            log.info(initCommand + " is not finished, will try later.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

}
