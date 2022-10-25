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
package com.aliyun.polardbx.rpl.taskmeta;


import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.RecoveryFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author jiyue
 */

@Service
public class MetaManagerTranProxy {

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, DataImportMeta meta,
                                                   DataImportFSM fsm) throws Throwable {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, ReplicateMeta meta, ReplicaFSM fsm) {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, RecoveryMeta meta, RecoveryFSM fsm) {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

}
