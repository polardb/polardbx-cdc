/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;

/**
 * @author yudong
 */
public class RecoveryTransitions {

    public static class SearchFinishTransition extends FSMTransition {
        public SearchFinishTransition() {
            super(FSMState.REC_SEARCH, FSMState.REC_COMBINE,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.REC_SEARCH);
        }
    }

    public static class ResultCombineFinishTransition extends FSMTransition {
        public ResultCombineFinishTransition() {
            super(FSMState.REC_COMBINE, FSMState.FINISHED,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.REC_COMBINE);
        }
    }
}
