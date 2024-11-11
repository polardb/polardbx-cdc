/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

/**
 * @author yudong
 */
public class RecoveryTriggeredActions {

    public static class SearchFinishTriggerAction extends FSMTriggeredAction {
        @Override
        public void execute(long FSMId) {
        }

    }

    public static class CombineFinishTriggerAction extends FSMTriggeredAction {
        @Override
        public void execute(long FSMId) {
        }
    }
}
