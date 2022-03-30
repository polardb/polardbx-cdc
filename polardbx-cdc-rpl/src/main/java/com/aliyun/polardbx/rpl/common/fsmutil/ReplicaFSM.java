/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ReplicateMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;

import java.util.ArrayList;

public class ReplicaFSM extends AbstractFSM<ReplicateMeta> {

    private static ReplicaFSM instance = new ReplicaFSM();

    public static ReplicaFSM getInstance() {
        return instance;
    }

    private ReplicaFSM() {
        super(new ArrayList<>(), FSMState.REPLICA);
    }

    @Override
    public long create(ReplicateMeta meta) {
        try {
            RplStateMachine stateMachine = FSMMetaManager.initStateMachine(StateMachineType.REPLICA, meta, this);
            return stateMachine.getId();
        } catch (Throwable e) {
            return RplConstants.ERROR_FSMID;
        }
    }
}

