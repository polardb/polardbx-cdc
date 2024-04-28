/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskManager;

/**
 * @author jiyue 2021/08/29
 */

public class ReplicaTransitions {
    public static class IncrementalModeInitTransition extends FSMTransition {
        public IncrementalModeInitTransition() {
            super(FSMState.REPLICA_INIT, FSMState.REPLICA_INC,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return !FSMMetaManager.isReplicaImageMode(FSMId);
        }
    }

    public static class ImageModeInitTransition extends FSMTransition {
        public ImageModeInitTransition() {
            super(FSMState.REPLICA_INIT, FSMState.REPLICA_FULL,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isReplicaImageMode(FSMId);
        }
    }

    public static class ReplicaFullFinishTransition extends FSMTransition {
        public ReplicaFullFinishTransition() {
            super(FSMState.REPLICA_FULL, FSMState.REPLICA_INC,
                null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.isServiceAndTaskFinish(FSMId, ServiceType.REPLICA_FULL);
        }
    }

    /**
     * 位点追平
     */
    public static class ReplicaIncCatchUpTransition extends FSMTransition {
        public ReplicaIncCatchUpTransition() {
            super(FSMState.REPLICA_INC, FSMState.REPLICA_INC_CATCH_UP, null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return FSMMetaManager.checkServiceCatchUp(FSMId, ServiceType.REPLICA_INC);
        }
    }

    /**
     * 进行全量校验
     */
    public static class ReplicaFullValidStartTransition extends FSMTransition {
        public ReplicaFullValidStartTransition() {
            super(FSMState.REPLICA_INC_CATCH_UP, FSMState.REPLICA_FULL_VALID, null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            return !ReplicaFullValidTaskManager.isAllTaskFinished(FSMId);
        }
    }

    /**
     * 全量校验完成
     */
    public static class ReplicaFullValidFinishedTransition extends FSMTransition {
        private long lastFinishedTime = -1;

        public ReplicaFullValidFinishedTransition() {
            super(FSMState.REPLICA_FULL_VALID, FSMState.REPLICA_INC, null, null);
        }

        @Override
        public boolean isMatch(long FSMId) {
            boolean allTaskFinished = ReplicaFullValidTaskManager.isAllTaskFinished(FSMId);
            if (!allTaskFinished) {
                lastFinishedTime = -1;
                return false;
            }
            if (lastFinishedTime == -1) {
                lastFinishedTime = System.currentTimeMillis();
                return false;
            }
            // 所有task结束之后依然等待600秒，等待可能的task提交，防止校验进程被干掉
            return System.currentTimeMillis() - lastFinishedTime > 600 * 1000;
        }
    }
}
