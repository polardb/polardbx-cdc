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

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;
import lombok.Getter;

import java.util.Arrays;

public class ReplicaFSM extends AbstractFSM<ReplicaMeta> {

    @Getter
    private static ReplicaFSM instance = new ReplicaFSM();
    private static final MetaManagerTranProxy manager = SpringContextHolder.getObject(MetaManagerTranProxy.class);

    private ReplicaFSM() {
        super(Arrays.asList(
            new ReplicaTransitions.IncrementalModeInitTransition(),
            new ReplicaTransitions.ImageModeInitTransition(),
            new ReplicaTransitions.ReplicaFullFinishTransition(),
            new ReplicaTransitions.ReplicaIncCatchUpTransition(),
            new ReplicaTransitions.ReplicaFullValidStartTransition(),
            new ReplicaTransitions.ReplicaFullValidFinishedTransition()
        ), FSMState.REPLICA_INIT);
    }

    @Override
    public long create(ReplicaMeta meta) {
        try {
            RplStateMachine stateMachine = manager.initStateMachine(StateMachineType.REPLICA, meta, this);
            return stateMachine.getId();
        } catch (Throwable e) {
            return RplConstants.ERROR_FSMID;
        }
    }
}

