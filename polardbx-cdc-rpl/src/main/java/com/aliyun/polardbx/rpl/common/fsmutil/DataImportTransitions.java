/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            return FSMMetaManager.checkIncServiceCatchUp(FSMId);
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
            return FSMMetaManager.checkIncServiceCatchUp(FSMId);
        }
    }

    public static class CheckNotCatchUpTransition extends FSMTransition {
        public CheckNotCatchUpTransition() {
            super(FSMState.RECON_FINISHED_CATCH_UP, FSMState.RECON_FINISHED_WAIT_CATCH_UP,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return !FSMMetaManager.checkIncServiceCatchUp(FSMId);
        }
    }

}
