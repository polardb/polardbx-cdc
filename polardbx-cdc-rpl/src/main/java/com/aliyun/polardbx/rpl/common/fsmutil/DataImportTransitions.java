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
 * @author jiyue 2021/08/29
 */

// 如果需要transition和其他成员解耦，可以去掉super构造函数，采取builder的形式来进行初始化，提供更高的可定制性
//            DataImportTransitions.IncCopyCatchUpTransition.builder()
//                .currentState(FSMState.INC_COPY_CHECK)
//                .nextState(FSMState.INC_COPY_CHECK)
//                .triggeredAction(new DataImportTriggeredActions.IncCatchUpTriggerAction())
//                .build()

public class DataImportTransitions {
    public static class FullCopyFinishTransition extends FSMTransition {
        public FullCopyFinishTransition() {
            super(FSMState.FULL_COPY, FSMState.INC_COPY,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.FULL_COPY);
        }
    }

    public static class IncCopyCatchUpTransition extends FSMTransition {
        public IncCopyCatchUpTransition() {
            super(FSMState.INC_COPY, FSMState.CATCH_UP_VALIDATION,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.INC_COPY);
        }
    }

    public static class FullCheckFinishTransition extends FSMTransition {
        public FullCheckFinishTransition() {
            super(FSMState.CATCH_UP_VALIDATION, FSMState.RECONCILIATION,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.FULL_VALIDATION);
        }
    }

    public static class FullReconFinishTransition extends FSMTransition {
        public FullReconFinishTransition() {
            super(FSMState.RECONCILIATION, FSMState.RECON_FINISHED_WAIT_CATCH_UP,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.RECONCILIATION);
        }
    }

    public static class CheckCatchUpTransition extends FSMTransition {
        public CheckCatchUpTransition() {
            super(FSMState.RECON_FINISHED_WAIT_CATCH_UP, FSMState.RECON_FINISHED_CATCH_UP,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.INC_COPY);
        }
    }

    public static class CheckNotCatchUpTransition extends FSMTransition {
        public CheckNotCatchUpTransition() {
            super(FSMState.RECON_FINISHED_CATCH_UP, FSMState.RECON_FINISHED_WAIT_CATCH_UP,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return !FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.INC_COPY);
        }
    }

    public static class CheckBackFlowCatchUpTransition extends FSMTransition {
        public CheckBackFlowCatchUpTransition() {
            super(FSMState.BACK_FLOW, FSMState.BACK_FLOW_CATCH_UP,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.CDC_INC);
        }
    }

    public static class CheckBackFlowNotCatchUpTransition extends FSMTransition {
        public CheckBackFlowNotCatchUpTransition() {
            super(FSMState.BACK_FLOW_CATCH_UP, FSMState.BACK_FLOW,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return !FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.CDC_INC);
        }
    }

}
