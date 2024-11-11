/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;

import java.util.List;

/**
 * @author jiyue 2021/08/29
 */

public class DataImportTriggeredActions {

    public static class JustStartNextStateTasks extends FSMTriggeredAction {
        @Override
        public void execute(long FSMId) {
            FSMMetaManager.startServiceByState(FSMId);
        }
    }
}
