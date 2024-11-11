/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

